package com.thunder.wildernessodysseyapi.structuregen.model;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Registry identifier and canonical property map for one palette state.
 *
 * @param blockId Minecraft resource location
 * @param properties sorted block-state properties
 * @param rawPaletteEntrySnbt original palette compound for lossless unknown-field preservation
 */
public record StructureBlockState(
        String blockId,
        Map<String, String> properties,
        String rawPaletteEntrySnbt
) {

    public StructureBlockState {
        properties = Collections.unmodifiableMap(new TreeMap<>(properties));
    }

    /** Creates an authored state without imported raw palette data. */
    public StructureBlockState(String blockId, Map<String, String> properties) {
        this(blockId, properties, null);
    }

    /** Stable identity used for deterministic palettes and semantic comparisons. */
    public String canonicalKey() {
        if (properties.isEmpty()) {
            return blockId;
        }
        return blockId + "[" + properties.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",")) + "]";
    }
}
