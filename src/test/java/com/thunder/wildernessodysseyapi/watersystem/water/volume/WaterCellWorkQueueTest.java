package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WaterCellWorkQueueTest {
    @Test
    void neighborhoodWakePreservesAnUnavailableCellsRetryCooldown() {
        WaterCellWorkQueue queue = new WaterCellWorkQueue();
        queue.offer(10, 100);
        queue.offer(10, 0);
        assertNull(queue.poll(99));
        assertEquals(10, queue.poll(100));
    }

    @Test
    void explicitTerrainWakeSchedulesAConservedDisplacementReservoir() {
        WaterVolumeChunk volume = new WaterVolumeChunk();
        BlockPos pos = new BlockPos(7, 60, 7);
        volume.set(pos, WaterVolumeChunk.WaterCell.still(4096,
                WaterVolumeChunk.FLAG_DISPLACEMENT_RESERVOIR));
        volume.scheduleWork(pos, 0);
        assertEquals(WaterVolumeChunk.pack(pos), volume.pollWork(0));
        assertEquals(4096, volume.get(pos).volumeUnits());
    }

    @Test
    void exposedReservoirWakeSurvivesDeferralAndSaveReload() {
        WaterVolumeChunk volume = new WaterVolumeChunk();
        BlockPos pos = new BlockPos(15, 60, 7);
        volume.set(pos, WaterVolumeChunk.WaterCell.still(4096,
                WaterVolumeChunk.FLAG_DISPLACEMENT_RESERVOIR));
        volume.scheduleWork(pos, 10);
        WaterVolumeChunk restored = new WaterVolumeChunk();
        restored.deserializeNBT(null, volume.serializeNBT(null));
        assertEquals(WaterVolumeChunk.pack(pos), restored.pollWork(0));
        assertTrue(restored.get(pos).displacementReservoir());
        assertEquals(4096, restored.get(pos).volumeUnits());
    }

    @Test
    void repeatedWritesCoalesceAndCoolingEntriesDoNotStarveReadyWork() {
        WaterCellWorkQueue queue = new WaterCellWorkQueue();
        queue.offer(10, 50);
        queue.offer(10, 70);
        queue.offer(20, 0);
        assertEquals(2, queue.size());
        assertEquals(20, queue.poll(1));
        assertNull(queue.poll(69));
        assertEquals(10, queue.poll(70));
        assertEquals(0, queue.size());
    }

    @Test
    void boundedProbeRotationEventuallyVisitsReadyWorkBehindLargeDeferredBacklog() {
        WaterCellWorkQueue queue = new WaterCellWorkQueue();
        for (int position = 0; position < 100; position++) queue.offer(position, 100);
        queue.offer(500, 0);
        Integer ready = null;
        for (int pass = 0; pass < 13 && ready == null; pass++) ready = queue.poll(0);
        assertEquals(500, ready);
        assertEquals(100, queue.size());
    }

    @Test
    void reloadResumesAwakeAndPendingCellsButLeavesStableAndHiddenCellsDormant() {
        WaterVolumeChunk original = new WaterVolumeChunk();
        BlockPos awake = new BlockPos(0, 60, 0);
        BlockPos asleep = new BlockPos(1, 60, 0);
        BlockPos dry = new BlockPos(2, 60, 0);
        BlockPos hidden = new BlockPos(3, 60, 0);
        original.set(awake, WaterVolumeChunk.WaterCell.still(3072, 0));
        original.set(asleep, WaterVolumeChunk.WaterCell.still(1024, WaterVolumeChunk.FLAG_SLEEPING));
        original.set(dry, WaterVolumeChunk.WaterCell.EMPTY.withAddedFlags(WaterVolumeChunk.FLAG_PROJECTION_PENDING));
        original.set(hidden, WaterVolumeChunk.WaterCell.still(4096,
                WaterVolumeChunk.FLAG_DISPLACEMENT_RESERVOIR | WaterVolumeChunk.FLAG_SLEEPING));
        WaterVolumeChunk restored = new WaterVolumeChunk();
        restored.deserializeNBT(null, original.serializeNBT(null));
        assertEquals(2, restored.pendingWork());
        var work = java.util.Set.of(restored.pollWork(0), restored.pollWork(0));
        assertEquals(java.util.Set.of(WaterVolumeChunk.pack(awake), WaterVolumeChunk.pack(dry)), work);
        assertEquals(8192, restored.snapshot().stream().mapToInt(entry -> entry.cell().volumeUnits()).sum());
    }

    @Test
    void absentPositionsCannotGrowWorkAndLatestSleepCancelsIt() {
        WaterVolumeChunk volume = new WaterVolumeChunk();
        BlockPos pos = new BlockPos(7, 60, 7);
        volume.scheduleWork(pos, 0);
        assertEquals(0, volume.pendingWork());
        volume.set(pos, WaterVolumeChunk.WaterCell.still(4096, 0));
        volume.scheduleWork(pos, 0);
        volume.scheduleWork(pos, 10);
        assertEquals(1, volume.pendingWork());
        volume.set(pos, WaterVolumeChunk.WaterCell.still(4096, WaterVolumeChunk.FLAG_SLEEPING));
        assertEquals(0, volume.pendingWork());
        assertEquals(4096, volume.get(pos).volumeUnits());
    }
}
