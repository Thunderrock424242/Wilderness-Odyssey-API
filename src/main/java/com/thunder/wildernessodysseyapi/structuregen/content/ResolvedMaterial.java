package com.thunder.wildernessodysseyapi.structuregen.content;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Auditable result of resolving one semantic material role to a concrete registered block state.
 */
public record ResolvedMaterial(
        String role,
        String intent,
        String selectedBlock,
        Map<String, String> properties,
        String sourceNamespace,
        String source,
        boolean fallbackAvailable,
        List<RejectedMaterialCandidate> rejectedCandidates
) {

    public ResolvedMaterial {
        properties = Collections.unmodifiableMap(new TreeMap<>(properties));
        rejectedCandidates = List.copyOf(rejectedCandidates);
    }
}
