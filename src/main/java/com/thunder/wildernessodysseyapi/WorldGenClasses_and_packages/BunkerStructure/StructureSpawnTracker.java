package com.thunder.wildernessodysseyapi.WorldGenClasses_and_packages.BunkerStructure;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * The type Structure spawn tracker.
 */
public class StructureSpawnTracker extends SavedData {
    private static final String DATA_NAME = "wildernessodyssey_structure_spawn_tracker";
    private boolean hasSpawned;

    /**
     * Instantiates a new Structure spawn tracker.
     */
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

    /**
     * Instantiates a new Structure spawn tracker.
     *
     * @param tag        the tag
     * @param registries the registries
     */
// Constructor for loading tracker from saved data
    public StructureSpawnTracker(CompoundTag tag, HolderLookup.Provider registries) {
        this.hasSpawned = tag.getBoolean("hasSpawned");
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
