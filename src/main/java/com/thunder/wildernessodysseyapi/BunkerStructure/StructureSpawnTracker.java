package com.thunder.wildernessodysseyapi.BunkerStructure;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

public class StructureSpawnTracker extends SavedData {
    private static final String DATA_NAME = "wildernessodyssey_structure_spawn_tracker";
    private boolean hasSpawned;

    // Constructor for creating a new tracker
    public StructureSpawnTracker() {
        this.hasSpawned = false;
    }

    /**
     * @param tag
     * @param registries
     * @return
     */
    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        return null;
    }

    // Constructor for loading tracker from saved data
    public StructureSpawnTracker(CompoundTag tag, HolderLookup.Provider registries) {
        this.hasSpawned = tag.getBoolean("hasSpawned");
    }

    // Check if the structure has already spawned
    public boolean hasSpawned() {
        return this.hasSpawned;
    }

    // Mark the structure as spawned
    public void markAsSpawned() {
        this.hasSpawned = true;
        this.setDirty(); // Mark data as dirty so it gets saved
    }

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
