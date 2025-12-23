package com.thunder.wildernessodysseyapi.chunk;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Deduplicates chunk section slices (biomes, noise/height data) while recording hashes
 * for downstream cache reuse. Uses a small interning pool backed by soft references
 * so redundant slices in vertically stacked sections can share the same backing array.
 */
final class ChunkSliceCache {
    private static final String HASHES_TAG = "wapi_slice_hashes";
    private static final String BIOME_HASHES = "biome";
    private static final String NOISE_HASHES = "noise";

    private final SliceInternPool biomeSlices;
    private final SliceInternPool noiseSlices;

    ChunkSliceCache(int maxEntries) {
        this.biomeSlices = new SliceInternPool(maxEntries);
        this.noiseSlices = new SliceInternPool(maxEntries);
    }

    CompoundTag dedupe(ChunkPos pos, CompoundTag payload) {
        Map<String, Long> biomeHashes = new HashMap<>();
        Map<String, Long> noiseHashes = new HashMap<>();

        if (payload.contains("sections", Tag.TAG_LIST)) {
            ListTag sections = payload.getList("sections", Tag.TAG_COMPOUND);
            for (int i = 0; i < sections.size(); i++) {
                CompoundTag section = sections.getCompound(i);
                int sectionY = section.getByte("Y");
                long hash = dedupeBiomeSlice(section, biomeHashes, sectionY);
                if (hash != 0L) {
                    ModConstants.LOGGER.trace("[ChunkStream] Cached biome slice hash {} for {} @ section {}", hash, pos, sectionY);
                }
            }
        }

        if (payload.contains("Heightmaps", Tag.TAG_COMPOUND)) {
            CompoundTag heightmaps = payload.getCompound("Heightmaps");
            Set<String> keys = heightmaps.getAllKeys();
            for (String key : keys) {
                if (!heightmaps.contains(key, Tag.TAG_LONG_ARRAY)) {
                    continue;
                }
                long[] data = heightmaps.getLongArray(key);
                if (data.length == 0) {
                    continue;
                }
                SliceFingerprint fp = SliceFingerprint.from(data);
                long[] interned = noiseSlices.intern(fp, data);
                heightmaps.putLongArray(key, interned);
                noiseHashes.put(key, fp.hash());
            }
        }

        if (!biomeHashes.isEmpty() || !noiseHashes.isEmpty()) {
            CompoundTag hashes = new CompoundTag();
            if (!biomeHashes.isEmpty()) {
                CompoundTag biome = new CompoundTag();
                biomeHashes.forEach(biome::putLong);
                hashes.put(BIOME_HASHES, biome);
            }
            if (!noiseHashes.isEmpty()) {
                CompoundTag noise = new CompoundTag();
                noiseHashes.forEach(noise::putLong);
                hashes.put(NOISE_HASHES, noise);
            }
            payload.put(HASHES_TAG, hashes);
        }

        return payload;
    }

    void reset() {
        biomeSlices.clear();
        noiseSlices.clear();
    }

    private long dedupeBiomeSlice(CompoundTag section, Map<String, Long> hashSink, int sectionY) {
        if (!section.contains("biomes", Tag.TAG_COMPOUND)) {
            return 0L;
        }
        CompoundTag biomes = section.getCompound("biomes");
        if (!biomes.contains("data", Tag.TAG_LONG_ARRAY)) {
            return 0L;
        }
        long[] data = biomes.getLongArray("data");
        if (data.length == 0) {
            return 0L;
        }
        SliceFingerprint fp = SliceFingerprint.from(data);
        long[] interned = biomeSlices.intern(fp, data);
        biomes.putLongArray("data", interned);
        hashSink.put(String.valueOf(sectionY), fp.hash());
        return fp.hash();
    }
}
