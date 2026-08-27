package com.thunder.wildernessodysseyapi.watersystem.water.wave;

import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import com.thunder.wildernessodysseyapi.watersystem.ocean.ClientOceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.water.environment.WaterEnvironmentState;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterRenderingConfig;
import net.minecraft.client.Minecraft;

/**
 * Provides the client wave clock and samples the shared Gerstner profiles.
 *
 * <p>The clock follows client world time rather than wall time, so pausing the
 * game does not make waves jump and every caller observes the same phase. The
 * complete {@link WaveSurfaceSample} is exposed so geometry, normals, boats,
 * and future shader uniforms can all use one model.</p>
 */
public final class GerstnerWaveAnimator {

    private static final float TICKS_PER_SECOND = 20.0f;
    // Keep camera immersion, boat response, and the live ocean mesh on the
    // same deliberately reduced visual tide amplitude.
    private static final float VISUAL_TIDE_SCALE = 0.18f;

    private static volatile double currentTime = 0.0;
    private static float clientTideOffset = 0.0f;

    private GerstnerWaveAnimator() {
    }

    /**
     * Synchronizes wave and tide animation state to the active client world.
     * This must run on the client tick, not a chunk-tessellation worker.
     */
    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            currentTime = mc.level.getGameTime() / (double) TICKS_PER_SECOND;
            clientTideOffset = TideSystem.getTideOffset(mc.level);
        } else {
            currentTime = 0.0;
            clientTideOffset = 0.0f;
        }
    }

    /**
     * Samples displacement, normal, and orbital velocity at a water surface.
     *
     * @param worldX world X coordinate in blocks
     * @param worldZ world Z coordinate in blocks
     * @param type classified water-body type
     * @return the complete surface state
     */
    public static WaveSurfaceSample getSurfaceSampleAt(
            float worldX,
            float worldZ,
            WaterBodyClassifier.WaterType type
    ) {
        return getSurfaceSampleAt((double) worldX, worldZ, type, 0.0f, 0.0f);
    }

    /**
     * Samples the client surface with double world coordinates and optional
     * local flow alignment for directional river components.
     */
    public static WaveSurfaceSample getSurfaceSampleAt(
            double worldX,
            double worldZ,
            WaterBodyClassifier.WaterType type,
            float flowDirectionX,
            float flowDirectionZ
    ) {
        if (!WaterRenderingConfig.ENABLE_GERSTNER_WAVES.get()) {
            return WaveSurfaceSample.flat();
        }

        GerstnerWaveProfile profile = profileFor(type);
        Minecraft minecraft = Minecraft.getInstance();
        OceanSeaState.Sample seaState = minecraft.level == null
                ? OceanSeaState.CALM
                : ClientOceanSeaState.sampleAt(minecraft.level, worldX, worldZ);
        float rain = minecraft.level == null ? 0.0f : minecraft.level.getRainLevel(0.0f);
        WaveSpectrumState spectrum = WaterEnvironmentState.waveSpectrumFor(
                type,
                seaState,
                rain,
                fallbackFetch(type)
        );
        WaveSurfaceSample sample = profile.sampleAt(
                worldX,
                worldZ,
                currentTime,
                WaterRenderingConfig.waveTrainLimit(type),
                spectrum,
                type == WaterBodyClassifier.WaterType.RIVER ? flowDirectionX : 0.0f,
                type == WaterBodyClassifier.WaterType.RIVER ? flowDirectionZ : 0.0f
        );
        if (WaterBodyClassifier.isOceanic(type)) {
            return sample.withHeightOffset(clientTideOffset * VISUAL_TIDE_SCALE);
        }
        return sample;
    }

    /**
     * Samples the deterministic wave phase used by Minecraft's baked terrain
     * mesh. Chunk sections compile at different times, so using live animation
     * time here would leave permanent phase seams between neighboring sections.
     *
     * @param worldX world X coordinate in blocks
     * @param worldZ world Z coordinate in blocks
     * @param type classified water-body type
     * @return a time-independent terrain surface sample
     */
    public static WaveSurfaceSample getTerrainSurfaceSampleAt(
            float worldX,
            float worldZ,
            WaterBodyClassifier.WaterType type
    ) {
        if (!WaterRenderingConfig.ENABLE_GERSTNER_WAVES.get()) {
            return WaveSurfaceSample.flat();
        }

        return profileFor(type).sampleAt(
                worldX,
                worldZ,
                0.0f,
                WaterRenderingConfig.waveTrainLimit(type)
        );
    }

    /**
     * Returns the vertical surface displacement at a world position.
     *
     * @param worldX world X coordinate in blocks
     * @param worldZ world Z coordinate in blocks
     * @param type classified water-body type
     * @return vertical displacement in blocks
     */
    public static float getHeightAt(float worldX, float worldZ,
                                    WaterBodyClassifier.WaterType type) {
        return getSurfaceSampleAt(worldX, worldZ, type).height();
    }

    /**
     * Returns horizontal orbital velocity scaled for entity movement.
     *
     * @param worldX world X coordinate in blocks
     * @param worldZ world Z coordinate in blocks
     * @param type classified water-body type
     * @return X/Z velocity contribution
     */
    public static float[] getPushAt(float worldX, float worldZ,
                                    WaterBodyClassifier.WaterType type) {
        if (!WaterRenderingConfig.ENABLE_GERSTNER_WAVES.get()) {
            return new float[]{0.0f, 0.0f};
        }

        WaveSurfaceSample sample = getSurfaceSampleAt(worldX, worldZ, type);
        float strength = profileFor(type).entityPushStrength;
        return new float[]{
                sample.velocityX() * strength,
                sample.velocityZ() * strength
        };
    }

    /** Returns the current world-synchronized wave time in seconds. */
    public static float getTime() {
        return (float) currentTime;
    }

    /** Returns precise world-synchronized wave time for CPU surface sampling. */
    public static double getTimeSeconds() {
        return currentTime;
    }

    /** Returns the current client-side tide offset in blocks. */
    public static float getClientTideOffset() {
        return clientTideOffset;
    }

    private static GerstnerWaveProfile profileFor(WaterBodyClassifier.WaterType type) {
        return switch (type) {
            case OCEAN -> GerstnerWaveProfile.OCEAN;
            case RIVER -> GerstnerWaveProfile.RIVER;
            case POND -> GerstnerWaveProfile.POND;
            case COAST -> GerstnerWaveProfile.COAST;
            case LAKE -> GerstnerWaveProfile.LAKE;
        };
    }

    /**
     * Returns a constant-time exposure proxy for presentation-only sampling.
     * Authoritative surface queries use generated depth and volume metadata;
     * this path may also be called while terrain meshes are being assembled,
     * so it must never trigger an authority query or neighboring chunk load.
     */
    private static float fallbackFetch(WaterBodyClassifier.WaterType type) {
        return switch (type) {
            case OCEAN -> 1.0f;
            case COAST -> 0.76f;
            case RIVER -> 0.28f;
            case LAKE -> 0.52f;
            case POND -> 0.08f;
        };
    }
}
