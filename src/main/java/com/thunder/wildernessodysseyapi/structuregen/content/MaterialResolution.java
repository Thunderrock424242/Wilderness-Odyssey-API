package com.thunder.wildernessodysseyapi.structuregen.content;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Concrete role selections and the manifest produced by one deterministic resolution pass. */
public record MaterialResolution(
        Map<String, ResolvedMaterial> materials,
        StructureContentManifest manifest
) {

    public MaterialResolution {
        materials = Collections.unmodifiableMap(new TreeMap<>(materials));
    }
}
