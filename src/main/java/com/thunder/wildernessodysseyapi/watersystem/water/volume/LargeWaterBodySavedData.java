package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists derived large-water-body column metadata for one dimension.
 *
 * <p>This is the safe per-world cache for ocean/lake/river body data. It uses
 * Minecraft's normal {@link SavedData} storage instead of an ad-hoc file, so it
 * follows dimension saves, backups, and server lifecycle rules. The cache is
 * only a performance hint; authority resamples a column whenever the terrain
 * hash or water-system version no longer matches.</p>
 */
public final class LargeWaterBodySavedData extends SavedData {

    private static final String DATA_NAME = ModConstants.MOD_ID + "_large_water_bodies";
    private static final String VERSION_KEY = "version";
    private static final String COLUMN_DATA_KEY = "columns";
    private static final int COLUMN_STRIDE = 18;

    private final LinkedHashMap<Long, StoredColumn> columns =
            new LinkedHashMap<>(1024, 0.75f, true);

    /** Returns the dimension-owned large-body cache. */
    public static LargeWaterBodySavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(LargeWaterBodySavedData::new, LargeWaterBodySavedData::load),
                DATA_NAME
        );
    }

    private static LargeWaterBodySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        LargeWaterBodySavedData data = new LargeWaterBodySavedData();
        int version = tag.getInt(VERSION_KEY);
        if (version != WildernessWaterAuthority.CURRENT_WATER_SYSTEM_VERSION) {
            return data;
        }

        int[] encoded = tag.getIntArray(COLUMN_DATA_KEY);
        int entries = encoded.length / COLUMN_STRIDE;
        int maxEntries = Math.max(1, WaterSimulationConfig.largeBodyCacheMaxColumns());
        for (int index = 0; index < entries && data.columns.size() < maxEntries; index++) {
            int offset = index * COLUMN_STRIDE;
            long key = unpackLong(encoded[offset], encoded[offset + 1]);
            StoredColumn column = new StoredColumn(
                    encoded[offset + 2],
                    encoded[offset + 3],
                    encoded[offset + 4],
                    encoded[offset + 5],
                    encoded[offset + 6],
                    encoded[offset + 7],
                    encoded[offset + 8],
                    Float.intBitsToFloat(encoded[offset + 9]),
                    Float.intBitsToFloat(encoded[offset + 10]),
                    encoded[offset + 11],
                    Float.intBitsToFloat(encoded[offset + 12]),
                    unpackLong(encoded[offset + 13], encoded[offset + 14]),
                    encoded[offset + 15] != 0,
                    waterType(encoded[offset + 16]),
                    encoded[offset + 17]
            );
            data.columns.put(key, column);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        trimToBudget();
        int[] encoded = new int[columns.size() * COLUMN_STRIDE];
        int index = 0;
        for (Map.Entry<Long, StoredColumn> entry : columns.entrySet()) {
            StoredColumn column = entry.getValue();
            int offset = index++ * COLUMN_STRIDE;
            encoded[offset] = (int) (entry.getKey() & 0xFFFFFFFFL);
            encoded[offset + 1] = (int) (entry.getKey() >>> 32);
            encoded[offset + 2] = column.chunkX;
            encoded[offset + 3] = column.chunkZ;
            encoded[offset + 4] = column.minX;
            encoded[offset + 5] = column.maxX;
            encoded[offset + 6] = column.minZ;
            encoded[offset + 7] = column.maxZ;
            encoded[offset + 8] = column.surfaceBlockY;
            encoded[offset + 9] = Float.floatToIntBits(column.baseSurfaceFill);
            encoded[offset + 10] = Float.floatToIntBits(column.baseSurfaceHeight);
            encoded[offset + 11] = column.floorY;
            encoded[offset + 12] = Float.floatToIntBits(column.depth);
            encoded[offset + 13] = (int) (column.estimatedVolumeUnits & 0xFFFFFFFFL);
            encoded[offset + 14] = (int) (column.estimatedVolumeUnits >>> 32);
            encoded[offset + 15] = column.shoreline ? 1 : 0;
            encoded[offset + 16] = column.waterType.ordinal();
            encoded[offset + 17] = column.terrainHash;
        }
        tag.putInt(VERSION_KEY, WildernessWaterAuthority.CURRENT_WATER_SYSTEM_VERSION);
        tag.putIntArray(COLUMN_DATA_KEY, encoded);
        return tag;
    }

    /** Returns a current cached column or {@code null} when the cache is stale. */
    HybridWaterBodyModel.SurfaceColumn getColumn(long key, int terrainHash) {
        StoredColumn stored = columns.get(key);
        if (stored == null || stored.terrainHash != terrainHash) {
            return null;
        }
        return stored.toSurfaceColumn();
    }

    /** Stores or refreshes one derived large-body column. */
    void putColumn(long key, int terrainHash, HybridWaterBodyModel.SurfaceColumn column) {
        if (!column.valid()) {
            return;
        }
        columns.put(key, StoredColumn.from(column, terrainHash));
        trimToBudget();
        setDirty();
    }

    /** Returns the number of currently cached large-body columns for diagnostics. */
    public int cachedColumnCount() {
        return columns.size();
    }

    private void trimToBudget() {
        int maxEntries = Math.max(1, WaterSimulationConfig.largeBodyCacheMaxColumns());
        while (columns.size() > maxEntries) {
            Long eldest = columns.keySet().iterator().next();
            columns.remove(eldest);
        }
    }

    private static long unpackLong(int low, int high) {
        return (low & 0xFFFFFFFFL) | ((long) high << 32);
    }

    private static WaterBodyClassifier.WaterType waterType(int ordinal) {
        WaterBodyClassifier.WaterType[] values = WaterBodyClassifier.WaterType.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return WaterBodyClassifier.WaterType.POND;
        }
        return values[ordinal];
    }

    private record StoredColumn(
            int chunkX,
            int chunkZ,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int surfaceBlockY,
            float baseSurfaceFill,
            float baseSurfaceHeight,
            int floorY,
            float depth,
            long estimatedVolumeUnits,
            boolean shoreline,
            WaterBodyClassifier.WaterType waterType,
            int terrainHash
    ) {
        static StoredColumn from(HybridWaterBodyModel.SurfaceColumn column, int terrainHash) {
            return new StoredColumn(
                    column.chunkX(),
                    column.chunkZ(),
                    column.minX(),
                    column.maxX(),
                    column.minZ(),
                    column.maxZ(),
                    column.surfaceBlockY(),
                    column.baseSurfaceFill(),
                    column.baseSurfaceHeight(),
                    column.floorY(),
                    column.depth(),
                    column.estimatedVolumeUnits(),
                    column.shoreline(),
                    column.waterType(),
                    terrainHash
            );
        }

        HybridWaterBodyModel.SurfaceColumn toSurfaceColumn() {
            return new HybridWaterBodyModel.SurfaceColumn(
                    true,
                    chunkX,
                    chunkZ,
                    minX,
                    maxX,
                    minZ,
                    maxZ,
                    surfaceBlockY,
                    baseSurfaceFill,
                    baseSurfaceHeight,
                    floorY,
                    depth,
                    estimatedVolumeUnits,
                    shoreline,
                    waterType
            );
        }
    }
}
