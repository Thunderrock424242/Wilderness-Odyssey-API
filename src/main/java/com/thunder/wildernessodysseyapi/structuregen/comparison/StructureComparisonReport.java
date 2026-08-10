package com.thunder.wildernessodysseyapi.structuregen.comparison;

import java.util.List;

/**
 * Semantic comparison totals for two canonical structures.
 *
 * <p>Palette indices, tag order, and compressed bytes are intentionally excluded.</p>
 */
public record StructureComparisonReport(
        boolean namesMatch,
        boolean dimensionsMatch,
        boolean dataVersionsMatch,
        boolean structureMetadataMatches,
        boolean sourcePalettesMatch,
        boolean preservedRootTagsMatch,
        int matchingBlocks,
        int missingBlocks,
        int addedBlocks,
        int changedBlockIds,
        int changedBlockStates,
        int changedBlockEntities,
        int changedBlockMarkers,
        int changedBlockEntryTags,
        int matchingEntities,
        int missingEntities,
        int addedEntities,
        List<String> details
) {

    public StructureComparisonReport {
        details = List.copyOf(details);
    }

    /** Returns whether every supported semantic field matches. */
    public boolean semanticallyMatches() {
        return namesMatch
                && dimensionsMatch
                && dataVersionsMatch
                && structureMetadataMatches
                && sourcePalettesMatch
                && preservedRootTagsMatch
                && missingBlocks == 0
                && addedBlocks == 0
                && changedBlockIds == 0
                && changedBlockStates == 0
                && changedBlockEntities == 0
                && changedBlockMarkers == 0
                && changedBlockEntryTags == 0
                && missingEntities == 0
                && addedEntities == 0;
    }
}
