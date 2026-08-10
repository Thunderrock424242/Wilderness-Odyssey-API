package com.thunder.wildernessodysseyapi.structuregen.blueprint;

import com.thunder.wildernessodysseyapi.structuregen.model.StructureSize;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Parsed but untrusted Blueprint Format v1 document.
 *
 * <p>Keeping this separate from {@code StructureModel} prevents JSON parsing from
 * becoming an implicit authorization to write Minecraft NBT.</p>
 */
public record BlueprintDocument(
        Path source,
        int formatVersion,
        String name,
        StructureSize size,
        Integer dataVersion,
        Map<String, String> metadata,
        List<String> markers,
        List<BlueprintBlock> blocks,
        List<BlueprintEntity> entities,
        String rawRootSnbt
) {

    public BlueprintDocument {
        metadata = Collections.unmodifiableMap(new TreeMap<>(metadata));
        markers = List.copyOf(markers);
        blocks = List.copyOf(blocks);
        entities = List.copyOf(entities);
    }
}
