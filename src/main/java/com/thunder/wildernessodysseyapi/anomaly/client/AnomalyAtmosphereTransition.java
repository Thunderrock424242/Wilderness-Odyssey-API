package com.thunder.wildernessodysseyapi.anomaly.client;

import java.util.Objects;

/**
 * Bounded local interpolation with no world access. Age is measured in unpaused
 * client ticks; the server refreshes every 200 ticks, leaving a 100-tick margin.
 */
final class AnomalyAtmosphereTransition {
    private static final int STALE_TICKS = 300;
    private String dimension;
    private int zone = -1;
    private int age;
    private float targetIntensity;
    private float targetRiftIntensity;
    private float intensity;
    private float riftIntensity;

    boolean accept(String currentDimension, String packetDimension, int zone, float intensity, float riftIntensity) {
        if (currentDimension == null || !Objects.equals(currentDimension, packetDimension)) {
            return false;
        }
        if (!Objects.equals(dimension, currentDimension)) {
            clear();
            dimension = currentDimension;
        }
        this.zone = zone >= -1 && zone <= 4 ? zone : -1;
        targetIntensity = AnomalyEffectPolicy.clampUnit(intensity);
        targetRiftIntensity = AnomalyEffectPolicy.clampUnit(riftIntensity);
        age = 0;
        return true;
    }

    void tick(String currentDimension) {
        if (currentDimension == null || !Objects.equals(dimension, currentDimension)) {
            clear();
            return;
        }
        age = Math.min(STALE_TICKS + 1, age + 1);
        if (age > STALE_TICKS) {
            targetIntensity = 0.0F;
            targetRiftIntensity = 0.0F;
            zone = -1;
        }
        intensity = approach(intensity, targetIntensity);
        riftIntensity = approach(riftIntensity, targetRiftIntensity);
    }

    int zone() {
        return zone;
    }

    float intensity() {
        return intensity;
    }

    float riftIntensity() {
        return riftIntensity;
    }

    void clear() {
        dimension = null;
        zone = -1;
        age = 0;
        targetIntensity = 0.0F;
        targetRiftIntensity = 0.0F;
        intensity = 0.0F;
        riftIntensity = 0.0F;
    }

    private static float approach(float current, float target) {
        float result = current + (target - current) * 0.1F;
        return Math.abs(result - target) < 0.0001F ? target : result;
    }
}
