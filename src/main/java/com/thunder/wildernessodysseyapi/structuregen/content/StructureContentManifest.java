package com.thunder.wildernessodysseyapi.structuregen.content;

import java.util.List;

/**
 * Content policy and resolution provenance retained with a generated structure.
 *
 * <p>The manifest records what the authoring environment selected. It does not activate
 * any mod integration or gameplay system at structure placement time.</p>
 */
public record StructureContentManifest(
        int schemaVersion,
        boolean allowInstalledModBlocks,
        List<String> preferredDecorativeMods,
        List<String> requiredMods,
        List<String> enabledFunctionalSystems,
        List<ResolvedMaterial> resolvedMaterials,
        ContentManifestStatus provenanceStatus
) {

    /** Schema version emitted for manifests authored by this StructureGen release. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Sentinel retained when a present manifest did not provide a readable schema version. */
    public static final int UNKNOWN_SCHEMA_VERSION = 0;

    private static final StructureContentManifest DEFAULT = new StructureContentManifest(
            UNKNOWN_SCHEMA_VERSION, true, List.of(), List.of(), List.of(), List.of(),
            ContentManifestStatus.ABSENT
    );

    /** Creates a manifest whose policy came from a validated Blueprint authoring run. */
    public StructureContentManifest(
            boolean allowInstalledModBlocks,
            List<String> preferredDecorativeMods,
            List<String> requiredMods,
            List<String> enabledFunctionalSystems,
            List<ResolvedMaterial> resolvedMaterials
    ) {
        this(
                CURRENT_SCHEMA_VERSION, allowInstalledModBlocks, preferredDecorativeMods, requiredMods,
                enabledFunctionalSystems, resolvedMaterials, ContentManifestStatus.VERIFIED
        );
    }

    public StructureContentManifest {
        preferredDecorativeMods = List.copyOf(preferredDecorativeMods);
        requiredMods = List.copyOf(requiredMods);
        enabledFunctionalSystems = List.copyOf(enabledFunctionalSystems);
        resolvedMaterials = List.copyOf(resolvedMaterials);
        if (provenanceStatus == null) {
            throw new IllegalArgumentException("Content-manifest provenance status is required");
        }
    }

    /** Returns the backward-compatible policy used when no content fields were authored. */
    public static StructureContentManifest defaults() {
        return DEFAULT;
    }

    /** Returns an unreadable-but-present manifest with default policy values. */
    public static StructureContentManifest partialDefaults() {
        return new StructureContentManifest(
                UNKNOWN_SCHEMA_VERSION, true, List.of(), List.of(), List.of(), List.of(),
                ContentManifestStatus.PARTIAL
        );
    }

    /** Returns whether this value represents a structure with no stored content manifest. */
    public boolean isDefault() {
        return equals(DEFAULT);
    }
}
