package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Versioned per-dimension storage for compact watershed chunk cells.
 *
 * <p>Parallel primitive arrays keep the save bounded and make malformed count
 * mismatches recoverable. Entries are initialized only from already-loaded
 * chunks and are evicted by least-recent access when the configured hard budget
 * is exceeded.</p>
 */
public final class WatershedSavedData extends SavedData {

    private static final String DATA_NAME = ModConstants.MOD_ID + "_watersheds";
    private static final int DATA_VERSION = 3;
    private static final int HARD_MAX_ENTRIES = 65_536;

    private static final String VERSION_KEY = "version";
    private static final String CHUNK_KEYS = "chunk_keys";
    private static final String BASIN_IDS = "basin_ids";
    private static final String TERRAIN = "terrain";
    private static final String HYDROLOGY = "hydrology";
    private static final String ENVIRONMENT = "environment";
    private static final String FLOW = "flow";
    private static final String CLIMATE = "climate";
    private static final String DRAINAGE_DIRECTIONS = "drainage_directions";
    private static final String DRAINAGE_ACCUMULATION = "drainage_accumulation";
    private static final String REPRESENTATIVES = "representatives";
    private static final String REVISIONS = "revisions";
    private static final String UPDATED = "updated";
    private static final String FLOOD_CURSORS = "flood_cursors";
    private static final String ACTIVE_FLOOD_CELLS = "active_flood_cells";
    private static final String ACTIVE_SURFACE_WATER_CELLS = "active_surface_water_cells";

    private final LinkedHashMap<Long, WatershedChunkState> states =
            new LinkedHashMap<>(256, 0.75f, true);

    /** Returns the authoritative dimension-owned watershed storage. */
    public static WatershedSavedData get(ServerLevel level) {
        WatershedSavedData data = level.getDataStorage().computeIfAbsent(
                new Factory<>(WatershedSavedData::new, WatershedSavedData::load),
                DATA_NAME
        );
        data.enforceBudget(WaterSimulationConfig.watershedMaxSavedChunks());
        return data;
    }

    static WatershedSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WatershedSavedData data = new WatershedSavedData();
        int version = tag == null ? 0 : tag.getInt(VERSION_KEY);
        if (version < 1 || version > DATA_VERSION) {
            return data;
        }
        long[] keys = tag.getLongArray(CHUNK_KEYS);
        long[] basins = tag.getLongArray(BASIN_IDS);
        long[] terrain = tag.getLongArray(TERRAIN);
        long[] hydrology = tag.getLongArray(HYDROLOGY);
        long[] environment = tag.getLongArray(ENVIRONMENT);
        long[] flow = tag.getLongArray(FLOW);
        long[] climate = tag.getLongArray(CLIMATE);
        long[] drainageDirections = tag.getLongArray(DRAINAGE_DIRECTIONS);
        long[] drainageAccumulation = tag.getLongArray(DRAINAGE_ACCUMULATION);
        long[] representatives = tag.getLongArray(REPRESENTATIVES);
        long[] revisions = tag.getLongArray(REVISIONS);
        long[] updated = tag.getLongArray(UPDATED);
        int[] cursors = tag.getIntArray(FLOOD_CURSORS);
        int[] activeFlood = tag.getIntArray(ACTIVE_FLOOD_CELLS);
        int[] activeSurfaceWater = tag.getIntArray(ACTIVE_SURFACE_WATER_CELLS);
        int count = minimumLength(
                keys.length,
                basins.length,
                terrain.length,
                hydrology.length,
                environment.length,
                flow.length,
                representatives.length,
                revisions.length,
                updated.length,
                cursors.length,
                activeFlood.length
        );
        for (int index = 0; index < count && data.states.size() < HARD_MAX_ENTRIES; index++) {
            WatershedChunkState.Packed packed = new WatershedChunkState.Packed(
                    basins[index],
                    terrain[index],
                    hydrology[index],
                    environment[index],
                    flow[index],
                    migratedClimate(version, index < climate.length ? climate[index] : 0L),
                    version >= 2 && index < drainageDirections.length
                            ? drainageDirections[index]
                            : migratedGrid(terrain[index]).directionBits(),
                    version >= 2 && index < drainageAccumulation.length
                            ? drainageAccumulation[index]
                            : migratedGrid(terrain[index]).accumulationBits(),
                    representatives[index],
                    revisions[index],
                    updated[index],
                    cursors[index],
                    activeFlood[index],
                    version >= 3 && index < activeSurfaceWater.length
                            ? activeSurfaceWater[index]
                            : 0
            );
            data.states.put(keys[index], WatershedChunkState.fromPacked(packed));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        int count = states.size();
        long[] keys = new long[count];
        long[] basins = new long[count];
        long[] terrain = new long[count];
        long[] hydrology = new long[count];
        long[] environment = new long[count];
        long[] flow = new long[count];
        long[] climate = new long[count];
        long[] drainageDirections = new long[count];
        long[] drainageAccumulation = new long[count];
        long[] representatives = new long[count];
        long[] revisions = new long[count];
        long[] updated = new long[count];
        int[] cursors = new int[count];
        int[] activeFlood = new int[count];
        int[] activeSurfaceWater = new int[count];
        int index = 0;
        for (Map.Entry<Long, WatershedChunkState> entry : states.entrySet()) {
            WatershedChunkState.Packed packed = entry.getValue().packed();
            keys[index] = entry.getKey();
            basins[index] = packed.basinId();
            terrain[index] = packed.terrainBits();
            hydrology[index] = packed.hydrologyBits();
            environment[index] = packed.environmentBits();
            flow[index] = packed.flowBits();
            climate[index] = packed.climateBits();
            drainageDirections[index] = packed.drainageDirectionBits();
            drainageAccumulation[index] = packed.drainageAccumulationBits();
            representatives[index] = packed.representativePosition();
            revisions[index] = packed.revision();
            updated[index] = packed.lastUpdatedTick();
            cursors[index] = packed.floodCursor();
            activeFlood[index] = packed.activeFloodCells();
            activeSurfaceWater[index] = packed.activeSurfaceWaterCells();
            index++;
        }
        tag.putInt(VERSION_KEY, DATA_VERSION);
        tag.putLongArray(CHUNK_KEYS, keys);
        tag.putLongArray(BASIN_IDS, basins);
        tag.putLongArray(TERRAIN, terrain);
        tag.putLongArray(HYDROLOGY, hydrology);
        tag.putLongArray(ENVIRONMENT, environment);
        tag.putLongArray(FLOW, flow);
        tag.putLongArray(CLIMATE, climate);
        tag.putLongArray(DRAINAGE_DIRECTIONS, drainageDirections);
        tag.putLongArray(DRAINAGE_ACCUMULATION, drainageAccumulation);
        tag.putLongArray(REPRESENTATIVES, representatives);
        tag.putLongArray(REVISIONS, revisions);
        tag.putLongArray(UPDATED, updated);
        tag.putIntArray(FLOOD_CURSORS, cursors);
        tag.putIntArray(ACTIVE_FLOOD_CELLS, activeFlood);
        tag.putIntArray(ACTIVE_SURFACE_WATER_CELLS, activeSurfaceWater);
        return tag;
    }

    /** Returns or deterministically initializes one already-loaded chunk cell. */
    public WatershedChunkState getOrCreate(ServerLevel level, LevelChunk chunk) {
        long key = chunk.getPos().toLong();
        WatershedChunkState existing = states.get(key);
        if (existing != null) {
            return existing;
        }
        WatershedChunkState created = WatershedTerrainInitializer.initialize(level, chunk);
        states.put(key, created);
        enforceBudget(WaterSimulationConfig.watershedMaxSavedChunks());
        setDirty();
        return created;
    }

    /** Returns an initialized state without creating or loading a chunk. */
    public WatershedChunkState state(long chunkKey) {
        return states.get(chunkKey);
    }

    // Package-private migration/test hook keeps save round trips independent of
    // live chunk construction while preserving the same bounded map format.
    void store(long chunkKey, WatershedChunkState state) {
        if (state == null) {
            return;
        }
        states.put(chunkKey, state);
        enforceBudget(HARD_MAX_ENTRIES);
        setDirty();
    }

    /** Returns an immutable condition view, or the dry fallback when absent. */
    public WatershedConditions conditions(int chunkX, int chunkZ) {
        WatershedChunkState state = states.get(ChunkPos.asLong(chunkX, chunkZ));
        return state == null ? WatershedConditions.NONE : state.conditions();
    }

    /** Marks the save after a state is changed by the simulation or flood ledger. */
    public void markChanged() {
        setDirty();
    }

    /** Returns the number of retained chunk-scale cells. */
    public int size() {
        return states.size();
    }

    /** Applies the configured LRU entry budget. */
    public void enforceBudget(int maximumEntries) {
        int maximum = Math.max(1, Math.min(HARD_MAX_ENTRIES, maximumEntries));
        boolean removed = false;
        while (states.size() > maximum) {
            Iterator<Long> iterator = states.keySet().iterator();
            iterator.next();
            iterator.remove();
            removed = true;
        }
        if (removed) {
            setDirty();
        }
    }

    private static int minimumLength(int... lengths) {
        int minimum = Integer.MAX_VALUE;
        for (int length : lengths) {
            minimum = Math.min(minimum, Math.max(0, length));
        }
        return minimum == Integer.MAX_VALUE ? 0 : minimum;
    }

    private static WatershedDrainageGrid migratedGrid(long terrainBits) {
        int directionId = (int) ((terrainBits >>> 16) & 0xFL);
        return WatershedDrainageGrid.uniform(WatershedConditions.DrainageDirection.fromId(directionId));
    }

    private static long migratedClimate(int version, long climateBits) {
        if (version >= 3) {
            return climateBits;
        }
        // Version two persisted snowmelt in word zero and left the remaining
        // climate words empty. Seed a modest water table so upgraded worlds do
        // not begin with hydrologically impossible globally empty aquifers.
        int initialStorage = Math.round(0.12f * 0xFFFF);
        return climateBits | (long) initialStorage << 32;
    }
}
