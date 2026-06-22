package com.thunder.wildernessodysseyapi.watersystem.water.wave;

import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
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
    private static final float VISUAL_TIDE_SCALE = 0.01f;

    private static volatile float currentTime = 0.0f;
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
            currentTime = mc.level.getGameTime() / TICKS_PER_SECOND;
            clientTideOffset = TideSystem.getTideOffset(mc.level);
        } else {
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
        if (!WaterRenderingConfig.ENABLE_GERSTNER_WAVES.get()) {
            return WaveSurfaceSample.flat();
        }

        GerstnerWaveProfile profile = profileFor(type);
        WaveSurfaceSample sample = profile.sampleAt(
                worldX,
                worldZ,
                currentTime,
                WaterRenderingConfig.waveTrainLimit(type)
        );
        if (type == WaterBodyClassifier.WaterType.OCEAN) {
            return sample.withHeightOffset(clientTideOffset * VISUAL_TIDE_SCALE);
        }
        return sample;
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
        };
    }
}
