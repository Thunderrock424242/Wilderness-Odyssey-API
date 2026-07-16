package com.thunder.wildernessodysseyapi.weather.client.cloud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thunder.wildernessodysseyapi.weather.client.ClientWeatherCoordinator;
import com.thunder.wildernessodysseyapi.weather.config.WeatherRenderingConfig;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Renders cached blocky cloud geometry from synchronized atmospheric cells.
 *
 * <p>The server-authored cloud and precipitation fields decide where geometry
 * may exist. A deterministic detail layer changes the outline over time, but
 * only that detail is displaced by wind; the broad cloud envelope stays over
 * the same world-space rainy area. All GPU resources are owned and replaced on
 * the client render thread.</p>
 */
public final class LocalizedCloudRenderer {

    private static final double CLOUD_BASE_OFFSET = 0.33;
    private static final float CLOUD_U = 17.5F / 256.0F;
    private static final float CLOUD_V = 0.5F / 256.0F;
    private static final double MOTION_WRAP_BLOCKS = CloudCoverageModel.CLOUD_TILE_SIZE * 1024.0;

    private static VertexBuffer cloudBuffer;
    private static boolean meshCacheValid;
    private static ClientLevel renderedLevel;
    private static CloudStatus builtCloudStatus;
    private static WeatherRenderingConfig.Settings builtSettings;
    private static int builtOriginTileX = Integer.MIN_VALUE;
    private static int builtOriginTileZ = Integer.MIN_VALUE;
    private static long builtSequence = Long.MIN_VALUE;
    private static Vec3 builtBaseColor = Vec3.ZERO;
    private static long lastBuildTick = Long.MIN_VALUE;
    private static double lastMotionTicks = Double.NaN;
    private static double windDetailOffsetX;
    private static double windDetailOffsetZ;
    private static double builtWindDetailOffsetX;
    private static double builtWindDetailOffsetZ;
    private static boolean builtMorphologyPotential;
    private static boolean builtTransitionComplete;
    private static boolean samplingBaseCloudColor;
    private static Diagnostics diagnostics = Diagnostics.INACTIVE;

    private LocalizedCloudRenderer() {
    }

    /**
     * Draws the localized cloud field and suppresses vanilla's global fallback.
     *
     * @param level active client level
     * @param ticks level-renderer tick counter
     * @param partialTick partial render tick
     * @param poseStack active cloud pose stack
     * @param camX camera world X
     * @param camY camera world Y
     * @param camZ camera world Z
     * @param frustumMatrix vanilla cloud frustum/model-view transform
     * @param projectionMatrix active projection matrix
     * @param cloudHeight dimension cloud base height
     */
    public static void render(
            ClientLevel level,
            int ticks,
            float partialTick,
            PoseStack poseStack,
            double camX,
            double camY,
            double camZ,
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            float cloudHeight
    ) {
        WeatherRenderingConfig.Settings settings = WeatherRenderingConfig.settings();
        ClientWeatherCoordinator.ClientStateView state = ClientWeatherCoordinator.stateView(level);
        if (state == null || !settings.enabled()) {
            clear();
            return;
        }

        double renderTicks = ticks + partialTick;
        prepareLevel(level, renderTicks);
        advanceWindDetail(level, camX, camZ, renderTicks, settings.windDetailSpeedBlocksPerSecond());

        CloudStatus cloudStatus = Minecraft.getInstance().options.getCloudsType();
        int originTileX = floorToInt(camX / CloudCoverageModel.CLOUD_TILE_SIZE);
        int originTileZ = floorToInt(camZ / CloudCoverageModel.CLOUD_TILE_SIZE);
        Vec3 baseColor = baseCloudColor(level, partialTick);
        if (needsRebuild(state, settings, cloudStatus, originTileX, originTileZ, ticks, baseColor)) {
            rebuild(
                    level,
                    state,
                    settings,
                    cloudStatus,
                    originTileX,
                    originTileZ,
                    ticks,
                    baseColor
            );
        }

        diagnostics = new Diagnostics(
                true,
                diagnostics.visibleTiles(),
                diagnostics.vertices(),
                diagnostics.averageCoverage(),
                windDetailOffsetX,
                windDetailOffsetZ
        );
        if (cloudBuffer == null) {
            return;
        }

        // Reuse vanilla's cloud render types so Fabulous targets, resource-pack
        // cloud textures, fog, and shader hooks retain their expected pipeline.
        FogRenderer.levelFogColor();
        poseStack.pushPose();
        poseStack.mulPose(frustumMatrix);
        poseStack.translate(
                builtOriginTileX * (double) CloudCoverageModel.CLOUD_TILE_SIZE - camX,
                cloudHeight - camY + CLOUD_BASE_OFFSET,
                builtOriginTileZ * (double) CloudCoverageModel.CLOUD_TILE_SIZE - camZ
        );
        cloudBuffer.bind();
        int firstPass = builtCloudStatus == CloudStatus.FANCY ? 0 : 1;
        for (int pass = firstPass; pass < 2; pass++) {
            RenderType renderType = pass == 0 ? RenderType.cloudsDepthOnly() : RenderType.clouds();
            renderType.setupRenderState();
            cloudBuffer.drawWithShader(
                    poseStack.last().pose(),
                    projectionMatrix,
                    RenderSystem.getShader()
            );
            renderType.clearRenderState();
        }
        VertexBuffer.unbind();
        poseStack.popPose();
    }

    /**
     * Returns whether the localized renderer is asking {@code ClientLevel} for
     * a daylight-only base color. The localized color mixin uses this guard to
     * avoid applying camera rain darkness before per-cloud shading is added.
     */
    public static boolean isSamplingBaseCloudColor() {
        return samplingBaseCloudColor;
    }

    /** Returns compact immutable values for the F3 weather diagnostics. */
    public static Diagnostics diagnostics() {
        return diagnostics;
    }

    /** Releases the cached cloud VBO and resets all per-level render state. */
    public static void clear() {
        closeCloudBuffer();
        renderedLevel = null;
        builtCloudStatus = null;
        builtSettings = null;
        builtOriginTileX = Integer.MIN_VALUE;
        builtOriginTileZ = Integer.MIN_VALUE;
        builtSequence = Long.MIN_VALUE;
        builtBaseColor = Vec3.ZERO;
        lastBuildTick = Long.MIN_VALUE;
        lastMotionTicks = Double.NaN;
        windDetailOffsetX = 0.0;
        windDetailOffsetZ = 0.0;
        builtWindDetailOffsetX = 0.0;
        builtWindDetailOffsetZ = 0.0;
        builtMorphologyPotential = false;
        builtTransitionComplete = false;
        meshCacheValid = false;
        samplingBaseCloudColor = false;
        diagnostics = Diagnostics.INACTIVE;
    }

    private static void prepareLevel(ClientLevel level, double renderTicks) {
        if (renderedLevel == level) {
            return;
        }
        clear();
        renderedLevel = level;
        lastMotionTicks = renderTicks;
    }

    private static void advanceWindDetail(
            ClientLevel level,
            double camX,
            double camZ,
            double renderTicks,
            double speedBlocksPerSecond
    ) {
        if (!Double.isFinite(lastMotionTicks)) {
            lastMotionTicks = renderTicks;
            return;
        }
        double elapsedSeconds = (renderTicks - lastMotionTicks) / 20.0;
        lastMotionTicks = renderTicks;
        if (elapsedSeconds <= 0.0 || elapsedSeconds > 0.25 || speedBlocksPerSecond <= 0.0) {
            return;
        }

        CloudFieldSample field = ClientWeatherCoordinator.cloudFieldAt(level, camX, camZ);
        windDetailOffsetX = wrapMotion(
                windDetailOffsetX + field.windX() * field.support() * speedBlocksPerSecond * elapsedSeconds
        );
        windDetailOffsetZ = wrapMotion(
                windDetailOffsetZ + field.windZ() * field.support() * speedBlocksPerSecond * elapsedSeconds
        );
    }

    private static boolean needsRebuild(
            ClientWeatherCoordinator.ClientStateView state,
            WeatherRenderingConfig.Settings settings,
            CloudStatus cloudStatus,
            int originTileX,
            int originTileZ,
            long ticks,
            Vec3 baseColor
    ) {
        if (!meshCacheValid
                || state.sequence() != builtSequence
                || originTileX != builtOriginTileX
                || originTileZ != builtOriginTileZ
                || cloudStatus != builtCloudStatus
                || !settings.equals(builtSettings)
                || (builtMorphologyPotential && builtBaseColor.distanceToSqr(baseColor) > 2.0E-4)) {
            return true;
        }
        long elapsed = ticks >= lastBuildTick ? ticks - lastBuildTick : Long.MAX_VALUE;
        return elapsed >= settings.rebuildIntervalTicks()
                && (state.interpolationProgress() < 0.999
                || !builtTransitionComplete
                || (builtMorphologyPotential && detailMovedSinceBuild()));
    }

    private static void rebuild(
            ClientLevel level,
            ClientWeatherCoordinator.ClientStateView state,
            WeatherRenderingConfig.Settings settings,
            CloudStatus cloudStatus,
            int originTileX,
            int originTileZ,
            long ticks,
            Vec3 baseColor
    ) {
        int radius = boundedRadius(settings);
        int diameter = radius * 2 + 1;
        byte[] heights = new byte[diameter * diameter];
        float[] darkness = new float[heights.length];
        float[] opacity = new float[heights.length];
        double coverageSum = 0.0;
        int visibleTiles = 0;
        boolean morphologyPotential = false;

        // Sample at each voxel center. Precipitation bypasses morphology noise,
        // guaranteeing that every meaningful rainy tile has cloud overhead.
        for (int localZ = -radius; localZ <= radius; localZ++) {
            for (int localX = -radius; localX <= radius; localX++) {
                if (localX * localX + localZ * localZ > radius * radius) {
                    continue;
                }
                int worldTileX = originTileX + localX;
                int worldTileZ = originTileZ + localZ;
                CloudFieldSample field = ClientWeatherCoordinator.cloudFieldAt(
                        level,
                        worldTileX * (double) CloudCoverageModel.CLOUD_TILE_SIZE
                                + CloudCoverageModel.CLOUD_TILE_SIZE * 0.5,
                        worldTileZ * (double) CloudCoverageModel.CLOUD_TILE_SIZE
                                + CloudCoverageModel.CLOUD_TILE_SIZE * 0.5
                );
                double coverage = CloudCoverageModel.coverage(field);
                morphologyPotential |= coverage > 0.015
                        || field.effectivePrecipitation()
                        >= CloudCoverageModel.PRECIPITATION_COVERAGE_THRESHOLD;
                if (!CloudCoverageModel.isPresent(
                        field,
                        worldTileX,
                        worldTileZ,
                        windDetailOffsetX,
                        windDetailOffsetZ
                )) {
                    continue;
                }

                int index = index(localX + radius, localZ + radius, diameter);
                heights[index] = (byte) CloudCoverageModel.thickness(field);
                darkness[index] = (float) CloudCoverageModel.darkness(field);
                opacity[index] = (float) CloudCoverageModel.opacity(field, settings.opacityMultiplier());
                coverageSum += coverage;
                visibleTiles++;
            }
        }

        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL
        );
        int vertices = cloudStatus == CloudStatus.FANCY
                ? emitFancyClouds(builder, heights, darkness, opacity, diameter, radius, baseColor)
                : emitFastClouds(builder, heights, darkness, opacity, diameter, radius, baseColor);
        MeshData mesh = builder.build();
        replaceCloudBuffer(mesh);
        // A null VBO is a valid cached result for a completely clear field.
        // Keeping this sentinel prevents rebuilding thousands of empty tiles
        // every frame merely because BufferBuilder produced no mesh.
        meshCacheValid = true;

        builtCloudStatus = cloudStatus;
        builtSettings = settings;
        builtOriginTileX = originTileX;
        builtOriginTileZ = originTileZ;
        builtSequence = state.sequence();
        builtBaseColor = baseColor;
        lastBuildTick = ticks;
        builtWindDetailOffsetX = windDetailOffsetX;
        builtWindDetailOffsetZ = windDetailOffsetZ;
        builtMorphologyPotential = morphologyPotential;
        builtTransitionComplete = state.interpolationProgress() >= 0.999;
        diagnostics = new Diagnostics(
                true,
                visibleTiles,
                vertices,
                visibleTiles == 0 ? 0.0 : coverageSum / visibleTiles,
                windDetailOffsetX,
                windDetailOffsetZ
        );
    }

    private static int emitFastClouds(
            BufferBuilder builder,
            byte[] heights,
            float[] darkness,
            float[] opacity,
            int diameter,
            int radius,
            Vec3 baseColor
    ) {
        int vertices = 0;
        for (int gridZ = 0; gridZ < diameter; gridZ++) {
            for (int gridX = 0; gridX < diameter; gridX++) {
                int cell = index(gridX, gridZ, diameter);
                if (heights[cell] == 0) {
                    continue;
                }
                float x0 = (gridX - radius) * CloudCoverageModel.CLOUD_TILE_SIZE;
                float z0 = (gridZ - radius) * CloudCoverageModel.CLOUD_TILE_SIZE;
                float x1 = x0 + CloudCoverageModel.CLOUD_TILE_SIZE;
                float z1 = z0 + CloudCoverageModel.CLOUD_TILE_SIZE;
                float light = (float) (1.0 - darkness[cell] * 0.38);
                emitHorizontalQuad(builder, x0, x1, 0.0F, z0, z1, baseColor, light, opacity[cell], 1.0F);
                vertices += 4;
            }
        }
        return vertices;
    }

    private static int emitFancyClouds(
            BufferBuilder builder,
            byte[] heights,
            float[] darkness,
            float[] opacity,
            int diameter,
            int radius,
            Vec3 baseColor
    ) {
        int vertices = 0;
        for (int gridZ = 0; gridZ < diameter; gridZ++) {
            for (int gridX = 0; gridX < diameter; gridX++) {
                int cell = index(gridX, gridZ, diameter);
                int height = heights[cell];
                if (height == 0) {
                    continue;
                }
                float x0 = (gridX - radius) * CloudCoverageModel.CLOUD_TILE_SIZE;
                float z0 = (gridZ - radius) * CloudCoverageModel.CLOUD_TILE_SIZE;
                float x1 = x0 + CloudCoverageModel.CLOUD_TILE_SIZE;
                float z1 = z0 + CloudCoverageModel.CLOUD_TILE_SIZE;
                float alpha = opacity[cell];
                float stormDarkness = darkness[cell];

                emitHorizontalQuad(
                        builder,
                        x0,
                        x1,
                        height - 9.765625E-4F,
                        z0,
                        z1,
                        baseColor,
                        (float) (1.0 - stormDarkness * 0.34),
                        alpha,
                        1.0F
                );
                emitHorizontalQuad(
                        builder,
                        x0,
                        x1,
                        0.0F,
                        z0,
                        z1,
                        baseColor,
                        (float) Math.max(0.22, 0.70 - stormDarkness * 0.42),
                        alpha,
                        -1.0F
                );
                vertices += 8;

                vertices += emitWestSide(
                        builder,
                        x0,
                        z0,
                        z1,
                        neighborHeight(heights, diameter, gridX - 1, gridZ),
                        height,
                        baseColor,
                        stormDarkness,
                        alpha
                );
                vertices += emitEastSide(
                        builder,
                        x1 - 9.765625E-4F,
                        z0,
                        z1,
                        neighborHeight(heights, diameter, gridX + 1, gridZ),
                        height,
                        baseColor,
                        stormDarkness,
                        alpha
                );
                vertices += emitNorthSide(
                        builder,
                        x0,
                        x1,
                        z0,
                        neighborHeight(heights, diameter, gridX, gridZ - 1),
                        height,
                        baseColor,
                        stormDarkness,
                        alpha
                );
                vertices += emitSouthSide(
                        builder,
                        x0,
                        x1,
                        z1 - 9.765625E-4F,
                        neighborHeight(heights, diameter, gridX, gridZ + 1),
                        height,
                        baseColor,
                        stormDarkness,
                        alpha
                );
            }
        }
        return vertices;
    }

    private static void emitHorizontalQuad(
            BufferBuilder builder,
            float x0,
            float x1,
            float y,
            float z0,
            float z1,
            Vec3 baseColor,
            float light,
            float alpha,
            float normalY
    ) {
        vertex(builder, x0, y, z1, baseColor, light, alpha, 0.0F, normalY, 0.0F);
        vertex(builder, x1, y, z1, baseColor, light, alpha, 0.0F, normalY, 0.0F);
        vertex(builder, x1, y, z0, baseColor, light, alpha, 0.0F, normalY, 0.0F);
        vertex(builder, x0, y, z0, baseColor, light, alpha, 0.0F, normalY, 0.0F);
    }

    private static int emitWestSide(
            BufferBuilder builder,
            float x,
            float z0,
            float z1,
            int adjacentHeight,
            int height,
            Vec3 color,
            float darkness,
            float alpha
    ) {
        if (adjacentHeight >= height) {
            return 0;
        }
        float light = (float) Math.max(0.24, 0.88 - darkness * 0.38);
        vertex(builder, x, adjacentHeight, z1, color, light, alpha, -1.0F, 0.0F, 0.0F);
        vertex(builder, x, height, z1, color, light, alpha, -1.0F, 0.0F, 0.0F);
        vertex(builder, x, height, z0, color, light, alpha, -1.0F, 0.0F, 0.0F);
        vertex(builder, x, adjacentHeight, z0, color, light, alpha, -1.0F, 0.0F, 0.0F);
        return 4;
    }

    private static int emitEastSide(
            BufferBuilder builder,
            float x,
            float z0,
            float z1,
            int adjacentHeight,
            int height,
            Vec3 color,
            float darkness,
            float alpha
    ) {
        if (adjacentHeight >= height) {
            return 0;
        }
        float light = (float) Math.max(0.24, 0.88 - darkness * 0.38);
        vertex(builder, x, adjacentHeight, z0, color, light, alpha, 1.0F, 0.0F, 0.0F);
        vertex(builder, x, height, z0, color, light, alpha, 1.0F, 0.0F, 0.0F);
        vertex(builder, x, height, z1, color, light, alpha, 1.0F, 0.0F, 0.0F);
        vertex(builder, x, adjacentHeight, z1, color, light, alpha, 1.0F, 0.0F, 0.0F);
        return 4;
    }

    private static int emitNorthSide(
            BufferBuilder builder,
            float x0,
            float x1,
            float z,
            int adjacentHeight,
            int height,
            Vec3 color,
            float darkness,
            float alpha
    ) {
        if (adjacentHeight >= height) {
            return 0;
        }
        float light = (float) Math.max(0.22, 0.80 - darkness * 0.40);
        vertex(builder, x0, adjacentHeight, z, color, light, alpha, 0.0F, 0.0F, -1.0F);
        vertex(builder, x0, height, z, color, light, alpha, 0.0F, 0.0F, -1.0F);
        vertex(builder, x1, height, z, color, light, alpha, 0.0F, 0.0F, -1.0F);
        vertex(builder, x1, adjacentHeight, z, color, light, alpha, 0.0F, 0.0F, -1.0F);
        return 4;
    }

    private static int emitSouthSide(
            BufferBuilder builder,
            float x0,
            float x1,
            float z,
            int adjacentHeight,
            int height,
            Vec3 color,
            float darkness,
            float alpha
    ) {
        if (adjacentHeight >= height) {
            return 0;
        }
        float light = (float) Math.max(0.22, 0.80 - darkness * 0.40);
        vertex(builder, x1, adjacentHeight, z, color, light, alpha, 0.0F, 0.0F, 1.0F);
        vertex(builder, x1, height, z, color, light, alpha, 0.0F, 0.0F, 1.0F);
        vertex(builder, x0, height, z, color, light, alpha, 0.0F, 0.0F, 1.0F);
        vertex(builder, x0, adjacentHeight, z, color, light, alpha, 0.0F, 0.0F, 1.0F);
        return 4;
    }

    private static void vertex(
            BufferBuilder builder,
            float x,
            float y,
            float z,
            Vec3 baseColor,
            float light,
            float alpha,
            float normalX,
            float normalY,
            float normalZ
    ) {
        builder.addVertex(x, y, z)
                // Sampling one known opaque vanilla cloud texel makes occupancy
                // authoritative; shape still comes from block geometry and the
                // active minecraft:clouds texture/render pipeline.
                .setUv(CLOUD_U, CLOUD_V)
                .setColor(
                        unit((float) baseColor.x * light),
                        unit((float) baseColor.y * light),
                        unit((float) baseColor.z * light),
                        unit(alpha)
                )
                .setNormal(normalX, normalY, normalZ);
    }

    private static Vec3 baseCloudColor(ClientLevel level, float partialTick) {
        samplingBaseCloudColor = true;
        try {
            return level.getCloudColor(partialTick);
        } finally {
            samplingBaseCloudColor = false;
        }
    }

    private static int boundedRadius(WeatherRenderingConfig.Settings settings) {
        int requested = (int) Math.ceil(
                settings.renderDistanceBlocks() / (double) CloudCoverageModel.CLOUD_TILE_SIZE
        );
        int tileCapRadius = Math.max(
                1,
                (int) Math.floor(Math.sqrt(settings.maximumCloudTiles() / Math.PI))
        );
        return Math.min(requested, tileCapRadius);
    }

    private static int neighborHeight(byte[] heights, int diameter, int x, int z) {
        if (x < 0 || z < 0 || x >= diameter || z >= diameter) {
            return 0;
        }
        return heights[index(x, z, diameter)];
    }

    private static int index(int x, int z, int diameter) {
        return z * diameter + x;
    }

    private static void replaceCloudBuffer(MeshData mesh) {
        closeCloudBuffer();
        if (mesh == null) {
            return;
        }
        VertexBuffer next = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        next.bind();
        next.upload(mesh);
        VertexBuffer.unbind();
        cloudBuffer = next;
    }

    private static void closeCloudBuffer() {
        VertexBuffer closing = cloudBuffer;
        cloudBuffer = null;
        if (closing == null) {
            return;
        }
        if (RenderSystem.isOnRenderThread()) {
            closing.close();
        } else {
            RenderSystem.recordRenderCall(closing::close);
        }
    }

    private static double wrapMotion(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        if (Math.abs(value) <= MOTION_WRAP_BLOCKS) {
            return value;
        }
        return Math.IEEEremainder(value, MOTION_WRAP_BLOCKS * 2.0);
    }

    private static boolean detailMovedSinceBuild() {
        double xDistance = Math.abs(windDetailOffsetX - builtWindDetailOffsetX);
        double zDistance = Math.abs(windDetailOffsetZ - builtWindDetailOffsetZ);
        return Math.hypot(xDistance, zDistance) >= 0.75;
    }

    private static int floorToInt(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private static float unit(float value) {
        if (!Float.isFinite(value)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    /** Immutable values shown in the F3 weather diagnostics. */
    public record Diagnostics(
            boolean active,
            int visibleTiles,
            int vertices,
            double averageCoverage,
            double windDetailOffsetX,
            double windDetailOffsetZ
    ) {
        private static final Diagnostics INACTIVE = new Diagnostics(false, 0, 0, 0.0, 0.0, 0.0);
    }
}
