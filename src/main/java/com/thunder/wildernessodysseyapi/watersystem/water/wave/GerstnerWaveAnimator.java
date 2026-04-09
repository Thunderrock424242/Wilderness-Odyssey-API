package com.thunder.wildernessodysseyapi.watersystem.water.wave;

import com.thunder.wilderness.water.tide.TideSystem;
import net.minecraft.client.Minecraft;

/**
 * GerstnerWaveAnimator
 *
 * Client-side wave height evaluator using Gerstner wave profiles.
 * Replaces the simple sine-based WaveAnimator from the first system
 * for water blocks that have been classified.
 *
 * Incorporates:
 *   - Per-water-body Gerstner profile (ocean/river/pond)
 *   - Tide offset from TideSystem (client approximation)
 *   - Real-time clock for smooth animation
 */
public class GerstnerWaveAnimator {

    private static float currentTime  = 0f;
    private static long  lastFrameMs  = -1L;

    // Client-side tide approximation (updated each tick from server data via packet,
    // or estimated from client day time as fallback)
    private static float clientTideOffset = 0f;

    public static void update() {
        long now = System.currentTimeMillis();
        if (lastFrameMs < 0) { lastFrameMs = now; return; }
        float dt = (now - lastFrameMs) / 1000f;
        currentTime  += dt;
        lastFrameMs   = now;

        // Update tide offset from client world
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            clientTideOffset = TideSystem.getTideOffset(mc.level);
        }
    }

    /**
     * Get the Y displacement (wave height + tide) for a water surface vertex.
     *
     * @param worldX  world X coordinate of the vertex
     * @param worldZ  world Z coordinate of the vertex
     * @param type    water body type at this position
     * @return        total Y offset to apply (blocks)
     */
    public static float getHeightAt(float worldX, float worldZ,
                                     WaterBodyClassifier.WaterType type) {
        GerstnerWaveProfile profile = profileFor(type);
        float waveHeight  = profile.getHeightAt(worldX, worldZ, currentTime);
        float tideContrib = (type == WaterBodyClassifier.WaterType.OCEAN)
                            ? clientTideOffset * 0.025f  // visual ripple only — real tide is server-side
                            : 0f;
        return waveHeight + tideContrib;
    }

    /**
     * Get the horizontal push vector at this position for this water type.
     * Used by the entity/boat push system each tick.
     */
    public static float[] getPushAt(float worldX, float worldZ,
                                     WaterBodyClassifier.WaterType type) {
        return profileFor(type).getPushAt(worldX, worldZ, currentTime);
    }

    public static float getTime()            { return currentTime; }
    public static float getClientTideOffset(){ return clientTideOffset; }

    private static GerstnerWaveProfile profileFor(WaterBodyClassifier.WaterType type) {
        return switch (type) {
            case OCEAN -> GerstnerWaveProfile.OCEAN;
            case RIVER -> GerstnerWaveProfile.RIVER;
            case POND  -> GerstnerWaveProfile.POND;
        };
    }
}
