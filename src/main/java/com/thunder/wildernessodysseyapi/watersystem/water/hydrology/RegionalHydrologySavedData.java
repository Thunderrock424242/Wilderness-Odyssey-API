package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned non-evicting physical inventory. The old LRU watershed save remains
 * a terrain/presentation cache and cannot delete any of this water.
 */
public final class RegionalHydrologySavedData extends SavedData {
    private static final String NAME = ModConstants.MOD_ID + "_regional_water";
    private static final int VERSION = 1;
    private final Map<Long, RegionalHydrologyState> regions = new LinkedHashMap<>();
    private final List<Long> keys = new ArrayList<>();
    private long terrainRevision;
    private int cursor;

    public static RegionalHydrologySavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(RegionalHydrologySavedData::new, RegionalHydrologySavedData::load), NAME);
    }

    static RegionalHydrologySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("version") != VERSION) {
            throw new IllegalArgumentException("Unsupported regional water save version; refusing to discard inventories");
        }
        RegionalHydrologySavedData data = new RegionalHydrologySavedData();
        ListTag entries = tag.getList("regions", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            RegionalHydrologyState state = RegionalHydrologyState.load(entries.getCompound(i));
            if (data.regions.putIfAbsent(state.key, state) != null) {
                throw new IllegalArgumentException("Duplicate regional water inventory " + state.key);
            }
            data.keys.add(state.key);
        }
        data.terrainRevision = tag.getLong("terrain_revision");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", VERSION);
        tag.putLong("terrain_revision", terrainRevision);
        ListTag entries = new ListTag();
        for (RegionalHydrologyState state : regions.values()) entries.add(state.save());
        tag.put("regions", entries);
        return tag;
    }

    public RegionalHydrologyState state(long key) { return regions.get(key); }
    public int size() { return regions.size(); }
    long terrainRevision() { return terrainRevision; }
    Map<Long, RegionalHydrologyState> regions() { return regions; }

    /** Refuses new cells at capacity; never replaces a previously owned inventory. */
    boolean admit(RegionalHydrologyState state, int capacity) {
        if (regions.containsKey(state.key) || size() >= capacity) return false;
        regions.put(state.key, state);
        keys.add(state.key);
        terrainChanged();
        return true;
    }

    /** Bounded round-robin discovery is independent of players and chunk loading. */
    RegionalHydrologyState next() {
        if (keys.isEmpty()) return null;
        cursor = Math.floorMod(cursor, keys.size());
        return regions.get(keys.get(cursor++));
    }

    void terrainChanged() { terrainRevision++; setDirty(); }
}
