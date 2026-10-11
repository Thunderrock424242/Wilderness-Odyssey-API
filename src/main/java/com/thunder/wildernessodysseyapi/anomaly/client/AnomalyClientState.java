package com.thunder.wildernessodysseyapi.anomaly.client;

import com.thunder.wildernessodysseyapi.anomaly.network.AnomalyAtmospherePayload;
import com.thunder.wildernessodysseyapi.worldgen.anomaly.AnomalyZone;
import net.minecraft.world.level.Level;

/**
 * Client-thread presentation state. Server packets supply local conclusions;
 * developer previews change only this client's presentation and clear on travel.
 */
public final class AnomalyClientState {
    private static final AnomalyAtmosphereTransition TRANSITION = new AnomalyAtmosphereTransition();
    private static Level activeLevel;
    private static Float previewIntensity;
    private static Integer previewZone;
    private static Boolean previewPost;
    private static long visualTicks;

    private AnomalyClientState() {
    }

    /** Called by the clientbound handler after scheduling onto the client thread. */
    public static void accept(Level level, AnomalyAtmospherePayload payload) {
        if (level == null || !level.isClientSide || payload == null
                || !level.dimension().location().equals(payload.dimension())) {
            return;
        }
        bindLevel(level);
        TRANSITION.accept(level.dimension().location().toString(), payload.dimension().toString(),
                payload.zone(), payload.intensity() / 100.0F, payload.riftIntensity() / 100.0F);
    }

    /** Advances once per unpaused client tick; replacing a level also handles same-dimension respawns. */
    static void tick(Level level) {
        bindLevel(level);
        if (level != null) {
            TRANSITION.tick(level.dimension().location().toString());
            visualTicks++;
        }
    }

    public static boolean controls(Level level) {
        return level != null && level == activeLevel;
    }

    public static int zone() {
        return previewZone != null ? previewZone : TRANSITION.zone();
    }

    public static float intensity() {
        if (previewIntensity != null) {
            return previewIntensity;
        }
        if (previewZone != null) {
            return previewZone < 0 ? 0.0F : (float) AnomalyZone.values()[previewZone].intensity();
        }
        return TRANSITION.intensity();
    }

    public static float riftIntensity() {
        // Reuse the rift owner's bounded, distance-based packet presentation.
        // A second block-entity search would disagree at chunk boundaries.
        return Math.max(TRANSITION.riftIntensity(), AnomalyEffectPolicy.clampUnit(
                com.thunder.wildernessodysseyapi.temporalrift.client.hybrid.RiftClientEffects.currentIntensity(activeLevel)));
    }

    static long visualTicks() {
        return visualTicks;
    }

    static boolean postEnabled(boolean configured) {
        return previewPost != null ? previewPost : configured;
    }

    static void previewIntensity(float intensity) {
        previewIntensity = AnomalyEffectPolicy.clampUnit(intensity);
    }

    static void previewZone(int zone) {
        previewZone = zone >= -1 && zone <= 4 ? zone : -1;
        previewIntensity = null;
    }

    static void previewPost(boolean enabled) {
        previewPost = enabled;
    }

    static void resetPreview() {
        previewIntensity = null;
        previewZone = null;
        previewPost = null;
    }

    /** Clears packet and preview state before another world or server can render. */
    public static void clear() {
        activeLevel = null;
        TRANSITION.clear();
        resetPreview();
        visualTicks = 0L;
    }

    static void clearLevel(Level level) {
        if (level == activeLevel) {
            clear();
        }
    }

    private static void bindLevel(Level level) {
        if (activeLevel != level) {
            clear();
            activeLevel = level;
        }
    }
}
