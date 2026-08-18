package com.thunder.wildernessodysseyapi.ecosystem.simulation;

import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeSimulationLod;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Immutable regional ecosystem view for diagnostics and future renderers or migration systems.
 */
public record EcosystemRegionSnapshot(
        EcosystemCellKey key,
        WildlifeSimulationLod simulationLevel,
        Map<ResourceLocation, Integer> speciesPopulations,
        int groupCount,
        EcosystemCellKey migrationTarget,
        double foodAvailability,
        double waterAvailability,
        double foodPressure,
        double disturbance,
        double weatherImpact,
        long lastUpdatedTick,
        long lastObservedTick
) {
    public EcosystemRegionSnapshot {
        speciesPopulations = Map.copyOf(speciesPopulations);
        groupCount = Math.max(0, groupCount);
        foodAvailability = unit(foodAvailability);
        waterAvailability = unit(waterAvailability);
        foodPressure = unit(foodPressure);
        disturbance = unit(disturbance);
        weatherImpact = unit(weatherImpact);
        lastUpdatedTick = Math.max(0L, lastUpdatedTick);
        lastObservedTick = Math.max(0L, lastObservedTick);
    }

    /** Returns the total abstract wildlife represented by this cell. */
    public int totalPopulation() {
        return speciesPopulations.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static double unit(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
