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
import com.thunder.wildernessodysseyapi.weather.api.CloudType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.Map;

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
    private static boolean builtRaymarched;
    private static boolean builtVolumetric;
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
                windDetailOffsetZ,
                diagnostics.mode(),
                diagnostics.layers(),
                diagnostics.cloudType()
        );
        if (cloudBuffer == null) {
            return;
        }

        FogRenderer.levelFogColor();
        poseStack.pushPose();
        poseStack.mulPose(frustumMatrix);
        poseStack.translate(
                builtOriginTileX * (double) CloudCoverageModel.CLOUD_TILE_SIZE - camX,
                cloudHeight - camY + CLOUD_BASE_OFFSET,
                builtOriginTileZ * (double) CloudCoverageModel.CLOUD_TILE_SIZE - camZ
        );
        cloudBuffer.bind();
        if (builtRaymarched) {
            RaymarchedCloudShaders.updateUniforms(
                    (float) (renderTicks / 20.0),
                    windDetailOffsetX,
                    windDetailOffsetZ,
                    builtOriginTileX,
                    builtOriginTileZ,
                    camX,
                    camY,
                    camZ,
                    cloudHeight,
                    level.getSunAngle(partialTick),
                    settings
            );
            RenderType renderType = RaymarchedCloudRenderTypes.raymarchedClouds();
            renderType.setupRenderState();
            cloudBuffer.drawWithShader(
                    poseStack.last().pose(),
                    projectionMatrix,
                    RenderSystem.getShader()
            );
            renderType.clearRenderState();
        } else if (builtVolumetric) {
            VolumetricCloudShaders.updateUniforms(
                    (float) (renderTicks / 20.0),
                    windDetailOffsetX,
                    windDetailOffsetZ,
                    builtOriginTileX,
                    builtOriginTileZ,
                    level.getSunAngle(partialTick),
                    settings.volumetricDetailStrength()
            );
            RenderType renderType = VolumetricCloudRenderTypes.volumetricClouds();
            renderType.setupRenderState();
            cloudBuffer.drawWithShader(
                    poseStack.last().pose(),
                    projectionMatrix,
                    RenderSystem.getShader()
            );
            renderType.clearRenderState();
        } else {
            // Reuse vanilla's cloud render types for the compatibility mesh so
            // Fabulous targets, resource packs, fog, and shader hooks survive.
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
        builtRaymarched = false;
        builtVolumetric = false;
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
        windDetailOffsetX = finiteMotion(
                windDetailOffsetX + field.cloudWindX() * field.support() * speedBlocksPerSecond * elapsedSeconds
        );
        windDetailOffsetZ = finiteMotion(
                windDetailOffsetZ + field.cloudWindZ() * field.support() * speedBlocksPerSecond * elapsedSeconds
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
        float[] baseOffsets = new float[diameter * diameter];
        CloudFieldSample[] fields = new CloudFieldSample[heights.length];
        CloudLayerProfile[] profiles = new CloudLayerProfile[heights.length];
        float[] darkness = new float[heights.length];
        float[] opacity = new float[heights.length];
        Map<CloudType, Double> cloudTypeWeights = new EnumMap<>(CloudType.class);
        double coverageSum = 0.0;
        int visibleTiles = 0;
        boolean morphologyPotential = false;
        CloudTileCoverageModel.PrecipitationSampler precipitationSampler =
                (blockX, blockZ) -> ClientWeatherCoordinator.visualPrecipitationIntensityAt(
                        level,
                        blockX,
                        blockZ
                );

        // Sample each voxel's center for its shape, then prove precipitation
        // overlap across bilinear sub-rectangles so thin rainy edges stay covered.
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
                boolean overlapsPrecipitation = field.effectivePrecipitation()
                        >= CloudCoverageModel.PRECIPITATION_COVERAGE_THRESHOLD
                        || CloudTileCoverageModel.overlapsPrecipitation(
                                worldTileX,
                                worldTileZ,
                                state.cellSize(),
                                precipitationSampler
                        );
                morphologyPotential |= coverage > 0.015
                        || overlapsPrecipitation;
                if (!overlapsPrecipitation && !CloudCoverageModel.isPresent(
                        field,
                        worldTileX,
                        worldTileZ,
                        windDetailOffsetX,
                        windDetailOffsetZ
                )) {
                    continue;
                }

                int index = index(localX + radius, localZ + radius, diameter);
                fields[index] = field;
                CloudLayerProfile profile = CloudLayerProfile.evaluate(field);
                profiles[index] = profile;
                CloudLayerProfile.BandProfile dominantBand = profile.dominantBand();
                baseOffsets[index] = (float) dominantBand.baseOffsetBlocks();
                heights[index] = (byte) CloudCoverageModel.thickness(field);
                darkness[index] = (float) CloudCoverageModel.darkness(field);
                opacity[index] = (float) CloudCoverageModel.opacity(field, settings.opacityMultiplier());
                coverageSum += coverage;
                cloudTypeWeights.merge(profile.dominantType(), Math.max(0.01, coverage), Double::sum);
                visibleTiles++;
            }
        }

        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL
        );
        boolean raymarched = cloudStatus == CloudStatus.FANCY
                && RaymarchedCloudShaders.shouldUse(settings);
        boolean volumetric = cloudStatus == CloudStatus.FANCY
                && !raymarched
                && VolumetricCloudShaders.shouldUse(settings);
        int vertices;
        if (raymarched) {
            vertices = emitRaymarchedClouds(
                    builder,
                    profiles,
                    darkness,
                    opacity,
                    diameter,
                    radius,
                    baseColor
            );
        } else if (volumetric) {
            vertices = emitVolumetricClouds(
                    builder,
                    fields,
                    profiles,
                    darkness,
                    opacity,
                    diameter,
                    radius,
                    baseColor,
                    settings.volumetricLayerCount()
            );
        } else if (cloudStatus == CloudStatus.FANCY) {
            vertices = emitFancyClouds(
                    builder, heights, baseOffsets, darkness, opacity, diameter, radius, baseColor
            );
        } else {
            vertices = emitFastClouds(
                    builder, heights, baseOffsets, darkness, opacity, diameter, radius, baseColor
            );
        }
        DistantCloudResult distant = emitDistantCloudLayer(
                builder,
                level,
                settings,
                originTileX,
                originTileZ,
                radius * CloudCoverageModel.CLOUD_TILE_SIZE,
                baseColor,
                raymarched,
                volumetric
        );
        vertices += distant.vertices();
        visibleTiles += distant.tiles();
        coverageSum += distant.coverageSum();
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
        builtRaymarched = raymarched;
        builtVolumetric = volumetric;
        builtMorphologyPotential = morphologyPotential;
        builtTransitionComplete = state.interpolationProgress() >= 0.999;
        diagnostics = new Diagnostics(
                true,
                visibleTiles,
                vertices,
                visibleTiles == 0 ? 0.0 : coverageSum / visibleTiles,
                windDetailOffsetX,
                windDetailOffsetZ,
                raymarched ? "raymarch" : volumetric ? "volume" : cloudStatus == CloudStatus.FANCY ? "voxel" : "fast",
                raymarched ? settings.raymarchSteps() : volumetric ? settings.volumetricLayerCount() : 1,
                dominantCloudType(cloudTypeWeights).displayName()
        );
    }

    /** Emits only the upper and lower shells; the fragment shader integrates the interior density. */
    private static int emitRaymarchedClouds(
            BufferBuilder builder,
            CloudLayerProfile[] profiles,
            float[] darkness,
            float[] opacity,
            int diameter,
            int radius,
            Vec3 baseColor
    ) {
        int vertices = 0;
        float noiseScale = VolumetricCloudShaders.worldNoiseScale();
        for (int gridZ = 0; gridZ < diameter; gridZ++) {
            for (int gridX = 0; gridX < diameter; gridX++) {
                int cell = index(gridX, gridZ, diameter);
                CloudLayerProfile profile = profiles[cell];
                if (profile == null) {
                    continue;
                }

                // A small overlap closes floating-point cracks between adjacent
                // slabs. Depth writing makes the overlap single-layered.
                float overlap = 0.28F;
                float x0 = (gridX - radius) * CloudCoverageModel.CLOUD_TILE_SIZE - overlap;
                float z0 = (gridZ - radius) * CloudCoverageModel.CLOUD_TILE_SIZE - overlap;
                float x1 = (gridX - radius + 1) * CloudCoverageModel.CLOUD_TILE_SIZE + overlap;
                float z1 = (gridZ - radius + 1) * CloudCoverageModel.CLOUD_TILE_SIZE + overlap;
                float storm = darkness[cell];
                for (CloudLayerProfile.BandProfile band : profile.bands()) {
                    if (!band.visible()) {
                        continue;
                    }
                    float base = (float) band.baseOffsetBlocks();
                    float depth = (float) Math.min(127.5, band.depthBlocks());
                    float top = base + depth;
                    float alpha00 = cornerBandOpacity(
                            profiles, opacity, diameter, gridX, gridZ, band.band()
                    );
                    float alpha10 = cornerBandOpacity(
                            profiles, opacity, diameter, gridX + 1, gridZ, band.band()
                    );
                    float alpha11 = cornerBandOpacity(
                            profiles, opacity, diameter, gridX + 1, gridZ + 1, band.band()
                    );
                    float alpha01 = cornerBandOpacity(
                            profiles, opacity, diameter, gridX, gridZ + 1, band.band()
                    );
                    float bottomLight = Math.max(0.34F, 0.72F - storm * 0.32F);
                    float topLight = Math.max(0.54F, 0.96F - storm * 0.20F);

                    raymarchedVertex(builder, x0, base, z1, x0 * noiseScale, z1 * noiseScale,
                            baseColor, bottomLight, alpha01, -depth, storm, band);
                    raymarchedVertex(builder, x1, base, z1, x1 * noiseScale, z1 * noiseScale,
                            baseColor, bottomLight, alpha11, -depth, storm, band);
                    raymarchedVertex(builder, x1, base, z0, x1 * noiseScale, z0 * noiseScale,
                            baseColor, bottomLight, alpha10, -depth, storm, band);
                    raymarchedVertex(builder, x0, base, z0, x0 * noiseScale, z0 * noiseScale,
                            baseColor, bottomLight, alpha00, -depth, storm, band);

                    raymarchedVertex(builder, x0, top, z0, x0 * noiseScale, z0 * noiseScale,
                            baseColor, topLight, alpha00, depth, storm, band);
                    raymarchedVertex(builder, x1, top, z0, x1 * noiseScale, z0 * noiseScale,
                            baseColor, topLight, alpha10, depth, storm, band);
                    raymarchedVertex(builder, x1, top, z1, x1 * noiseScale, z1 * noiseScale,
                            baseColor, topLight, alpha11, depth, storm, band);
                    raymarchedVertex(builder, x0, top, z1, x0 * noiseScale, z1 * noiseScale,
                            baseColor, topLight, alpha01, depth, storm, band);
                    vertices += 8;
                }
            }
        }
        return vertices;
    }

    /** Averages the four cells sharing one corner so density has no tile-sized steps. */
    private static float cornerBandOpacity(
            CloudLayerProfile[] profiles,
            float[] opacity,
            int diameter,
            int cornerX,
            int cornerZ,
            CloudLayerProfile.CloudBand targetBand
    ) {
        double sum = 0.0;
        int samples = 0;
        for (int z = cornerZ - 1; z <= cornerZ; z++) {
            for (int x = cornerX - 1; x <= cornerX; x++) {
                if (x < 0 || z < 0 || x >= diameter || z >= diameter) {
                    continue;
                }
                samples++;
                int cell = index(x, z, diameter);
                CloudLayerProfile profile = profiles[cell];
                if (profile == null) {
                    continue;
                }
                CloudLayerProfile.BandProfile band = profile.bands().get(targetBand.ordinal());
                if (band.visible()) {
                    sum += Math.min(0.985, opacity[cell] * band.density());
                }
            }
        }
        return samples == 0 ? 0.0F : (float) (sum / samples);
    }

    private static int emitVolumetricClouds(
            BufferBuilder builder,
            CloudFieldSample[] fields,
            CloudLayerProfile[] profiles,
            float[] darkness,
            float[] opacity,
            int diameter,
            int radius,
            Vec3 baseColor,
            int layerCount
    ) {
        int vertices = 0;
        float noiseScale = VolumetricCloudShaders.worldNoiseScale();
        for (int gridZ = 0; gridZ < diameter; gridZ++) {
            for (int gridX = 0; gridX < diameter; gridX++) {
                int cell = index(gridX, gridZ, diameter);
                CloudFieldSample field = fields[cell];
                CloudLayerProfile profile = profiles[cell];
                if (field == null || profile == null) {
                    continue;
                }

                float x0 = (gridX - radius) * CloudCoverageModel.CLOUD_TILE_SIZE;
                float z0 = (gridZ - radius) * CloudCoverageModel.CLOUD_TILE_SIZE;
                float x1 = x0 + CloudCoverageModel.CLOUD_TILE_SIZE;
                float z1 = z0 + CloudCoverageModel.CLOUD_TILE_SIZE;
                float coverage = (float) CloudCoverageModel.coverage(field);
                float storm = darkness[cell];

                // Each genus may contribute more than one physical deck. The
                // packed normal carries band, local layer, morphology, and
                // storm darkness without expanding the vertex format.
                for (CloudLayerProfile.BandProfile band : profile.bands()) {
                    if (!band.visible()) {
                        continue;
                    }
                    int bandLayers = Math.max(2, (int) Math.round(
                            layerCount * (0.45 + band.density() * 0.55)
                    ));
                    float base = (float) band.baseOffsetBlocks();
                    float depth = (float) band.depthBlocks();
                    float bandOpacity = (float) Math.min(0.985, opacity[cell] * band.density());
                    float sliceAlpha = (float) CloudColumnModel.sliceOpacity(bandOpacity, bandLayers);
                    for (int layerIndex = bandLayers - 1; layerIndex >= 0; layerIndex--) {
                        float layer = (layerIndex + 0.5F) / bandLayers;
                        float y = base + depth * layer;
                        float light = 0.86F + layer * 0.14F;
                        volumetricVertex(
                                builder, x0, y, z1, x0 * noiseScale, z1 * noiseScale,
                                baseColor, light, sliceAlpha, layer, coverage, storm, band
                        );
                        volumetricVertex(
                                builder, x1, y, z1, x1 * noiseScale, z1 * noiseScale,
                                baseColor, light, sliceAlpha, layer, coverage, storm, band
                        );
                        volumetricVertex(
                                builder, x1, y, z0, x1 * noiseScale, z0 * noiseScale,
                                baseColor, light, sliceAlpha, layer, coverage, storm, band
                        );
                        volumetricVertex(
                                builder, x0, y, z0, x0 * noiseScale, z0 * noiseScale,
                                baseColor, light, sliceAlpha, layer, coverage, storm, band
                        );
                        vertices += 4;
                    }
                }
            }
        }
        return vertices;
    }

    /** Emits sparse horizon patches and deeper storm-front walls at a coarse cadence. */
    private static DistantCloudResult emitDistantCloudLayer(
            BufferBuilder builder,
            ClientLevel level,
            WeatherRenderingConfig.Settings settings,
            int originTileX,
            int originTileZ,
            int nearDistance,
            Vec3 baseColor,
            boolean raymarched,
            boolean volumetric
    ) {
        if (!settings.distantCloudLayer()) {
            return DistantCloudResult.NONE;
        }
        int spacing = settings.distantCloudSpacingBlocks();
        int maximumDistance = settings.distantCloudDistanceBlocks();
        int maximumTiles = settings.maximumDistantCloudTiles();
        int tiles = 0;
        int vertices = 0;
        double coverageSum = 0.0;
        double originBlockX = originTileX * (double) CloudCoverageModel.CLOUD_TILE_SIZE;
        double originBlockZ = originTileZ * (double) CloudCoverageModel.CLOUD_TILE_SIZE;
        double minimumSquared = Math.max(nearDistance + spacing, settings.renderDistanceBlocks());
        minimumSquared *= minimumSquared;
        double maximumSquared = (double) maximumDistance * maximumDistance;
        float noiseScale = VolumetricCloudShaders.worldNoiseScale();
        for (int z = -maximumDistance; z <= maximumDistance && tiles < maximumTiles; z += spacing) {
            for (int x = -maximumDistance; x <= maximumDistance && tiles < maximumTiles; x += spacing) {
                double distanceSquared = (double) x * x + (double) z * z;
                if (distanceSquared < minimumSquared || distanceSquared > maximumSquared) {
                    continue;
                }
                CloudFieldSample field = ClientWeatherCoordinator.cloudFieldAt(
                        level,
                        originBlockX + x,
                        originBlockZ + z
                );
                double coverage = CloudCoverageModel.coverage(field);
                if (field.support() < 0.08 || coverage < 0.07) {
                    continue;
                }
                CloudLayerProfile profile = CloudLayerProfile.evaluate(field);
                CloudLayerProfile.BandProfile band = profile.dominantBand();
                float base = (float) band.baseOffsetBlocks();
                float depth = (float) Math.max(2.0, band.depthBlocks() * (0.52 + field.stormEnergy() * 0.48));
                float top = base + depth;
                float x0 = x - spacing * 0.48F;
                float x1 = x + spacing * 0.48F;
                float z0 = z - spacing * 0.48F;
                float z1 = z + spacing * 0.48F;
                float storm = (float) field.stormEnergy();
                float alpha = (float) Math.min(0.70,
                        CloudCoverageModel.opacity(field, settings.opacityMultiplier()) * (0.30 + storm * 0.34));
                float light = Math.max(0.28F, 0.84F - storm * 0.48F);
                if (raymarched) {
                    raymarchedVertex(builder, x0, base, z1, x0 * noiseScale, z1 * noiseScale,
                            baseColor, light, alpha, -depth, storm, band);
                    raymarchedVertex(builder, x1, base, z1, x1 * noiseScale, z1 * noiseScale,
                            baseColor, light, alpha, -depth, storm, band);
                    raymarchedVertex(builder, x1, base, z0, x1 * noiseScale, z0 * noiseScale,
                            baseColor, light, alpha, -depth, storm, band);
                    raymarchedVertex(builder, x0, base, z0, x0 * noiseScale, z0 * noiseScale,
                            baseColor, light, alpha, -depth, storm, band);
                    raymarchedVertex(builder, x0, top, z0, x0 * noiseScale, z0 * noiseScale,
                            baseColor, Math.min(1.0F, light + 0.12F), alpha, depth, storm, band);
                    raymarchedVertex(builder, x1, top, z0, x1 * noiseScale, z0 * noiseScale,
                            baseColor, Math.min(1.0F, light + 0.12F), alpha, depth, storm, band);
                    raymarchedVertex(builder, x1, top, z1, x1 * noiseScale, z1 * noiseScale,
                            baseColor, Math.min(1.0F, light + 0.12F), alpha, depth, storm, band);
                    raymarchedVertex(builder, x0, top, z1, x0 * noiseScale, z1 * noiseScale,
                            baseColor, Math.min(1.0F, light + 0.12F), alpha, depth, storm, band);
                    vertices += 8;
                } else if (volumetric) {
                    float sliceAlpha = (float) CloudColumnModel.sliceOpacity(alpha, 2);
                    for (int layer = 0; layer < 2; layer++) {
                        float layerAmount = 0.30F + layer * 0.48F;
                        float y = base + depth * layerAmount;
                        volumetricVertex(builder, x0, y, z1, x0 * noiseScale, z1 * noiseScale,
                                baseColor, light, sliceAlpha, layerAmount, (float) coverage, storm, band);
                        volumetricVertex(builder, x1, y, z1, x1 * noiseScale, z1 * noiseScale,
                                baseColor, light, sliceAlpha, layerAmount, (float) coverage, storm, band);
                        volumetricVertex(builder, x1, y, z0, x1 * noiseScale, z0 * noiseScale,
                                baseColor, light, sliceAlpha, layerAmount, (float) coverage, storm, band);
                        volumetricVertex(builder, x0, y, z0, x0 * noiseScale, z0 * noiseScale,
                                baseColor, light, sliceAlpha, layerAmount, (float) coverage, storm, band);
                        vertices += 4;
                    }
                } else {
                    emitHorizontalQuad(builder, x0, x1, top, z0, z1, baseColor, light, alpha, 1.0F);
                    vertices += 4;
                }
                if (!volumetric && storm >= 0.42F) {
                    // A dark vertical rim makes an approaching squall line
                    // legible at the horizon without expensive distant voxels.
                    vertices += emitWestSide(builder, x0, z0, z1, Float.NEGATIVE_INFINITY,
                            base, top, baseColor, storm, alpha * 0.85F);
                    vertices += emitEastSide(builder, x1, z0, z1, Float.NEGATIVE_INFINITY,
                            base, top, baseColor, storm, alpha * 0.85F);
                    vertices += emitNorthSide(builder, x0, x1, z0, Float.NEGATIVE_INFINITY,
                            base, top, baseColor, storm, alpha * 0.85F);
                    vertices += emitSouthSide(builder, x0, x1, z1, Float.NEGATIVE_INFINITY,
                            base, top, baseColor, storm, alpha * 0.85F);
                }
                tiles++;
                coverageSum += coverage;
            }
        }
        return new DistantCloudResult(tiles, vertices, coverageSum);
    }

    private static void raymarchedVertex(
            BufferBuilder builder,
            float x,
            float y,
            float z,
            float noiseX,
            float noiseZ,
            Vec3 baseColor,
            float light,
            float alpha,
            float signedDepth,
            float storm,
            CloudLayerProfile.BandProfile band
    ) {
        float encodedDepth = Math.copySign(Math.min(1.0F, Math.abs(signedDepth) / 128.0F), signedDepth);
        float packedShape = (shapeCode(band.shape()) + 0.10F + unit(storm) * 0.80F) / 4.0F;
        builder.addVertex(x, y, z)
                .setUv(noiseX, noiseZ)
                .setColor(
                        unit((float) baseColor.x * light),
                        unit((float) baseColor.y * light),
                        unit((float) baseColor.z * light),
                        unit(alpha)
                )
                .setNormal(encodedDepth, unit((float) band.density()), unit(packedShape));
    }

    private static void volumetricVertex(
            BufferBuilder builder,
            float x,
            float y,
            float z,
            float noiseX,
            float noiseZ,
            Vec3 baseColor,
            float light,
            float alpha,
            float layer,
            float coverage,
            float storm,
            CloudLayerProfile.BandProfile band
    ) {
        // Margins keep byte-normalized normals away from integer boundaries,
        // preventing a high deck or convective family from decoding one bin low.
        float packedLayer = (band.band().ordinal() + 0.08F + unit(layer) * 0.84F) / 4.0F;
        float packedShape = (shapeCode(band.shape()) + 0.10F + unit(storm) * 0.80F) / 4.0F;
        builder.addVertex(x, y, z)
                .setUv(noiseX, noiseZ)
                .setColor(
                        unit((float) baseColor.x * light),
                        unit((float) baseColor.y * light),
                        unit((float) baseColor.z * light),
                        unit(alpha)
                )
                .setNormal(unit(packedLayer), unit(coverage), unit(packedShape));
    }

    private static int emitFastClouds(
            BufferBuilder builder,
            byte[] heights,
            float[] baseOffsets,
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
                emitHorizontalQuad(
                        builder, x0, x1, baseOffsets[cell], z0, z1,
                        baseColor, light, opacity[cell], 1.0F
                );
                vertices += 4;
            }
        }
        return vertices;
    }

    private static int emitFancyClouds(
            BufferBuilder builder,
            byte[] heights,
            float[] baseOffsets,
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
                float base = baseOffsets[cell];
                float top = base + height;

                emitHorizontalQuad(
                        builder,
                        x0,
                        x1,
                        top - 9.765625E-4F,
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
                        base,
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
                        neighborTop(heights, baseOffsets, diameter, gridX - 1, gridZ),
                        base,
                        top,
                        baseColor,
                        stormDarkness,
                        alpha
                );
                vertices += emitEastSide(
                        builder,
                        x1 - 9.765625E-4F,
                        z0,
                        z1,
                        neighborTop(heights, baseOffsets, diameter, gridX + 1, gridZ),
                        base,
                        top,
                        baseColor,
                        stormDarkness,
                        alpha
                );
                vertices += emitNorthSide(
                        builder,
                        x0,
                        x1,
                        z0,
                        neighborTop(heights, baseOffsets, diameter, gridX, gridZ - 1),
                        base,
                        top,
                        baseColor,
                        stormDarkness,
                        alpha
                );
                vertices += emitSouthSide(
                        builder,
                        x0,
                        x1,
                        z1 - 9.765625E-4F,
                        neighborTop(heights, baseOffsets, diameter, gridX, gridZ + 1),
                        base,
                        top,
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
            float adjacentTop,
            float base,
            float top,
            Vec3 color,
            float darkness,
            float alpha
    ) {
        if (adjacentTop >= top) {
            return 0;
        }
        float lower = Math.max(base, adjacentTop);
        float light = (float) Math.max(0.24, 0.88 - darkness * 0.38);
        vertex(builder, x, lower, z1, color, light, alpha, -1.0F, 0.0F, 0.0F);
        vertex(builder, x, top, z1, color, light, alpha, -1.0F, 0.0F, 0.0F);
        vertex(builder, x, top, z0, color, light, alpha, -1.0F, 0.0F, 0.0F);
        vertex(builder, x, lower, z0, color, light, alpha, -1.0F, 0.0F, 0.0F);
        return 4;
    }

    private static int emitEastSide(
            BufferBuilder builder,
            float x,
            float z0,
            float z1,
            float adjacentTop,
            float base,
            float top,
            Vec3 color,
            float darkness,
            float alpha
    ) {
        if (adjacentTop >= top) {
            return 0;
        }
        float lower = Math.max(base, adjacentTop);
        float light = (float) Math.max(0.24, 0.88 - darkness * 0.38);
        vertex(builder, x, lower, z0, color, light, alpha, 1.0F, 0.0F, 0.0F);
        vertex(builder, x, top, z0, color, light, alpha, 1.0F, 0.0F, 0.0F);
        vertex(builder, x, top, z1, color, light, alpha, 1.0F, 0.0F, 0.0F);
        vertex(builder, x, lower, z1, color, light, alpha, 1.0F, 0.0F, 0.0F);
        return 4;
    }

    private static int emitNorthSide(
            BufferBuilder builder,
            float x0,
            float x1,
            float z,
            float adjacentTop,
            float base,
            float top,
            Vec3 color,
            float darkness,
            float alpha
    ) {
        if (adjacentTop >= top) {
            return 0;
        }
        float lower = Math.max(base, adjacentTop);
        float light = (float) Math.max(0.22, 0.80 - darkness * 0.40);
        vertex(builder, x0, lower, z, color, light, alpha, 0.0F, 0.0F, -1.0F);
        vertex(builder, x0, top, z, color, light, alpha, 0.0F, 0.0F, -1.0F);
        vertex(builder, x1, top, z, color, light, alpha, 0.0F, 0.0F, -1.0F);
        vertex(builder, x1, lower, z, color, light, alpha, 0.0F, 0.0F, -1.0F);
        return 4;
    }

    private static int emitSouthSide(
            BufferBuilder builder,
            float x0,
            float x1,
            float z,
            float adjacentTop,
            float base,
            float top,
            Vec3 color,
            float darkness,
            float alpha
    ) {
        if (adjacentTop >= top) {
            return 0;
        }
        float lower = Math.max(base, adjacentTop);
        float light = (float) Math.max(0.22, 0.80 - darkness * 0.40);
        vertex(builder, x1, lower, z, color, light, alpha, 0.0F, 0.0F, 1.0F);
        vertex(builder, x1, top, z, color, light, alpha, 0.0F, 0.0F, 1.0F);
        vertex(builder, x0, top, z, color, light, alpha, 0.0F, 0.0F, 1.0F);
        vertex(builder, x0, lower, z, color, light, alpha, 0.0F, 0.0F, 1.0F);
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

    private static float neighborTop(
            byte[] heights,
            float[] baseOffsets,
            int diameter,
            int x,
            int z
    ) {
        if (x < 0 || z < 0 || x >= diameter || z >= diameter) {
            return Float.NEGATIVE_INFINITY;
        }
        int cell = index(x, z, diameter);
        return heights[cell] == 0
                ? Float.NEGATIVE_INFINITY
                : baseOffsets[cell] + Byte.toUnsignedInt(heights[cell]);
    }

    private static CloudType dominantCloudType(Map<CloudType, Double> weights) {
        CloudType result = CloudType.CLEAR;
        double greatestWeight = 0.0;
        for (Map.Entry<CloudType, Double> entry : weights.entrySet()) {
            if (entry.getValue() > greatestWeight) {
                result = entry.getKey();
                greatestWeight = entry.getValue();
            }
        }
        return result;
    }

    private static int shapeCode(CloudType.Shape shape) {
        return switch (shape) {
            case CLEAR, WISPY -> 0;
            case LAYERED -> 1;
            case CELLULAR -> 2;
            case CONVECTIVE -> 3;
        };
    }

    private static int index(int x, int z, int diameter) {
        return z * diameter + x;
    }

    private record DistantCloudResult(int tiles, int vertices, double coverageSum) {
        private static final DistantCloudResult NONE = new DistantCloudResult(0, 0, 0.0);
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

    private static double finiteMotion(double value) {
        return Double.isFinite(value) ? value : 0.0;
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
            double windDetailOffsetZ,
            String mode,
            int layers,
            String cloudType
    ) {
        private static final Diagnostics INACTIVE =
                new Diagnostics(false, 0, 0, 0.0, 0.0, 0.0, "inactive", 0, "Clear");

        public Diagnostics(
                boolean active,
                int visibleTiles,
                int vertices,
                double averageCoverage,
                double windDetailOffsetX,
                double windDetailOffsetZ
        ) {
            this(
                    active,
                    visibleTiles,
                    vertices,
                    averageCoverage,
                    windDetailOffsetX,
                    windDetailOffsetZ,
                    active ? "compatibility" : "inactive",
                    active ? 1 : 0,
                    "Clear"
            );
        }

        public Diagnostics {
            mode = mode == null || mode.isBlank() ? "unknown" : mode;
            layers = Math.max(0, layers);
            cloudType = cloudType == null || cloudType.isBlank() ? "Unknown" : cloudType;
        }
    }
}
