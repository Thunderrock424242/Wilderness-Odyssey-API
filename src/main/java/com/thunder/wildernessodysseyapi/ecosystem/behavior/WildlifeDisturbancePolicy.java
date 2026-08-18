package com.thunder.wildernessodysseyapi.ecosystem.behavior;

import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;

/**
 * Pure wildlife-response policy for normalized environmental disturbance.
 *
 * <p>The policy never returns a zero spawn chance. It only reduces natural
 * wildlife pressure and reserves active retreat for the configurable strong
 * band, leaving vanilla AI and all non-natural spawn sources intact.</p>
 */
public final class WildlifeDisturbancePolicy {

    private WildlifeDisturbancePolicy() {
    }

    /** Returns the configured probability that one otherwise-valid natural spawn remains eligible. */
    public static double spawnChance(double disturbance) {
        return spawnChance(disturbance, Settings.fromConfig());
    }

    /** Pure overload used by deterministic tests and external simulations. */
    public static double spawnChance(double disturbance, Settings settings) {
        double normalized = unit(disturbance);
        if (normalized < settings.mildThreshold()) {
            return 1.0;
        }
        if (normalized < settings.reducedThreshold()) {
            return settings.mildSpawnMultiplier();
        }
        if (normalized < settings.strongThreshold()) {
            return settings.reducedSpawnMultiplier();
        }
        return settings.strongSpawnMultiplier();
    }

    /** Returns whether wild animals should actively avoid this amount of disturbance. */
    public static boolean stronglyAvoided(double disturbance) {
        return stronglyAvoided(disturbance, Settings.fromConfig());
    }

    /** Pure overload used by tests and future destination-ranking systems. */
    public static boolean stronglyAvoided(double disturbance, Settings settings) {
        return unit(disturbance) >= settings.strongThreshold();
    }

    /** Immutable validated response bands and non-zero spawn multipliers. */
    public record Settings(
            double mildThreshold,
            double reducedThreshold,
            double strongThreshold,
            double mildSpawnMultiplier,
            double reducedSpawnMultiplier,
            double strongSpawnMultiplier
    ) {
        public Settings {
            mildThreshold = unit(mildThreshold);
            reducedThreshold = Math.max(mildThreshold, unit(reducedThreshold));
            strongThreshold = Math.max(reducedThreshold, unit(strongThreshold));
            mildSpawnMultiplier = nonZeroUnit(mildSpawnMultiplier);
            reducedSpawnMultiplier = Math.min(mildSpawnMultiplier, nonZeroUnit(reducedSpawnMultiplier));
            strongSpawnMultiplier = Math.min(reducedSpawnMultiplier, nonZeroUnit(strongSpawnMultiplier));
        }

        /** Builds one validated policy snapshot from the live server config. */
        public static Settings fromConfig() {
            return new Settings(
                    EcosystemConfig.WILDLIFE_MILD_DISTURBANCE.get(),
                    EcosystemConfig.WILDLIFE_REDUCED_DISTURBANCE.get(),
                    EcosystemConfig.WILDLIFE_STRONG_AVOIDANCE_DISTURBANCE.get(),
                    EcosystemConfig.WILDLIFE_MILD_SPAWN_MULTIPLIER.get(),
                    EcosystemConfig.WILDLIFE_REDUCED_SPAWN_MULTIPLIER.get(),
                    EcosystemConfig.WILDLIFE_STRONG_SPAWN_MULTIPLIER.get()
            );
        }
    }

    private static double nonZeroUnit(double value) {
        return Math.max(0.000_001, unit(value));
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }
}
