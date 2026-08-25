package com.thunder.wildernessodysseyapi.simulation.integration;

import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeGroup;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifePopulationUpdate;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.AbstractEcosystemModel;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemRegionSnapshot;
import com.thunder.wildernessodysseyapi.environment.api.RegionalEnvironmentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure regional population calculation used by the Data Engine worker path. */
public final class PopulationEcologyModel {
    private PopulationEcologyModel() {
    }

    /**
     * Composes carrying pressure from immutable ecosystem and environment snapshots.
     */
    public static AbstractEcosystemModel.Environment conditions(
            EcosystemRegionSnapshot ecosystem,
            RegionalEnvironmentSnapshot environment,
            double currentDisturbance,
            int regionalCarryingCapacity
    ) {
        Objects.requireNonNull(ecosystem, "Ecosystem snapshot is required");
        Objects.requireNonNull(environment, "Environment snapshot is required");
        double habitat = environment.influence().habitatProductivity();
        double water = (ecosystem.waterAvailability() + environment.influence().waterAvailability()) * 0.5;
        double resourceSupport = unit(habitat * 0.55 + water * 0.45);
        int effectiveCapacity = Math.max(
                1,
                (int) Math.round(Math.max(1, regionalCarryingCapacity) * (0.25 + resourceSupport * 0.75))
        );
        double densityPressure = unit(ecosystem.totalPopulation() / (double) effectiveCapacity);
        double food = Math.max(
                0.05,
                ((ecosystem.foodAvailability() + habitat) * 0.5) * (1.0 - densityPressure * 0.70)
        );
        return new AbstractEcosystemModel.Environment(
                food,
                water,
                densityPressure,
                currentDisturbance,
                environment.influence().overallHazard()
        );
    }

    /** Advances only groups whose persisted population clock is due. */
    public static Calculation calculate(
            List<DistantWildlifeGroup> groups,
            AbstractEcosystemModel.Environment observedEnvironment,
            long gameTime,
            long minimumElapsedTicks
    ) {
        Objects.requireNonNull(groups, "Distant wildlife groups are required");
        Objects.requireNonNull(observedEnvironment, "Observed population environment is required");
        long safeTime = Math.max(0L, gameTime);
        long minimumElapsed = Math.max(1L, minimumElapsedTicks);
        List<DistantWildlifePopulationUpdate> updates = new ArrayList<>();
        int sourcePopulation = 0;
        int targetPopulation = 0;
        for (DistantWildlifeGroup group : List.copyOf(groups)) {
            long elapsed = safeTime >= group.populationReferenceGameTime()
                    ? safeTime - group.populationReferenceGameTime()
                    : Long.MAX_VALUE;
            if (elapsed < minimumElapsed) {
                continue;
            }
            DistantWildlifeGroup advanced = group.withLazyPopulationUpdate(observedEnvironment, safeTime);
            updates.add(DistantWildlifePopulationUpdate.between(group, advanced));
            sourcePopulation += group.populationEstimate();
            targetPopulation += advanced.populationEstimate();
        }
        return new Calculation(updates, sourcePopulation, targetPopulation);
    }

    /** Immutable result containing only optimistic owner updates and summary counts. */
    public record Calculation(
            List<DistantWildlifePopulationUpdate> updates,
            int sourcePopulation,
            int targetPopulation
    ) {
        public Calculation {
            updates = List.copyOf(updates);
            sourcePopulation = Math.max(0, sourcePopulation);
            targetPopulation = Math.max(0, targetPopulation);
        }
    }

    private static double unit(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
