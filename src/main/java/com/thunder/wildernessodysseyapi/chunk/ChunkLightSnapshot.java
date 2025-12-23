package com.thunder.wildernessodysseyapi.chunk;

import net.minecraft.world.level.LightLayer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable snapshot of a chunk's light arrays keyed by section Y.
 */
public record ChunkLightSnapshot(Map<LightLayer, Map<Integer, byte[]>> bands) {

    public ChunkLightSnapshot {
        bands = deepCopy(bands);
    }

    public static ChunkLightSnapshot empty() {
        return new ChunkLightSnapshot(Collections.emptyMap());
    }

    public Map<Integer, byte[]> layer(LightLayer layer) {
        return bands.getOrDefault(layer, Collections.emptyMap());
    }

    private static Map<LightLayer, Map<Integer, byte[]>> deepCopy(Map<LightLayer, Map<Integer, byte[]>> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<LightLayer, Map<Integer, byte[]>> copy = new EnumMap<>(LightLayer.class);
        source.forEach((layer, sections) -> {
            Map<Integer, byte[]> sectionCopy = new HashMap<>();
            sections.forEach((sectionY, data) -> sectionCopy.put(sectionY, data == null ? null : data.clone()));
            copy.put(layer, sectionCopy);
        });
        return copy;
    }
}
