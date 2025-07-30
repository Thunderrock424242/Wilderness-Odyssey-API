package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks where bunkers have spawned in the world.
 */
public class StructureSpawnTracker extends SavedData {
    private static final String DATA_NAME = "wildernessodyssey_structure_spawn_tracker";

    private final List<Long> spawnPositions = new ArrayList<>();

    public StructureSpawnTracker() {
    }

    public StructureSpawnTracker(CompoundTag tag, HolderLookup.Provider registries) {
        long[] arr = tag.getLongArray("spawns");
        for (long l : arr) {
            spawnPositions.add(l);
        }
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        tag.putLongArray("spawns", spawnPositions.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    /**
     * Add a new bunker spawn position.
     */
    public void addSpawnPos(BlockPos pos) {
        spawnPositions.add(pos.asLong());
        setDirty();
    }

    /**
     * Check if the given position is far enough from all previous spawns.
     */
    public boolean isFarEnough(BlockPos pos, int distanceChunks) {
        for (long l : spawnPositions) {
            BlockPos p = BlockPos.of(l);
            long dx = (pos.getX() - p.getX()) / 16L;
            long dz = (pos.getZ() - p.getZ()) / 16L;
            long dist = Math.max(Math.abs(dx), Math.abs(dz));
            if (dist < distanceChunks) {
                return false;
            }
        }
        return true;
    }

    public boolean hasSpawned() {
        return !spawnPositions.isEmpty();
    }

    /**
     * @return number of bunkers spawned so far
     */
    public int getSpawnCount() {
        return spawnPositions.size();
    }

    public static StructureSpawnTracker get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StructureSpawnTracker::new, StructureSpawnTracker::new),
                DATA_NAME
        );
    }
}
