package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RiverNetworkPlannerTest {
    private static final RiverTerrainSnapshot.Key KEY =
            new RiverTerrainSnapshot.Key(42L, "minecraft:overworld", 17L);

    @Test
    void routesDownhillAndAccumulatesConfluencesWithoutInventingWater() {
        var terrain = capture(-3, -3, 7, (x, z) -> land(100 + Math.abs(Math.floorDiv(x, 16))
                + Math.abs(Math.floorDiv(z, 16))));
        var plan = RiverNetworkPlanner.plan(terrain);
        var sink = plan.nodeAt(0, 0);
        assertEquals(RiverNetworkPlanner.Terminal.CLOSED_BASIN, sink.terminal());
        assertTrue(sink.contributingCells() >= 9);
        assertTrue(sink.confluence());
        assertEquals(100, sink.elevation());
        assertTrue(sink.potentialSpillElevation() > 100);
        assertFalse(sink.spillReachesKnownWater());
        for (var node : plan.nodes()) {
            var visited = new HashSet<Long>();
            while (node.terminal() == RiverNetworkPlanner.Terminal.DOWNSTREAM) {
                assertTrue(visited.add(node.cellKey()), "cyclic route");
                var downstream = plan.nodeAt(node.downstreamX(), node.downstreamZ());
                assertNotNull(downstream);
                assertTrue(downstream.elevation() < node.elevation(), "uphill reach");
                node = downstream;
            }
        }
    }

    @Test
    void retainsFlatHighElevationBasinsWithoutArtificialOutlets() {
        var plan = RiverNetworkPlanner.plan(capture(-2, -2, 5, (x, z) -> land(160)));
        var node = plan.nodeAt(0, 0);
        assertEquals(RiverNetworkPlanner.Terminal.CLOSED_BASIN, node.terminal());
        assertEquals(160, node.potentialSpillElevation());
        assertEquals(1, node.contributingCells());
    }

    @Test
    void receiverStopsRoutingAtItsActualElevationAndSpillIsSeparateFromFlow() {
        var plan = RiverNetworkPlanner.plan(capture(-3, -3, 7, (x, z) -> {
            int cx = Math.floorDiv(x, 16);
            int cz = Math.floorDiv(z, 16);
            if (cx == 2 && cz == 0) return new RiverTerrainSnapshot.Terrain(123, true);
            return land(cx == 0 && cz == 0 ? 120 : 125 + Math.abs(cz));
        }));
        var receiver = plan.nodeAt(2, 0);
        var basin = plan.nodeAt(0, 0);
        assertEquals(RiverNetworkPlanner.Terminal.RECEIVING_WATER, receiver.terminal());
        assertEquals(RiverNetworkPlanner.Terminal.CLOSED_BASIN, basin.terminal());
        assertEquals(125, basin.potentialSpillElevation());
        assertTrue(basin.spillReachesKnownWater());
        assertTrue(basin.potentialSpillElevation() > basin.elevation());
    }

    @Test
    void unknownNeighborsNeverBecomeInventedOutlets() {
        var terrain = capture(-2, -2, 5, (x, z) -> x >= 24 ? null : land(100 - x / 16));
        var plan = RiverNetworkPlanner.plan(terrain);
        assertEquals(RiverNetworkPlanner.Terminal.UNRESOLVED, plan.nodeAt(0, 0).terminal());
        assertNull(plan.nodeAt(1, 0));
        assertTrue(plan.unresolvedCells() > 0);
        assertFalse(plan.nodeAt(-1, 0).catchmentComplete());
    }

    @Test
    void resolvedBorderEdgesAgreeAcrossOverlappingPlanningWindows() {
        RiverTerrainSnapshot.TerrainSampler slope = (x, z) -> land(500 - Math.floorDiv(x, 16));
        var left = RiverNetworkPlanner.plan(capture(-4, -4, 9, slope));
        var right = RiverNetworkPlanner.plan(capture(-1, -4, 9, slope));
        for (int z = -3; z <= 3; z++) {
            for (int x = 0; x <= 3; x++) {
                var a = left.nodeAt(x, z);
                var b = right.nodeAt(x, z);
                assertEquals(RiverNetworkPlanner.Terminal.DOWNSTREAM, a.terminal());
                assertEquals(a.downstreamX(), b.downstreamX());
                assertEquals(a.downstreamZ(), b.downstreamZ());
                assertEquals(a.reachId(), b.reachId());
                assertEquals(x + 1, a.downstreamX());
                assertEquals(z, a.downstreamZ());
            }
        }
    }

    @Test
    void shuffledAndConcurrentRequestsProduceIdenticalImmutablePlans() throws Exception {
        var terrain = capture(-4, -4, 9, (x, z) -> land(300 + Math.floorDiv(x, 16) * 2 + Math.floorDiv(z, 16)));
        var expected = RiverNetworkPlanner.plan(terrain).nodes();
        List<Callable<List<RiverNetworkPlanner.Node>>> work = new ArrayList<>();
        for (int i = 0; i < 32; i++) work.add(() -> RiverNetworkPlanner.plan(terrain).nodes());
        Collections.shuffle(work, new java.util.Random(7));
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (var result : executor.invokeAll(work)) assertEquals(expected, result.get());
        }
        assertThrows(UnsupportedOperationException.class, () -> expected.clear());
    }

    @Test
    void snapshotDoesNotRetainMutableSamplerInputs() {
        int[] elevation = {200};
        var terrain = capture(-2, -2, 5, (x, z) -> land(elevation[0]));
        elevation[0] = 1;
        assertEquals(200, RiverNetworkPlanner.plan(terrain).nodeAt(0, 0).elevation());
    }

    @Test
    void rejectsExcessWorkAndOverflowBeforeCallingTerrainSampler() {
        AtomicInteger requests = new AtomicInteger();
        RiverTerrainSnapshot.TerrainSampler sampler = (x, z) -> { requests.incrementAndGet(); return land(64); };
        assertThrows(IllegalArgumentException.class, () -> RiverTerrainSnapshot.capture(KEY,
                new RiverTerrainSnapshot.Layout(0, 0, 10, 10, 16, 99), sampler));
        assertThrows(IllegalArgumentException.class, () -> RiverTerrainSnapshot.capture(KEY,
                new RiverTerrainSnapshot.Layout(Integer.MAX_VALUE, 0, 3, 3, 16, 9), sampler));
        assertEquals(0, requests.get());
    }

    @Test
    void negativeWorldCoordinatesUseOneGlobalLatticeAndExactlyTheAllowedBudget() {
        List<String> requests = new ArrayList<>();
        RiverTerrainSnapshot.capture(KEY, new RiverTerrainSnapshot.Layout(-1, -1, 3, 3, 16, 9),
                (x, z) -> { requests.add(x + "," + z); return land(64); });
        assertEquals(9, requests.size());
        assertEquals("-8,-8", requests.getFirst());
        assertEquals("24,24", requests.getLast());
    }

    @Test
    void worldAndTerrainIdentityChangeReachIdentity() {
        var layout = new RiverTerrainSnapshot.Layout(-1, -1, 3, 3, 16, 9);
        var first = RiverNetworkPlanner.plan(RiverTerrainSnapshot.capture(KEY, layout, (x, z) -> land(64)));
        var second = RiverNetworkPlanner.plan(RiverTerrainSnapshot.capture(
                new RiverTerrainSnapshot.Key(43, "minecraft:overworld", 17), layout, (x, z) -> land(64)));
        var third = RiverNetworkPlanner.plan(RiverTerrainSnapshot.capture(
                new RiverTerrainSnapshot.Key(42, "minecraft:the_nether", 18), layout, (x, z) -> land(64)));
        assertNotEquals(first.nodeAt(0, 0).reachId(), second.nodeAt(0, 0).reachId());
        assertNotEquals(first.nodeAt(0, 0).reachId(), third.nodeAt(0, 0).reachId());
    }

    private static RiverTerrainSnapshot capture(int x, int z, int size,
                                                RiverTerrainSnapshot.TerrainSampler sampler) {
        return RiverTerrainSnapshot.capture(KEY,
                new RiverTerrainSnapshot.Layout(x, z, size, size, 16, size * size), sampler);
    }

    private static RiverTerrainSnapshot.Terrain land(int height) {
        return new RiverTerrainSnapshot.Terrain(height, false);
    }
}
