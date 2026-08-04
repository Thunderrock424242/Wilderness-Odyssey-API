package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Versioned union table that reconciles stable local basin ids across regions.
 *
 * <p>Chunk metadata keeps its original seed-derived id. When already-loaded
 * downstream chunks prove that two local regions drain together, this table
 * aliases both ids to a deterministic canonical id without rewriting chunks or
 * loading any neighbor.</p>
 */
public final class WatershedBasinSavedData extends SavedData {

    private static final String DATA_NAME = ModConstants.MOD_ID + "_watershed_basins";
    private static final int DATA_VERSION = 1;
    private static final int HARD_MAX_ALIASES = 16_384;
    private static final String VERSION_KEY = "version";
    private static final String BASIN_IDS = "basin_ids";
    private static final String PARENTS = "parents";

    private final LinkedHashMap<Long, Long> parents = new LinkedHashMap<>();

    /** Returns the authoritative per-dimension basin-alias table. */
    public static WatershedBasinSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(WatershedBasinSavedData::new, WatershedBasinSavedData::load),
                DATA_NAME
        );
    }

    static WatershedBasinSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WatershedBasinSavedData data = new WatershedBasinSavedData();
        if (tag == null || tag.getInt(VERSION_KEY) != DATA_VERSION) {
            return data;
        }
        long[] ids = tag.getLongArray(BASIN_IDS);
        long[] savedParents = tag.getLongArray(PARENTS);
        int count = Math.min(Math.min(ids.length, savedParents.length), HARD_MAX_ALIASES);
        for (int index = 0; index < count; index++) {
            if (ids[index] != 0L && savedParents[index] != 0L) {
                data.parents.put(ids[index], savedParents[index]);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] ids = new long[parents.size()];
        long[] savedParents = new long[parents.size()];
        int index = 0;
        for (Map.Entry<Long, Long> entry : parents.entrySet()) {
            ids[index] = entry.getKey();
            savedParents[index] = resolve(entry.getValue());
            index++;
        }
        tag.putInt(VERSION_KEY, DATA_VERSION);
        tag.putLongArray(BASIN_IDS, ids);
        tag.putLongArray(PARENTS, savedParents);
        return tag;
    }

    /** Returns the canonical id while repairing any persisted parent chain. */
    public long resolve(long basinId) {
        if (basinId == 0L) {
            return 0L;
        }
        long current = basinId;
        int guard = 0;
        while (parents.containsKey(current) && parents.get(current) != current && guard++ < HARD_MAX_ALIASES) {
            current = parents.get(current);
        }
        long root = current;
        current = basinId;
        guard = 0;
        while (parents.containsKey(current) && parents.get(current) != root && guard++ < HARD_MAX_ALIASES) {
            long next = parents.get(current);
            parents.put(current, root);
            current = next;
        }
        return root;
    }

    /** Merges two proven-connected local basins and returns their canonical id. */
    public long union(long first, long second) {
        if (first == 0L) {
            return resolve(second);
        }
        if (second == 0L) {
            return resolve(first);
        }
        long firstRoot = resolve(first);
        long secondRoot = resolve(second);
        if (firstRoot == secondRoot) {
            return firstRoot;
        }
        if (parents.size() + 2 > HARD_MAX_ALIASES) {
            return unsignedMinimum(firstRoot, secondRoot);
        }
        long canonical = unsignedMinimum(firstRoot, secondRoot);
        long alias = canonical == firstRoot ? secondRoot : firstRoot;
        parents.putIfAbsent(canonical, canonical);
        parents.put(alias, canonical);
        parents.put(first, canonical);
        parents.put(second, canonical);
        setDirty();
        return canonical;
    }

    /** Returns the number of retained local-id aliases. */
    public int size() {
        return parents.size();
    }

    private static long unsignedMinimum(long first, long second) {
        return Long.compareUnsigned(first, second) <= 0 ? first : second;
    }
}
