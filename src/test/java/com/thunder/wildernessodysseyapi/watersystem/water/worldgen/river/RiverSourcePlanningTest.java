package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RiverSourcePlanningTest {
    private static final RiverGenerationSettings GENERATION = new RiverGenerationSettings(true, 1, 2, 1, 2, 4, 256, 12288, true, true, true);
    private static final RiverConnectedPlanner.Settings PLANNING = new RiverConnectedPlanner.Settings(8, 1, 2, 2, 4096);
    private static final RiverConnectedPlanner.TerrainSampler SLOPE = (x, z) ->
            new RiverConnectedPlanner.Terrain(x >= 88 ? 94 : 100 - Math.floorDiv(x, 16), x >= 88, 0.8f);

    @Test
    void independentlyCertifiedRoutesProduceIdenticalSharedColumns() {
        var west = RiverSourcePlanning.plan(42, 0, 0, GENERATION, PLANNING, 4, SLOPE);
        var east = RiverSourcePlanning.plan(42, 32, 0, GENERATION, PLANNING, 4, SLOPE);
        assertFalse(west.shape().columns().isEmpty());
        assertFalse(east.shape().columns().isEmpty());
        int shared = 0;
        for (var column : west.shape().columns()) if (column.x() >= 31) {
            assertEquals(column, east.shape().at(column.x(), column.z()));
            shared++;
        }
        assertTrue(shared > 0);
    }

    @Test
    void fineBudgetExhaustionNeverRetainsEarlierCertifiedFragments() {
        var full = RiverSourcePlanning.plan(42, 0, 0, GENERATION, PLANNING, 4, SLOPE);
        assertFalse(full.topology().budgetExceeded());
        assertTrue(full.certifiedBeforeExhaustion() > 0);
        int limit = full.topology().terrainSamples() - 1;
        var calls = new AtomicInteger();
        var settings = new RiverConnectedPlanner.Settings(8, 1, 2, 2, limit);
        var plan = RiverSourcePlanning.plan(42, 0, 0, GENERATION, settings, 4, (x, z) -> {
            calls.incrementAndGet();
            return SLOPE.sample(x, z);
        });
        assertTrue(plan.topology().budgetExceeded());
        assertTrue(plan.shape().columns().isEmpty());
        assertTrue(plan.topology().edges().isEmpty());
        assertTrue(plan.certifiedBeforeExhaustion() > 0, "Exhaustion must occur after a route was provisionally accepted");
        assertTrue(calls.get() <= limit);
    }

    @Test
    void wholeRouteTerrainFitPreventsAnUpstreamFragmentBeforeAnExcessiveCut() {
        var plan = RiverSourcePlanning.plan(42, 0, 0, GENERATION, PLANNING, 4, (x, z) -> {
            var original = SLOPE.sample(x, z);
            return x == 53 ? new RiverConnectedPlanner.Terrain(120, false, 0.8f) : original;
        });
        assertTrue(plan.shape().columns().isEmpty());
    }

    @Test
    void aggregateRasterWorkIsCappedEvenWhenSamplesAreCached() {
        var budget = new RiverChannelShape.RasterBudget(100);
        var edge = new RiverConnectedPlanner.Edge(42, 123, 8, 8, 99, 24, 8, 98, 3, 2, false);
        var shape = RiverChannelShape.rasterComplete(java.util.List.of(edge), 1, 4, true, SLOPE, budget);
        assertTrue(shape.columns().isEmpty());
        assertTrue(budget.exhausted());
        assertEquals(100, budget.visits());
    }
}
