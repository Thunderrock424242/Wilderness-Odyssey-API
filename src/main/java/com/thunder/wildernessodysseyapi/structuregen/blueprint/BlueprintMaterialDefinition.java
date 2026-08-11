package com.thunder.wildernessodysseyapi.structuregen.blueprint;

import java.util.List;

/**
 * Parsed definition for a reusable semantic material role such as {@code industrial_detail}.
 *
 * <p>Preferred candidates and fallbacks retain their declared order because that order is part
 * of deterministic material resolution. The default intent is decorative; functional intent is
 * not authorized unless later validation also accepts the explicitly named system.</p>
 *
 * @param intent author-declared intent, defaulting to {@code decorative}
 * @param requiredSystem optional namespaced gameplay-system ID required by this role
 * @param preferred ordered preferred block-state candidates
 * @param fallbacks ordered fallback block-state candidates
 */
public record BlueprintMaterialDefinition(
        String intent,
        String requiredSystem,
        List<BlueprintMaterialCandidate> preferred,
        List<BlueprintMaterialCandidate> fallbacks
) {

    /** Backward-compatible default for a material definition that omits {@code intent}. */
    public static final String DEFAULT_INTENT = "decorative";

    public BlueprintMaterialDefinition {
        preferred = List.copyOf(preferred);
        fallbacks = List.copyOf(fallbacks);
    }
}
