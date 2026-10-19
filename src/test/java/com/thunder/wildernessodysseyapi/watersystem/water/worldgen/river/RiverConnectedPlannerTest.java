package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RiverConnectedPlannerTest {
    private static final RiverConnectedPlanner.Settings SETTINGS = new RiverConnectedPlanner.Settings(8, 1, 2, 4, 4096);
    private static final RiverConnectedPlanner.TerrainSampler SLOPE = (x, z) ->
            new RiverConnectedPlanner.Terrain(x >= 88 ? 94 : 100 - Math.floorDiv(x, 16), x >= 88, 0.8f);

    @Test
    void sourceRoutesContinueAcrossAssemblyBordersToKnownWater() {
        var plan = RiverConnectedPlanner.plan(42, 0, 0, 32, SETTINGS, SLOPE);
        assertFalse(plan.edges().isEmpty());
        assertTrue(plan.edges().stream().anyMatch(edge -> edge.sourceX() < 32 && edge.targetX() >= 32));
        assertTrue(plan.edges().stream().allMatch(edge -> edge.sourceY() > edge.targetY() || edge.receiving()));
        assertTrue(plan.certifiedRoutes() > 0);
    }

    @Test
    void overlappingAssembliesUseIdenticalWorldPositionEdges() {
        var west = RiverConnectedPlanner.plan(42, 0, 0, 32, SETTINGS, SLOPE);
        var east = RiverConnectedPlanner.plan(42, 32, 0, 32, SETTINGS, SLOPE);
        var crossing = west.edges().stream().filter(edge -> edge.sourceX() < 32 && edge.targetX() >= 32).toList();
        assertFalse(crossing.isEmpty());
        for (var edge : crossing) assertTrue(east.edges().contains(edge), "Border geometry depends on requesting tile");
    }

    @Test
    void fineAdmissionReceivesWholeStableRoutesBeforeClipping() {
        var westRoutes = new java.util.HashMap<Long, List<RiverConnectedPlanner.Edge>>();
        var eastRoutes = new java.util.HashMap<Long, List<RiverConnectedPlanner.Edge>>();
        var west = RiverConnectedPlanner.plan(42, 0, 0, 32, SETTINGS, SLOPE, route -> {
            assertTrue(route.getLast().receiving());
            westRoutes.put(route.getFirst().id(), route);
            return true;
        });
        RiverConnectedPlanner.plan(42, 32, 0, 32, SETTINGS, SLOPE, route -> {
            eastRoutes.put(route.getFirst().id(), route);
            return true;
        });
        assertFalse(west.edges().isEmpty());
        int shared = 0;
        for (var entry : westRoutes.entrySet()) if (eastRoutes.containsKey(entry.getKey())) {
            assertEquals(entry.getValue(), eastRoutes.get(entry.getKey()));
            shared++;
        }
        assertTrue(shared > 0);
    }

    @Test
    void rejectingAFineRouteCannotRetainItsUpstreamFragment() {
        var plan = RiverConnectedPlanner.plan(42, 0, 0, 32, SETTINGS, SLOPE, route -> false);
        assertTrue(plan.edges().isEmpty());
        assertEquals(0, plan.certifiedRoutes());
    }

    @Test
    void unknownOutletsRejectWholeRoutes() {
        var plan = RiverConnectedPlanner.plan(42, 0, 0, 32, SETTINGS,
                (x, z) -> x >= 64 ? null : SLOPE.sample(x, z));
        assertTrue(plan.edges().isEmpty(), "A route was cut off at the prediction frontier");
    }

    @Test
    void closedBasinsNeverGainFabricatedOutlets() {
        var plan = RiverConnectedPlanner.plan(42, -32, -32, 32, SETTINGS,
                (x, z) -> new RiverConnectedPlanner.Terrain(140 + (Math.abs(x) + Math.abs(z)) / 16, false, 0.8f));
        assertTrue(plan.edges().isEmpty());
    }

    @Test
    void workLimitRejectsTheAssemblyAndBoundsProviderCalls() {
        var reads = new AtomicInteger();
        var small = new RiverConnectedPlanner.Settings(8, 1, 2, 4, 16);
        var plan = RiverConnectedPlanner.plan(42, 0, 0, 32, small, (x, z) -> {
            reads.incrementAndGet();
            return SLOPE.sample(x, z);
        });
        assertTrue(plan.edges().isEmpty());
        assertTrue(plan.budgetExceeded());
        assertTrue(reads.get() <= 16);
    }

    @Test
    void aridSourcesAreRarerWithoutChangingDownstreamRouting() {
        var dry = RiverConnectedPlanner.plan(42, 0, 0, 32, SETTINGS, (x, z) -> {
            var terrain = SLOPE.sample(x, z);
            return new RiverConnectedPlanner.Terrain(terrain.height(), terrain.water(), 0);
        });
        var wet = RiverConnectedPlanner.plan(42, 0, 0, 32, SETTINGS, SLOPE);
        assertTrue(dry.certifiedRoutes() < wet.certifiedRoutes());
    }

    @Test
    void parallelQueriesAndNegativeCoordinatesAreReproducible() throws Exception {
        RiverConnectedPlanner.TerrainSampler slope = (x, z) -> new RiverConnectedPlanner.Terrain(
                x >= -8 ? 94 : 100 - Math.floorDiv(x + 96, 16), x >= -8, 0.8f);
        var expected = RiverConnectedPlanner.plan(42, -96, -32, 32, SETTINGS, slope);
        assertFalse(expected.edges().isEmpty());
        try (var executor = Executors.newFixedThreadPool(4)) {
            Callable<RiverConnectedPlanner.Plan> call = () -> RiverConnectedPlanner.plan(42, -96, -32, 32, SETTINGS, slope);
            for (var result : executor.invokeAll(List.of(call, call, call, call))) assertEquals(expected, result.get());
        }
        assertThrows(UnsupportedOperationException.class, () -> expected.edges().clear());
    }
}
