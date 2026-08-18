package com.thunder.wildernessodysseyapi.ecosystem.simulation;

import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;

/**
 * Validated snapshot of simulation-zone scheduling settings.
 *
 * <p>Radii are normalized into strictly increasing bands even if a hand-edited
 * server config supplies them out of order. This keeps classification total and
 * prevents an invalid reload from mutating entity state unpredictably.</p>
 */
public record EcosystemSimulationSettings(
        boolean enabled,
        int cellSize,
        int activeRadius,
        int nearRadius,
        int distantRadius,
        int regionalUpdateInterval,
        int maxRegionUpdatesPerTick,
        int entityTransitionRate
) {
    public static final int DEFAULT_CELL_SIZE = 64;
    public static final EcosystemSimulationSettings DEFAULT = new EcosystemSimulationSettings(
            true, DEFAULT_CELL_SIZE, 96, 224, 512, 40, 16, 2
    );

    public EcosystemSimulationSettings {
        cellSize = clamp(cellSize, 16, 256);
        activeRadius = clamp(activeRadius, 16, 4_096);
        nearRadius = clamp(Math.max(nearRadius, activeRadius + cellSize), 32, 8_192);
        distantRadius = clamp(Math.max(distantRadius, nearRadius + cellSize), 64, 16_384);
        regionalUpdateInterval = clamp(regionalUpdateInterval, 1, 1_200);
        maxRegionUpdatesPerTick = clamp(maxRegionUpdatesPerTick, 1, 4_096);
        entityTransitionRate = clamp(entityTransitionRate, 1, 256);
    }

    /** Captures config values once for a manager pass instead of repeatedly querying suppliers. */
    public static EcosystemSimulationSettings fromConfig() {
        return new EcosystemSimulationSettings(
                EcosystemConfig.ENABLED.get() && EcosystemConfig.SIMULATION_ZONES_ENABLED.get(),
                DEFAULT_CELL_SIZE,
                EcosystemConfig.FAR_ANIMAL_DISTANCE.get(),
                EcosystemConfig.NEAR_ANIMAL_DISTANCE.get(),
                EcosystemConfig.DISTANT_ANIMAL_DISTANCE.get(),
                EcosystemConfig.REGIONAL_UPDATE_INTERVAL.get(),
                EcosystemConfig.MAX_REGION_UPDATES_PER_TICK.get(),
                EcosystemConfig.ENTITY_TRANSITION_RATE.get()
        );
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
