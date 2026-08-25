package com.thunder.wildernessodysseyapi.simulation.region;

import com.thunder.wildernessodysseyapi.performance.background.ActivityLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationRegionManagerTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");
    private static final ResourceLocation NETHER = ResourceLocation.withDefaultNamespace("the_nether");

    @Test
    void overlappingPlayerCoverageCoalescesOneRegionalUpdate() {
        SimulationRegionManager manager = new SimulationRegionManager(8, 8);
        SimulationRegion first = SimulationRegion.fromBlock(OVERWORLD, new BlockPos(1, 64, 1), 64);
        SimulationRegion overlapping = SimulationRegion.fromBlock(OVERWORLD, new BlockPos(63, 80, 63), 64);

        assertEquals(SimulationRegionManager.RequestResult.ACCEPTED,
                manager.request(first, SimulationTrigger.PLAYER_INTEREST, 10L));
        assertEquals(SimulationRegionManager.RequestResult.COALESCED,
                manager.request(overlapping, SimulationTrigger.WORLD_DISTURBANCE, 12L));
        assertEquals(1, manager.diagnostics().pendingRegions());

        SimulationRegionManager.PendingRegion request = manager.poll().orElseThrow();
        assertEquals(SimulationTrigger.WORLD_DISTURBANCE, request.trigger());
        assertEquals(12L, request.requestedTick());
        assertTrue(manager.poll().isEmpty());
    }

    @Test
    void playerInterestOutranksParticipantDiscoveredRelevance() {
        SimulationRegionManager manager = new SimulationRegionManager(4, 4);
        SimulationRegion region = SimulationRegion.fromBlock(OVERWORLD, BlockPos.ZERO, 64);

        manager.request(region, SimulationTrigger.SYSTEM_RELEVANCE, 10L);
        manager.request(region, SimulationTrigger.PLAYER_INTEREST, 12L);

        assertEquals(SimulationTrigger.PLAYER_INTEREST, manager.poll().orElseThrow().trigger());
    }

    @Test
    void queueAndRecentStateRemainBounded() {
        SimulationRegionManager manager = new SimulationRegionManager(1, 1);
        SimulationRegion first = SimulationRegion.fromBlock(OVERWORLD, new BlockPos(0, 64, 0), 64);
        SimulationRegion second = SimulationRegion.fromBlock(OVERWORLD, new BlockPos(128, 64, 0), 64);

        assertTrue(manager.request(first, SimulationTrigger.EXPLICIT_REQUEST, 1L).accepted());
        assertFalse(manager.request(second, SimulationTrigger.EXPLICIT_REQUEST, 1L).accepted());
        SimulationRegionManager.PendingRegion firstRequest = manager.poll().orElseThrow();
        manager.complete(firstRequest, ActivityLevel.ACTIVE, 2L);

        assertTrue(manager.request(second, SimulationTrigger.EXPLICIT_REQUEST, 3L).accepted());
        manager.complete(manager.poll().orElseThrow(), ActivityLevel.BACKGROUND, 4L);

        assertTrue(manager.state(first).isEmpty());
        assertEquals(ActivityLevel.BACKGROUND, manager.state(second).orElseThrow().activity());
        assertEquals(1, manager.diagnostics().trackedRegions());
        assertEquals(1, manager.diagnostics().rejectedRequests());
    }

    @Test
    void levelCleanupRemovesOnlyTheUnloadingDimension() {
        SimulationRegionManager manager = new SimulationRegionManager(8, 8);
        SimulationRegion overworld = SimulationRegion.fromBlock(OVERWORLD, BlockPos.ZERO, 64);
        SimulationRegion nether = SimulationRegion.fromBlock(NETHER, BlockPos.ZERO, 64);
        manager.request(overworld, SimulationTrigger.EXPLICIT_REQUEST, 1L);
        manager.request(nether, SimulationTrigger.EXPLICIT_REQUEST, 1L);

        manager.clearDimension(OVERWORLD);

        assertEquals(1, manager.diagnostics().pendingRegions());
        assertEquals(NETHER, manager.poll().orElseThrow().region().dimension());
    }
}
