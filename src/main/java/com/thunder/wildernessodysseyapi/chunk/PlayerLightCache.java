package com.thunder.wildernessodysseyapi.chunk;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains last-sent light bands for a single player so deltas can be computed.
 */
final class PlayerLightCache {
    private final Map<ChunkPos, Map<LightLayer, Map<Integer, byte[]>>> cache = new ConcurrentHashMap<>();

    Map<Integer, byte[]> getLayer(ChunkPos pos, LightLayer layer) {
        return cache.getOrDefault(pos, Collections.emptyMap()).getOrDefault(layer, Collections.emptyMap());
    }

    void updateBand(ChunkPos pos, LightLayer layer, int sectionY, byte[] data) {
        ChunkPos key = new ChunkPos(pos.x, pos.z);
        Map<LightLayer, Map<Integer, byte[]>> perChunk = cache.computeIfAbsent(key, ignored -> new EnumMap<>(LightLayer.class));
        Map<Integer, byte[]> perLayer = perChunk.computeIfAbsent(layer, ignored -> new HashMap<>());
        perLayer.put(sectionY, data == null ? null : data.clone());
    }

    void seedFromSnapshot(ChunkPos pos, ChunkLightSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        ChunkPos key = new ChunkPos(pos.x, pos.z);
        Map<LightLayer, Map<Integer, byte[]>> perChunk = cache.computeIfAbsent(key, ignored -> new EnumMap<>(LightLayer.class));
        snapshot.bands().forEach((layer, sections) -> {
            Map<Integer, byte[]> perLayer = perChunk.computeIfAbsent(layer, ignored -> new HashMap<>());
            sections.forEach((section, bytes) -> perLayer.put(section, bytes == null ? null : bytes.clone()));
        });
    }

    void dropChunk(ChunkPos pos) {
        cache.remove(pos);
    }

    void clear() {
        cache.clear();
    }
}
