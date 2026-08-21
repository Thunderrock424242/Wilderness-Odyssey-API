package com.thunder.wildernessodysseyapi.worldgen.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;

/**
 * Persists the world positions of cryo tubes that can be used for player spawns.
 */
public class CryoSpawnData extends SavedData {
    private static final String DATA_NAME = "wildernessodyssey_cryo_spawn_data";
    private static final String VERSION_KEY = "version";
    private static final String STARTER_BUNKER_PLACED_KEY = "starter_bunker_placed";
    private static final String STARTER_BUNKER_BOUNDS_KEY = "starter_bunker_bounds";
    private static final int CURRENT_VERSION = 4;
    private final Set<Long> cryoPositions = new HashSet<>();
    private int version = CURRENT_VERSION;
    private boolean starterBunkerPlaced;
    private AABB starterBunkerBounds;

    public CryoSpawnData() {
    }

    public CryoSpawnData(CompoundTag tag, HolderLookup.Provider registries) {
        this.version = tag.contains(VERSION_KEY, Tag.TAG_INT) ? tag.getInt(VERSION_KEY) : 1;
        long[] entries = tag.getLongArray("cryo_positions");
        for (long entry : entries) {
            cryoPositions.add(entry);
        }
        this.starterBunkerPlaced = tag.getBoolean(STARTER_BUNKER_PLACED_KEY) || !cryoPositions.isEmpty();
        if (tag.contains(STARTER_BUNKER_BOUNDS_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag bounds = tag.getCompound(STARTER_BUNKER_BOUNDS_KEY);
            this.starterBunkerBounds = new AABB(
                    bounds.getDouble("min_x"), bounds.getDouble("min_y"), bounds.getDouble("min_z"),
                    bounds.getDouble("max_x"), bounds.getDouble("max_y"), bounds.getDouble("max_z"));
        }
        migrateIfNeeded();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        tag.putInt(VERSION_KEY, version);
        tag.putBoolean(STARTER_BUNKER_PLACED_KEY, starterBunkerPlaced);
        tag.putLongArray("cryo_positions", cryoPositions.stream().mapToLong(Long::longValue).toArray());
        if (starterBunkerBounds != null) {
            CompoundTag bounds = new CompoundTag();
            bounds.putDouble("min_x", starterBunkerBounds.minX);
            bounds.putDouble("min_y", starterBunkerBounds.minY);
            bounds.putDouble("min_z", starterBunkerBounds.minZ);
            bounds.putDouble("max_x", starterBunkerBounds.maxX);
            bounds.putDouble("max_y", starterBunkerBounds.maxY);
            bounds.putDouble("max_z", starterBunkerBounds.maxZ);
            tag.put(STARTER_BUNKER_BOUNDS_KEY, bounds);
        }
        return tag;
    }

    /**
     * Adds a cryo tube position to the saved data.
     *
     * @return {@code true} if the position was newly added
     */
    public boolean add(BlockPos pos) {
        return add(pos.asLong());
    }

    /**
     * Adds multiple cryo tube positions.
     *
     * @return {@code true} if any entry was newly added
     */
    public boolean addAll(Collection<BlockPos> positions) {
        boolean added = false;
        for (BlockPos pos : positions) {
            if (add(pos.asLong())) {
                added = true;
            }
        }
        return added;
    }

    /**
     * Replaces all stored cryo tube positions.
     */
    public void replaceAll(Collection<BlockPos> positions) {
        Set<Long> newPositions = new HashSet<>(positions.size());
        for (BlockPos pos : positions) {
            newPositions.add(pos.asLong());
        }
        if (!cryoPositions.equals(newPositions)) {
            cryoPositions.clear();
            cryoPositions.addAll(newPositions);
            setDirty();
        }
    }

    /**
     * @return an immutable list of all known cryo tube positions.
     */
    public List<BlockPos> getPositions() {
        if (cryoPositions.isEmpty()) {
            return List.of();
        }
        List<BlockPos> positions = new ArrayList<>(cryoPositions.size());
        for (long entry : cryoPositions) {
            positions.add(BlockPos.of(entry));
        }
        return List.copyOf(positions);
    }

    /**
     * @return {@code true} after the starter bunker has been successfully handled for this world.
     */
    public boolean hasStarterBunkerPlaced() {
        return starterBunkerPlaced;
    }

    /**
     * Marks starter bunker placement as complete so existing saves do not repeat bunker spawn checks.
     */
    public void markStarterBunkerPlaced() {
        if (!starterBunkerPlaced) {
            starterBunkerPlaced = true;
            setDirty();
        }
    }

    /** Persists the exact successful bunker bounds used by hostile-spawn protection. */
    public void setStarterBunkerBounds(AABB bounds) {
        if (bounds == null) {
            return;
        }
        this.starterBunkerBounds = new AABB(
                bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);
        setDirty();
    }

    /** Returns durable bunker bounds when this save has recorded them. */
    public Optional<AABB> getStarterBunkerBounds() {
        return Optional.ofNullable(starterBunkerBounds);
    }

    /**
     * Retrieve the data instance for the given world.
     */
    public static CryoSpawnData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CryoSpawnData::new, CryoSpawnData::new),
                DATA_NAME
        );
    }

    private boolean add(long position) {
        if (cryoPositions.add(position)) {
            setDirty();
            return true;
        }
        return false;
    }

    private void migrateIfNeeded() {
        if (version < CURRENT_VERSION) {
            version = CURRENT_VERSION;
            setDirty();
        }
    }
}
