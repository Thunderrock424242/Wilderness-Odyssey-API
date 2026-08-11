package com.thunder.wildernessodysseyapi.structuregen.blueprint;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * One untrusted concrete block-state candidate in a semantic material preference chain.
 *
 * <p>The parser guarantees only JSON shape. Registry existence and property values are checked
 * later against the available-content catalog.</p>
 *
 * @param blockId explicit namespaced block ID supplied by the Blueprint
 * @param properties candidate-specific block-state properties
 * @param requiresMod optional installed-mod gate and preference affinity; this is not a claim that
 *                    the mod owns the candidate's registry namespace
 */
public record BlueprintMaterialCandidate(
        String blockId,
        Map<String, String> properties,
        String requiresMod
) {

    public BlueprintMaterialCandidate {
        properties = Collections.unmodifiableMap(new TreeMap<>(properties));
    }

    /** Creates a candidate without an explicit installed-mod gate. */
    public BlueprintMaterialCandidate(String blockId, Map<String, String> properties) {
        this(blockId, properties, null);
    }
}
