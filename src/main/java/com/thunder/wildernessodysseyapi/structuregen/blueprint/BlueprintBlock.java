package com.thunder.wildernessodysseyapi.structuregen.blueprint;

import com.thunder.wildernessodysseyapi.structuregen.model.StructurePosition;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Untrusted, parsed Blueprint v1 block awaiting semantic and registry validation. */
public record BlueprintBlock(
        StructurePosition position,
        String blockId,
        Map<String, String> properties,
        String blockEntitySnbt,
        List<String> markers,
        String rawEntrySnbt
) {

    public BlueprintBlock {
        properties = Collections.unmodifiableMap(new TreeMap<>(properties));
        markers = List.copyOf(markers);
    }
}
