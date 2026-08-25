package com.thunder.wildernessodysseyapi.ecosystem.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies lazy elapsed-time updates remain constant-time and pressure-sensitive. */
class AbstractEcosystemModelTest {

    @Test
    void threeDormantDaysAreAppliedAsOneAggregateCalculation() {
        int population = AbstractEcosystemModel.advancePopulation(
                100,
                AbstractEcosystemModel.TICKS_PER_DAY * 3,
                new AbstractEcosystemModel.Environment(0.9, 0.9, 0.05, 0.0, 0.0)
        );

        assertTrue(population > 100);
        assertEquals(population, AbstractEcosystemModel.advancePopulation(
                100,
                72_000L,
                new AbstractEcosystemModel.Environment(0.9, 0.9, 0.05, 0.0, 0.0)
        ));
    }

    @Test
    void severePressureAndDisturbanceReducePopulationWithoutGoingNegative() {
        int population = AbstractEcosystemModel.advancePopulation(
                100,
                AbstractEcosystemModel.TICKS_PER_DAY * 30,
                new AbstractEcosystemModel.Environment(0.1, 0.1, 1.0, 1.0, 1.0)
        );

        assertTrue(population >= 0 && population < 100);
    }

    @Test
    void fractionalRemainderPreservesSmallDailyChanges() {
        AbstractEcosystemModel.Environment environment = new AbstractEcosystemModel.Environment(
                0.9, 0.9, 0.05, 0.0, 0.0
        );
        AbstractEcosystemModel.PopulationState daily = new AbstractEcosystemModel.PopulationState(20, 0.0);
        for (int day = 0; day < 30; day++) {
            daily = AbstractEcosystemModel.advancePopulationState(
                    daily.population(),
                    daily.remainder(),
                    AbstractEcosystemModel.TICKS_PER_DAY,
                    environment
            );
        }
        AbstractEcosystemModel.PopulationState aggregate = AbstractEcosystemModel.advancePopulationState(
                20,
                0.0,
                AbstractEcosystemModel.TICKS_PER_DAY * 30,
                environment
        );

        assertTrue(daily.population() > 20);
        assertEquals(aggregate.population(), daily.population());
        assertEquals(aggregate.remainder(), daily.remainder(), 1.0E-9);
    }
}
