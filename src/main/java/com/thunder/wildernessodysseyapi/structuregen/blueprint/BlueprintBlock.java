package com.thunder.wildernessodysseyapi.structuregen.blueprint;

import com.thunder.wildernessodysseyapi.structuregen.model.StructurePosition;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Untrusted, parsed Blueprint v1 block awaiting semantic and registry validation.
 *
 * @param usageIntent optional literal-block classification; third-party literals must explicitly
 *                    choose {@code decorative} or {@code functional}
 * @param requiredSystem namespaced gameplay-system opt-in required by functional literals
 */
public record BlueprintBlock(
        StructurePosition position,
        String blockId,
        Map<String, String> properties,
        String blockEntitySnbt,
        List<String> markers,
        String rawEntrySnbt,
        String usageIntent,
        String requiredSystem
) {

    public BlueprintBlock {
        properties = Collections.unmodifiableMap(new TreeMap<>(properties));
        markers = List.copyOf(markers);
    }

    /**
     * Retains the original Blueprint v1 constructor for vanilla and semantic-role callers.
     *
     * <p>An omitted usage declaration is intentionally not interpreted as decorative. Validation
     * permits that legacy shape for vanilla literals, while third-party literals fail closed.</p>
     */
    public BlueprintBlock(
            StructurePosition position,
            String blockId,
            Map<String, String> properties,
            String blockEntitySnbt,
            List<String> markers,
            String rawEntrySnbt
    ) {
        this(position, blockId, properties, blockEntitySnbt, markers, rawEntrySnbt, null, null);
    }
}
