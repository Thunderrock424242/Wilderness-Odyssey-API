package com.thunder.wildernessodysseyapi.structuregen.content;

/** One unavailable or policy-rejected candidate considered during deterministic material resolution. */
public record RejectedMaterialCandidate(String blockId, String reason) {
}
