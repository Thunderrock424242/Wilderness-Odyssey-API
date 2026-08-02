package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.ocean.ClientOceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterChunkSnapshot;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterSnapshotStore;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Resolves camera immersion from immutable snapshots and the GPU surface equation.
 *
 * <p>No block, heightmap, or mutable authority scan occurs here. The same base
 * surface, body blend, tide, synchronized sea state, and shader-compatible
 * Gerstner calculation drive both visible geometry and underwater transitions.</p>
 */
public final class ClientWaterImmersion {

    private static final float VISUAL_TIDE_SCALE = 0.18f;
    private static final float EXIT_HYSTERESIS = 0.08f;

    private static ClientLevel cachedLevel;
    private static long cachedGameTime = Long.MIN_VALUE;
    private static float cachedPartialTick = Float.NaN;
    private static Vec3 cachedCameraPosition = new Vec3(Double.NaN, Double.NaN, Double.NaN);
    private static ImmersionState cachedState = ImmersionState.DRY;

    private ClientWaterImmersion() {
    }

    /** Returns the camera's current bounded water-immersion state. */
    public static ImmersionState sample(Camera camera, float partialTick) {
        if (camera == null
                || camera.getEntity() == null
                || !(camera.getEntity().level() instanceof ClientLevel level)
                || !WildernessWaterRules.isEnabled(level)) {
            return ImmersionState.DRY;
        }
        Vec3 cameraPosition = camera.getPosition();
        if (isCached(level, cameraPosition, partialTick)) {
            return cachedState;
        }

        ImmersionState previous = cachedLevel == level ? cachedState : ImmersionState.DRY;
        cachedLevel = level;
        cachedGameTime = level.getGameTime();
        cachedPartialTick = partialTick;
        cachedCameraPosition = cameraPosition;
        cachedState = resolve(level, cameraPosition, partialTick, previous);
        return cachedState;
    }

    /** Clears per-frame state when the client unloads a dimension. */
    public static void clear(ClientLevel level) {
        if (cachedLevel == level) {
            cachedLevel = null;
            cachedGameTime = Long.MIN_VALUE;
            cachedPartialTick = Float.NaN;
            cachedCameraPosition = new Vec3(Double.NaN, Double.NaN, Double.NaN);
            cachedState = ImmersionState.DRY;
        }
    }

    private static ImmersionState resolve(
            ClientLevel level,
            Vec3 cameraPosition,
            float partialTick,
            ImmersionState previous
    ) {
        int blockX = (int) Math.floor(cameraPosition.x);
        int blockZ = (int) Math.floor(cameraPosition.z);
        ClientWaterChunkSnapshot snapshot = ClientWaterSnapshotStore.getAtBlock(level, blockX, blockZ);
        if (snapshot == null) {
            return resolveMobileWater(level, cameraPosition);
        }
        ClientWaterChunkSnapshot.Column column = snapshot.column(blockX & 15, blockZ & 15);
        if (!column.wet()) {
            return resolveMobileWater(level, cameraPosition);
        }

        OceanSeaState.Sample sea = ClientOceanSeaState.current(level);
        float oceanWeight = column.oceanWeight() / 255.0f;
        float riverWeight = column.riverWeight() / 255.0f;
        float lakeWeight = column.lakeWeight() / 255.0f;
        float surfaceY = visibleSurfaceHeight(
                level,
                column,
                cameraPosition.x,
                cameraPosition.z,
                partialTick
        );
        float depthBelowSurface = surfaceY - (float) cameraPosition.y;
        boolean withinColumn = cameraPosition.y >= column.floorY() + 0.92f
                && cameraPosition.y <= surfaceY + (previous.waterColumnPresent() ? EXIT_HYSTERESIS : 0.0f);
        if (!withinColumn) {
            return resolveMobileWater(level, cameraPosition);
        }

        float columnDepth = Math.max(0.05f, surfaceY - (column.floorY() + 1.0f));
        float daylight = LightTexture.getBrightness(
                level.dimensionType(),
                level.getMaxLocalRawBrightness(new BlockPos(blockX, column.surfaceBlockY(), blockZ))
        );
        float[] tint = bodyTint(oceanWeight, riverWeight, lakeWeight, column.waterTint());
        UnderwaterOpticsModel.OpticalProperties optics = UnderwaterOpticsModel.evaluate(
                depthBelowSurface,
                columnDepth,
                Math.min(1.0f, column.currentSpeed() / WaterSurfaceVertexData.MAX_RENDER_CURRENT),
                daylight,
                tint[0],
                tint[1],
                tint[2],
                sea.strength() * oceanWeight,
                WaterRenderingConfig.UNDERWATER_VISIBILITY_BLOCKS.get().floatValue(),
                WaterRenderingConfig.UNDERWATER_TURBIDITY_STRENGTH.get().floatValue()
        );
        return new ImmersionState(true, surfaceY, depthBelowSurface,
                sea.strength() * oceanWeight, optics);
    }

    // Snapshot mesh vertices average the four touching custom columns. Sampling
    // the same triangle interpolation here prevents camera entry from crossing
    // a full-amplitude CPU crest after the GPU has tapered that shoreline flat.
    /**
     * Samples the snapshot mesh's interpolated loaded-surface continuity.
     *
     * <p>Client-only consumers such as boat presentation use this to follow the
     * same shoreline taper as the active GPU mesh. It never changes authority
     * state or server physics.</p>
     */
    public static float sampleSurfaceContinuity(
            ClientLevel level,
            double worldX,
            double worldZ
    ) {
        int minimumX = (int) Math.floor(worldX);
        int minimumZ = (int) Math.floor(worldZ);
        float localX = (float) (worldX - minimumX);
        float localZ = (float) (worldZ - minimumZ);
        return interpolateQuadContinuity(
                vertexContinuity(level, minimumX, minimumZ),
                vertexContinuity(level, minimumX, minimumZ + 1),
                vertexContinuity(level, minimumX + 1, minimumZ + 1),
                vertexContinuity(level, minimumX + 1, minimumZ),
                localX,
                localZ
        );
    }

    /**
     * Samples the same body-blended, shoreline-tapered surface used by immersion.
     *
     * <p>Ambient spray uses this path so tides and transient wakes cannot leave
     * particles floating above or clipping below the built-in mesh. An external
     * shader pack has no inspectable displacement equation, so its safe fallback
     * is the synchronized flat fluid height.</p>
     */
    static float visibleSurfaceHeight(
            ClientLevel level,
            ClientWaterChunkSnapshot.Column column,
            double worldX,
            double worldZ,
            float partialTick
    ) {
        float oceanWeight = column.oceanWeight() / 255.0f;
        float riverWeight = column.riverWeight() / 255.0f;
        float lakeWeight = column.lakeWeight() / 255.0f;
        double timeSeconds = (level.getGameTime() + (double) partialTick) / 20.0;
        boolean customSurface = WaterShaders.shouldUseCoreShader()
                && WaterChunkMeshCache.usesCustomSurface(column);
        boolean waveSurface = customSurface && WaterRenderingConfig.ENABLE_GERSTNER_WAVES.get();
        float transientHeight = customSurface
                ? WaterSurfaceDisplacement.sampleHeight(
                        level,
                        worldX,
                        worldZ,
                        level.getGameTime() + partialTick
                )
                : 0.0f;
        float surfaceContinuity = customSurface
                ? sampleSurfaceContinuity(level, worldX, worldZ)
                : 1.0f;
        return WaterSurfaceEquation.snapshotSurfaceHeight(
                column.baseSurfaceY(),
                worldX,
                worldZ,
                timeSeconds,
                ClientOceanSeaState.current(level).spectrum(),
                waveSurface
                        ? WaterRenderingConfig.waveTrainLimit(WaterBodyClassifier.WaterType.OCEAN) : 0,
                waveSurface
                        ? WaterRenderingConfig.waveTrainLimit(WaterBodyClassifier.WaterType.RIVER) : 0,
                waveSurface
                        ? WaterRenderingConfig.waveTrainLimit(WaterBodyClassifier.WaterType.POND) : 0,
                oceanWeight,
                riverWeight,
                lakeWeight,
                column.velocityX(),
                column.velocityZ(),
                surfaceContinuity,
                customSurface ? TideSystem.getTideOffset(level) * VISUAL_TIDE_SCALE : 0.0f,
                transientHeight
        );
    }

    private static float vertexContinuity(ClientLevel level, int vertexX, int vertexZ) {
        int count = 0;
        for (int offsetZ = -1; offsetZ <= 0; offsetZ++) {
            for (int offsetX = -1; offsetX <= 0; offsetX++) {
                int columnX = vertexX + offsetX;
                int columnZ = vertexZ + offsetZ;
                ClientWaterChunkSnapshot snapshot = ClientWaterSnapshotStore.getAtBlock(
                        level,
                        columnX,
                        columnZ
                );
                if (snapshot == null) {
                    continue;
                }
                ClientWaterChunkSnapshot.Column column = snapshot.column(columnX & 15, columnZ & 15);
                if (column.wet() && WaterChunkMeshCache.usesCustomSurface(column)) {
                    count++;
                }
            }
        }
        return Math.max(0.18f, count * 0.25f);
    }

    static float interpolateQuadContinuity(
            float northWest,
            float southWest,
            float southEast,
            float northEast,
            float x,
            float z
    ) {
        float boundedX = clamp(x, 0.0f, 1.0f);
        float boundedZ = clamp(z, 0.0f, 1.0f);
        // QUADS uses the north-west to south-east diagonal.
        if (boundedX <= boundedZ) {
            return northWest * (1.0f - boundedZ)
                    + southWest * (boundedZ - boundedX)
                    + southEast * boundedX;
        }
        return northWest * (1.0f - boundedX)
                + southEast * boundedZ
                + northEast * (boundedX - boundedZ);
    }

    private static ImmersionState resolveMobileWater(ClientLevel level, Vec3 cameraPosition) {
        SPHSimulationManager.MobileWaterSample mobile = SPHSimulationManager.get().sampleAt(
                level, cameraPosition.x, cameraPosition.y, cameraPosition.z);
        if (!mobile.wet()) {
            return ImmersionState.DRY;
        }
        float disturbance = clamp((float) Math.sqrt(
                mobile.velocityX() * mobile.velocityX()
                        + mobile.velocityY() * mobile.velocityY()
                        + mobile.velocityZ() * mobile.velocityZ()) / 1.5f, 0.0f, 1.0f);
        BlockPos cameraPos = BlockPos.containing(cameraPosition);
        float daylight = LightTexture.getBrightness(
                level.dimensionType(), level.getMaxLocalRawBrightness(cameraPos));
        UnderwaterOpticsModel.OpticalProperties optics = UnderwaterOpticsModel.evaluate(
                0.35f, 1.0f, disturbance, daylight,
                0.03f, 0.34f, 0.62f, 0.0f,
                WaterRenderingConfig.UNDERWATER_VISIBILITY_BLOCKS.get().floatValue(),
                WaterRenderingConfig.UNDERWATER_TURBIDITY_STRENGTH.get().floatValue()
        );
        return new ImmersionState(true, (float) cameraPosition.y + 0.35f,
                0.35f, 0.0f, optics);
    }

    static float[] bodyTint(float ocean, float river, float lake, int waterTint) {
        float bodyWeight = ocean + river + lake;
        float normalizedOcean = bodyWeight > 0.001f ? ocean / bodyWeight : 0.0f;
        float normalizedRiver = bodyWeight > 0.001f ? river / bodyWeight : 0.0f;
        float normalizedLake = bodyWeight > 0.001f ? lake / bodyWeight : 1.0f;
        float bodyRed = 0.018f * normalizedOcean
                + 0.035f * normalizedRiver
                + 0.045f * normalizedLake;
        float bodyGreen = 0.25f * normalizedOcean
                + 0.35f * normalizedRiver
                + 0.38f * normalizedLake;
        float bodyBlue = 0.62f * normalizedOcean
                + 0.58f * normalizedRiver
                + 0.52f * normalizedLake;
        float biomeRed = ((waterTint >>> 16) & 0xFF) / 255.0f;
        float biomeGreen = ((waterTint >>> 8) & 0xFF) / 255.0f;
        float biomeBlue = (waterTint & 0xFF) / 255.0f;

        // Match the visible mesh's optical mix: body identity remains dominant
        // while the synchronized biome tint differentiates local water color.
        return new float[]{
                bodyRed * 0.72f + biomeRed * 0.28f,
                bodyGreen * 0.72f + biomeGreen * 0.28f,
                bodyBlue * 0.72f + biomeBlue * 0.28f
        };
    }

    private static boolean isCached(ClientLevel level, Vec3 cameraPosition, float partialTick) {
        return cachedLevel == level
                && cachedGameTime == level.getGameTime()
                && Math.abs(cachedPartialTick - partialTick) < 1.0e-4f
                && cachedCameraPosition.distanceToSqr(cameraPosition) < 1.0e-8;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Immutable client sample shared by fog and overlay hooks in one frame. */
    public record ImmersionState(
            boolean waterColumnPresent,
            float surfaceY,
            float depthBelowSurface,
            float seaState,
            UnderwaterOpticsModel.OpticalProperties optics
    ) {
        public static final ImmersionState DRY = new ImmersionState(
                false,
                Float.NEGATIVE_INFINITY,
                Float.NEGATIVE_INFINITY,
                0.0f,
                new UnderwaterOpticsModel.OpticalProperties(0.0f, 0.0f, 0.0f,
                        1.0f, 128.0f, 0.0f, 0.0f, 0.0f)
        );

        /** Returns whether any smooth underwater transition should be active. */
        public boolean isVisuallySubmerged() {
            return waterColumnPresent && optics.immersionBlend() > 0.001f;
        }
    }
}
