package com.thunder.wildernessodysseyapi.simulation.integration;

import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeSimulationLod;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeForm;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeGroup;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.AbstractEcosystemModel;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemCellKey;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemRegionSnapshot;
import com.thunder.wildernessodysseyapi.environment.api.RegionalEnvironmentSnapshot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the first real simulation participant's worker-safe population math. */
class PopulationEcologyModelTest {

    @Test
    void regionalCarryingPressureRisesWhenPopulationExceedsResources() {
        var sparse = snapshot(12, 0.65, 0.60, 0.0, 0.0, 0.0);
        var crowded = snapshot(180, 0.65, 0.60, 0.0, 0.0, 0.0);

        AbstractEcosystemModel.Environment sparseConditions = PopulationEcologyModel.conditions(
                sparse, RegionalEnvironmentSnapshot.EMPTY, 0.0, 96
        );
        AbstractEcosystemModel.Environment crowdedConditions = PopulationEcologyModel.conditions(
                crowded, RegionalEnvironmentSnapshot.EMPTY, 0.0, 96
        );

        assertTrue(crowdedConditions.foodPressure() > sparseConditions.foodPressure());
        assertTrue(crowdedConditions.foodAvailability() < sparseConditions.foodAvailability());
    }

    @Test
    void calculationUsesPersistedElapsedTimeAndProducesOptimisticOwnerUpdate() {
        DistantWildlifeGroup group = group(20, 0L);
        long gameTime = AbstractEcosystemModel.TICKS_PER_DAY * 30;

        PopulationEcologyModel.Calculation calculation = PopulationEcologyModel.calculate(
                List.of(group),
                new AbstractEcosystemModel.Environment(0.9, 0.9, 0.0, 0.0, 0.0),
                gameTime,
                AbstractEcosystemModel.TICKS_PER_DAY
        );

        assertEquals(1, calculation.updates().size());
        assertEquals(group.populationEstimate(), calculation.sourcePopulation());
        assertTrue(calculation.targetPopulation() > calculation.sourcePopulation());
        assertEquals(gameTime, calculation.updates().getFirst().referenceGameTime());
        assertTrue(calculation.updates().getFirst().matches(group));
    }

    @Test
    void calculationSkipsGroupsBeforeConfiguredCadence() {
        DistantWildlifeGroup group = group(20, 10_000L);

        PopulationEcologyModel.Calculation calculation = PopulationEcologyModel.calculate(
                List.of(group),
                AbstractEcosystemModel.Environment.NEUTRAL,
                20_000L,
                AbstractEcosystemModel.TICKS_PER_DAY
        );

        assertTrue(calculation.updates().isEmpty());
        assertEquals(0, calculation.sourcePopulation());
        assertEquals(0, calculation.targetPopulation());
    }

    private static EcosystemRegionSnapshot snapshot(
            int population,
            double food,
            double water,
            double pressure,
            double disturbance,
            double weather
    ) {
        return new EcosystemRegionSnapshot(
                new EcosystemCellKey(0, 0),
                WildlifeSimulationLod.DORMANT,
                Map.of(ResourceLocation.withDefaultNamespace("cow"), population),
                1,
                new EcosystemCellKey(1, 0),
                food,
                water,
                pressure,
                disturbance,
                weather,
                0L,
                0L
        );
    }

    private static DistantWildlifeGroup group(int population, long populationReferenceTime) {
        return new DistantWildlifeGroup(
                7L,
                ResourceLocation.withDefaultNamespace("cow"),
                population,
                0.0, 64.0, 0.0,
                1.0, 0.0,
                0.4, 0.0,
                91L, 0L, populationReferenceTime,
                0.65, 0.60, 0.0, 0.0, 0.0,
                DistantWildlifeForm.GROUND,
                false,
                true
        );
    }
}
