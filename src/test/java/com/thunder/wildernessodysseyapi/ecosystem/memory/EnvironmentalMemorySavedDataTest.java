package com.thunder.wildernessodysseyapi.ecosystem.memory;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentalMemorySavedDataTest {

    private static final EnvironmentalMemorySavedData.Settings SETTINGS =
            new EnvironmentalMemorySavedData.Settings(0.20, 0.0025, 1.0);

    @Test
    void multiplayerActivityCombinesInOneRegionalCell() {
        EnvironmentalMemorySavedData data = new EnvironmentalMemorySavedData();
        BlockPos firstPosition = new BlockPos(4, 70, 4);
        BlockPos secondPosition = new BlockPos(12, 70, 12);
        long key = new ChunkPos(firstPosition).toLong();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();

        data.add(key, firstPosition, 0.20, DisturbanceSource.PLAYER_MOVEMENT,
                firstPlayer, 1_000L, SETTINGS);
        EnvironmentalMemory combined = data.add(
                key, secondPosition, 0.30, DisturbanceSource.PLAYER_ACTIVITY,
                secondPlayer, 1_000L, SETTINGS);

        assertEquals(0.50, combined.disturbance(), 0.000_001);
        assertEquals(0.50, combined.playerTraffic(), 0.000_001);
        assertEquals(secondPlayer, combined.lastSourceId());
        assertEquals(secondPosition, combined.lastSourcePosition());
    }

    @Test
    void disturbanceDecaysFromElapsedGameTimeWithoutTickingCells() {
        EnvironmentalMemorySavedData data = new EnvironmentalMemorySavedData();
        BlockPos position = new BlockPos(32, 64, -16);
        long key = new ChunkPos(position).toLong();
        data.add(key, position, 0.80, DisturbanceSource.EXPLOSION, null, 1_000L, SETTINGS);

        EnvironmentalMemory halfwayThroughDay = data.memory(key, 13_000L, SETTINGS).orElseThrow();

        assertEquals(0.70, halfwayThroughDay.disturbance(), 0.000_001);
        assertEquals(0.10, halfwayThroughDay.disturbanceDecayApplied(), 0.000_001);
        assertEquals(12_000L, halfwayThroughDay.elapsedTicks());
    }

    @Test
    void nbtRoundTripPreservesChannelsSourceAndTimestamp() {
        EnvironmentalMemorySavedData original = new EnvironmentalMemorySavedData();
        BlockPos position = new BlockPos(-20, 81, 35);
        long key = new ChunkPos(position).toLong();
        UUID sourceId = UUID.randomUUID();
        original.add(key, position, 0.40, DisturbanceSource.COMBAT, sourceId, 5_000L, SETTINGS);
        original.add(key, position, 0.15, DisturbanceSource.FIRE, null, 5_000L, SETTINGS);

        CompoundTag encoded = original.save(new CompoundTag(), null);
        EnvironmentalMemorySavedData decoded = EnvironmentalMemorySavedData.load(encoded, null);
        EnvironmentalMemory memory = decoded.memory(key, 5_000L, SETTINGS).orElseThrow();

        assertEquals(0.55, memory.disturbance(), 0.000_001);
        assertEquals(0.40, memory.recentCombatActivity(), 0.000_001);
        assertEquals(0.15, memory.recentFireActivity(), 0.000_001);
        assertEquals(DisturbanceSource.FIRE, memory.lastSource());
        assertEquals(5_000L, memory.lastUpdatedGameTime());
        assertEquals(1, decoded.size());
    }

    @Test
    void effectivelyEmptyCellIsRemovedOnLazyAccess() {
        EnvironmentalMemorySavedData data = new EnvironmentalMemorySavedData();
        BlockPos position = BlockPos.ZERO;
        long key = new ChunkPos(position).toLong();
        data.add(key, position, 0.01, DisturbanceSource.PLAYER_MOVEMENT, null, 0L, SETTINGS);

        assertTrue(data.memory(key, 1_200L, SETTINGS).isEmpty());
        assertEquals(0, data.size());
        assertTrue(data.save(new CompoundTag(), null).getList("cells", 10).isEmpty());
    }

    @Test
    void independentSavedDataInstancesSeparateDimensions() {
        EnvironmentalMemorySavedData overworld = new EnvironmentalMemorySavedData();
        EnvironmentalMemorySavedData nether = new EnvironmentalMemorySavedData();
        BlockPos position = new BlockPos(8, 64, 8);
        long key = new ChunkPos(position).toLong();

        overworld.add(key, position, 0.65, DisturbanceSource.EXPLOSION, null, 100L, SETTINGS);

        assertTrue(overworld.memory(key, 100L, SETTINGS).isPresent());
        assertTrue(nether.memory(key, 100L, SETTINGS).isEmpty());
        assertEquals(1, overworld.size());
        assertEquals(0, nether.size());
    }

    @Test
    void maintenanceChecksOnlyOneRotatingCellPerAccess() {
        EnvironmentalMemorySavedData data = new EnvironmentalMemorySavedData();
        for (int chunkX = 0; chunkX < 100; chunkX++) {
            BlockPos position = new BlockPos(chunkX * 16, 64, 0);
            data.add(new ChunkPos(position).toLong(), position, 0.01,
                    DisturbanceSource.PLAYER_MOVEMENT, null, 0L, SETTINGS);
        }

        data.pruneOne(24_000L, SETTINGS);

        assertEquals(99, data.size());
    }

    @Test
    void configuredMaximumCapsCombinedDisturbance() {
        EnvironmentalMemorySavedData.Settings capped =
                new EnvironmentalMemorySavedData.Settings(0.0, 0.001, 0.75);
        EnvironmentalMemorySavedData data = new EnvironmentalMemorySavedData();
        BlockPos position = BlockPos.ZERO;
        long key = new ChunkPos(position).toLong();

        data.add(key, position, 0.60, DisturbanceSource.COMBAT, null, 0L, capped);
        EnvironmentalMemory memory = data.add(
                key, position, 0.60, DisturbanceSource.COMBAT, null, 0L, capped);

        assertEquals(0.75, memory.disturbance(), 0.000_001);
        assertEquals(0.75, memory.recentCombatActivity(), 0.000_001);
    }
}
