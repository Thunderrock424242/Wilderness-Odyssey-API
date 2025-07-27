package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * The type Structure spawn tracker.
 */
public class StructureSpawnTracker extends SavedData {
    private static final String DATA_NAME = "wildernessodyssey_structure_spawn_tracker";
    private boolean hasSpawned;
    private long lastSpawnX;
    private long lastSpawnZ;

    /**
     * Instantiates a new Structure spawn tracker.
     */
// Constructor for creating a new tracker
    public StructureSpawnTracker() {
        this.hasSpawned = false;
        this.lastSpawnX = 0L;
        this.lastSpawnZ = 0L;
    }

    /**
     * @param tag
     * @param registries
     * @return
     */
    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        tag.putBoolean("hasSpawned", this.hasSpawned);
        tag.putLong("lastSpawnX", this.lastSpawnX);
        tag.putLong("lastSpawnZ", this.lastSpawnZ);
        return tag;
    }

    /**
     * Instantiates a new Structure spawn tracker.
     *
     * @param tag        the tag
     * @param registries the registries
     */
// Constructor for loading tracker from saved data
    public StructureSpawnTracker(CompoundTag tag, HolderLookup.Provider registries) {
        this.hasSpawned = tag.getBoolean("hasSpawned");
        this.lastSpawnX = tag.getLong("lastSpawnX");
        this.lastSpawnZ = tag.getLong("lastSpawnZ");
    }

    /**
     * Has spawned boolean.
     *
     * @return the boolean
     */
// Check if the structure has already spawned
    public boolean hasSpawned() {
        return this.hasSpawned;
    }

    /**
     * Mark as spawned.
     */
// Mark the structure as spawned
    public void markAsSpawned() {
        this.hasSpawned = true;
        this.setDirty(); // Mark data as dirty so it gets saved
    }

    /**
     * Update the last spawn position of the bunker.
     *
     * @param pos the world position
     */
    public void setLastSpawnPos(BlockPos pos) {
        this.lastSpawnX = pos.getX();
        this.lastSpawnZ = pos.getZ();
        this.setDirty();
    }

    /**
     * Get the last spawn position.
     */
    public BlockPos getLastSpawnPos() {
        return new BlockPos((int) this.lastSpawnX, 0, (int) this.lastSpawnZ);
    }

    /**
     * Get structure spawn tracker.
     *
     * @param level the level
     * @return the structure spawn tracker
     */
// Static method to get the tracker for the world
    public static StructureSpawnTracker get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        StructureSpawnTracker::new, // Constructor reference for new instances
                        StructureSpawnTracker::new  // Deserialization logic
                ),
                DATA_NAME
        );
    }
}
