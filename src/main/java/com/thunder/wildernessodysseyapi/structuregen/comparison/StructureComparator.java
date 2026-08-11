package com.thunder.wildernessodysseyapi.structuregen.comparison;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlock;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlockState;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureEntity;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureModel;
import com.thunder.wildernessodysseyapi.structuregen.model.StructurePosition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compares canonical structures without relying on palette ordering or compressed bytes.
 */
public final class StructureComparator {

    private static final int MAX_DETAILS = 100;

    /** Performs a supported-field semantic comparison. */
    public StructureComparisonReport compare(StructureModel expected, StructureModel actual) {
        List<String> details = new ArrayList<>();
        boolean namesMatch = Objects.equals(expected.name(), actual.name());
        boolean dimensionsMatch = Objects.equals(expected.size(), actual.size());
        boolean dataVersionsMatch = expected.dataVersion() == actual.dataVersion();
        boolean metadataMatches = Objects.equals(expected.metadata(), actual.metadata())
                && Objects.equals(expected.markers(), actual.markers())
                && Objects.equals(expected.contentManifest(), actual.contentManifest());
        boolean palettesMatch = palettesEqual(expected.sourcePalettes(), actual.sourcePalettes());
        boolean rawRootMatches = rawNbtEqual(expected.rawRootSnbt(), actual.rawRootSnbt());

        if (!namesMatch) {
            addDetail(details, "Name differs: expected " + expected.name() + ", actual " + actual.name() + ".");
        }
        if (!dimensionsMatch) {
            addDetail(details, "Dimensions differ: expected " + expected.size().display()
                    + ", actual " + actual.size().display() + ".");
        }
        if (!dataVersionsMatch) {
            addDetail(details, "DataVersion differs: expected " + expected.dataVersion()
                    + ", actual " + actual.dataVersion() + ".");
        }
        if (!metadataMatches) {
            addDetail(details, "StructureGen metadata, markers, or content manifest differ.");
        }
        if (!palettesMatch) {
            addDetail(details, "Imported source palettes differ.");
        }
        if (!rawRootMatches) {
            addDetail(details, "Preserved unknown root NBT differs.");
        }

        BlockCounts blockCounts = compareBlocks(expected.blocks(), actual.blocks(), details);
        EntityCounts entityCounts = compareEntities(expected.entities(), actual.entities(), details);
        return new StructureComparisonReport(
                namesMatch,
                dimensionsMatch,
                dataVersionsMatch,
                metadataMatches,
                palettesMatch,
                rawRootMatches,
                blockCounts.matching,
                blockCounts.missing,
                blockCounts.added,
                blockCounts.changedIds,
                blockCounts.changedStates,
                blockCounts.changedBlockEntities,
                blockCounts.changedMarkers,
                blockCounts.changedEntryTags,
                entityCounts.matching,
                entityCounts.missing,
                entityCounts.added,
                details
        );
    }

    /** Formats a concise developer-facing comparison report. */
    public String format(StructureComparisonReport report) {
        StringBuilder output = new StringBuilder("## Structure Comparison\n\n");
        output.append("Semantic result: ").append(report.semanticallyMatches() ? "MATCH" : "DIFFERENT").append('\n');
        output.append("Dimensions: ").append(report.dimensionsMatch() ? "MATCH" : "DIFFERENT").append('\n');
        output.append("DataVersion: ").append(report.dataVersionsMatch() ? "MATCH" : "DIFFERENT").append("\n\n");
        output.append("Blocks:\n");
        output.append("  Matching: ").append(report.matchingBlocks()).append('\n');
        output.append("  Missing: ").append(report.missingBlocks()).append('\n');
        output.append("  Added: ").append(report.addedBlocks()).append('\n');
        output.append("  Changed IDs: ").append(report.changedBlockIds()).append('\n');
        output.append("  Changed states: ").append(report.changedBlockStates()).append('\n');
        output.append("  Changed block entities: ").append(report.changedBlockEntities()).append('\n');
        output.append("  Changed StructureGen markers: ").append(report.changedBlockMarkers()).append('\n');
        output.append("  Changed preserved entry tags: ").append(report.changedBlockEntryTags()).append("\n\n");
        output.append("Entities:\n");
        output.append("  Matching: ").append(report.matchingEntities()).append('\n');
        output.append("  Missing: ").append(report.missingEntities()).append('\n');
        output.append("  Added: ").append(report.addedEntities()).append('\n');
        if (!report.details().isEmpty()) {
            output.append("\nDetails:\n");
            report.details().forEach(detail -> output.append("  - ").append(detail).append('\n'));
        }
        return output.toString();
    }

    private BlockCounts compareBlocks(
            List<StructureBlock> expected,
            List<StructureBlock> actual,
            List<String> details
    ) {
        // Sorting reference arrays is substantially more memory-efficient than
        // constructing two multi-million-entry maps for the bunker fixture.
        List<StructureBlock> expectedSorted = expected.stream()
                .sorted(Comparator.comparing(StructureBlock::position))
                .toList();
        List<StructureBlock> actualSorted = actual.stream()
                .sorted(Comparator.comparing(StructureBlock::position))
                .toList();
        requireUniquePositions(expectedSorted, "expected");
        requireUniquePositions(actualSorted, "actual");
        BlockCounts counts = new BlockCounts();

        int expectedIndex = 0;
        int actualIndex = 0;
        while (expectedIndex < expectedSorted.size() && actualIndex < actualSorted.size()) {
            StructureBlock expectedBlock = expectedSorted.get(expectedIndex);
            StructureBlock actualBlock = actualSorted.get(actualIndex);
            int positionOrder = expectedBlock.position().compareTo(actualBlock.position());
            if (positionOrder < 0) {
                counts.missing++;
                addDetail(details, "Missing block at " + expectedBlock.position().display() + ".");
                expectedIndex++;
                continue;
            }
            if (positionOrder > 0) {
                counts.added++;
                addDetail(details, "Additional block at " + actualBlock.position().display() + ".");
                actualIndex++;
                continue;
            }
            boolean blockMatches = true;
            if (!expectedBlock.state().blockId().equals(actualBlock.state().blockId())) {
                counts.changedIds++;
                blockMatches = false;
                addDetail(details, "Block ID changed at " + expectedBlock.position().display() + ": "
                        + expectedBlock.state().blockId() + " -> " + actualBlock.state().blockId() + ".");
            }
            if (!expectedBlock.state().properties().equals(actualBlock.state().properties())) {
                counts.changedStates++;
                blockMatches = false;
                addDetail(details, "Block state changed at " + expectedBlock.position().display() + ".");
            }
            if (!nbtEqual(expectedBlock.blockEntitySnbt(), actualBlock.blockEntitySnbt())) {
                counts.changedBlockEntities++;
                blockMatches = false;
                addDetail(details, "Block entity changed at " + expectedBlock.position().display() + ".");
            }
            if (!expectedBlock.markers().equals(actualBlock.markers())) {
                counts.changedMarkers++;
                blockMatches = false;
                addDetail(details, "StructureGen block markers changed at "
                        + expectedBlock.position().display() + ".");
            }
            if (!rawNbtEqual(expectedBlock.rawEntrySnbt(), actualBlock.rawEntrySnbt())) {
                counts.changedEntryTags++;
                blockMatches = false;
                addDetail(details, "Preserved block-entry NBT changed at "
                        + expectedBlock.position().display() + ".");
            }
            if (blockMatches) {
                counts.matching++;
            }
            expectedIndex++;
            actualIndex++;
        }
        while (expectedIndex < expectedSorted.size()) {
            StructurePosition position = expectedSorted.get(expectedIndex++).position();
            counts.missing++;
            addDetail(details, "Missing block at " + position.display() + ".");
        }
        while (actualIndex < actualSorted.size()) {
            StructurePosition position = actualSorted.get(actualIndex++).position();
            counts.added++;
            addDetail(details, "Additional block at " + position.display() + ".");
        }
        return counts;
    }

    private EntityCounts compareEntities(
            List<StructureEntity> expected,
            List<StructureEntity> actual,
            List<String> details
    ) {
        Map<EntityKey, Integer> expectedCounts = entityMultiset(expected);
        Map<EntityKey, Integer> actualCounts = entityMultiset(actual);
        EntityCounts counts = new EntityCounts();
        for (Map.Entry<EntityKey, Integer> entry : expectedCounts.entrySet()) {
            int actualCount = actualCounts.getOrDefault(entry.getKey(), 0);
            counts.matching += Math.min(entry.getValue(), actualCount);
            if (entry.getValue() > actualCount) {
                counts.missing += entry.getValue() - actualCount;
            }
        }
        for (Map.Entry<EntityKey, Integer> entry : actualCounts.entrySet()) {
            int expectedCount = expectedCounts.getOrDefault(entry.getKey(), 0);
            if (entry.getValue() > expectedCount) {
                counts.added += entry.getValue() - expectedCount;
            }
        }
        if (counts.missing > 0 || counts.added > 0) {
            addDetail(details, "Entity multiset differs: " + counts.missing + " missing, " + counts.added + " added.");
        }
        return counts;
    }

    private void requireUniquePositions(List<StructureBlock> blocks, String side) {
        for (int index = 1; index < blocks.size(); index++) {
            StructurePosition previous = blocks.get(index - 1).position();
            StructurePosition current = blocks.get(index).position();
            if (previous.equals(current)) {
                throw new IllegalArgumentException("Duplicate " + side + " block position " + current.display());
            }
        }
    }

    private Map<EntityKey, Integer> entityMultiset(List<StructureEntity> entities) {
        Map<EntityKey, Integer> counts = new HashMap<>();
        for (StructureEntity entity : entities) {
            EntityKey key = new EntityKey(
                    entity.position(),
                    entity.blockPosition(),
                    nbtKey(entity.entityNbtSnbt()),
                    rawNbtKey(entity.rawEntrySnbt())
            );
            counts.merge(key, 1, Integer::sum);
        }
        return counts;
    }

    private boolean palettesEqual(
            List<List<StructureBlockState>> expected,
            List<List<StructureBlockState>> actual
    ) {
        // Authored models intentionally leave palette construction to the writer.
        // Compare source palette semantics only when both sides came from NBT.
        if (expected.isEmpty() || actual.isEmpty()) {
            return true;
        }
        if (expected.size() != actual.size()) {
            return false;
        }
        int entryCount = expected.getFirst().size();
        for (int palette = 0; palette < expected.size(); palette++) {
            if (expected.get(palette).size() != entryCount || actual.get(palette).size() != entryCount) {
                return false;
            }
        }

        // A shared state-index permutation across every palette does not change
        // the structure. Match complete palette columns so alternate-palette
        // correlations remain intact while numeric indices are ignored.
        boolean[] matchedActualColumns = new boolean[entryCount];
        for (int expectedEntry = 0; expectedEntry < entryCount; expectedEntry++) {
            boolean matched = false;
            for (int actualEntry = 0; actualEntry < entryCount; actualEntry++) {
                if (!matchedActualColumns[actualEntry]
                        && paletteColumnsEqual(expected, expectedEntry, actual, actualEntry)) {
                    matchedActualColumns[actualEntry] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private boolean paletteColumnsEqual(
            List<List<StructureBlockState>> expected,
            int expectedEntry,
            List<List<StructureBlockState>> actual,
            int actualEntry
    ) {
        for (int palette = 0; palette < expected.size(); palette++) {
            StructureBlockState expectedState = expected.get(palette).get(expectedEntry);
            StructureBlockState actualState = actual.get(palette).get(actualEntry);
            if (!expectedState.canonicalKey().equals(actualState.canonicalKey())
                    || !rawNbtEqual(expectedState.rawPaletteEntrySnbt(), actualState.rawPaletteEntrySnbt())) {
                return false;
            }
        }
        return true;
    }

    private boolean nbtEqual(String first, String second) {
        if (first == null || second == null) {
            return first == null && second == null;
        }
        try {
            CompoundTag firstTag = TagParser.parseTag(first);
            CompoundTag secondTag = TagParser.parseTag(second);
            return firstTag.equals(secondTag);
        } catch (CommandSyntaxException exception) {
            return first.equals(second);
        }
    }

    private boolean rawNbtEqual(String first, String second) {
        return Objects.equals(rawNbtKey(first), rawNbtKey(second));
    }

    private Object nbtKey(String snbt) {
        if (snbt == null) {
            return NullNbt.INSTANCE;
        }
        try {
            return TagParser.parseTag(snbt);
        } catch (CommandSyntaxException exception) {
            return snbt;
        }
    }

    // Raw extension compounds contain only unknown fields. An omitted compound
    // and an explicitly empty compound carry the same information after a
    // writer/reader pass, unlike canonical block-entity or entity payload NBT.
    private Object rawNbtKey(String snbt) {
        if (snbt == null) {
            return EmptyRawNbt.INSTANCE;
        }
        try {
            CompoundTag parsed = TagParser.parseTag(snbt);
            return parsed.isEmpty() ? EmptyRawNbt.INSTANCE : parsed;
        } catch (CommandSyntaxException exception) {
            return snbt;
        }
    }

    private void addDetail(List<String> details, String detail) {
        if (details.size() < MAX_DETAILS) {
            details.add(detail);
        } else if (details.size() == MAX_DETAILS) {
            details.add("Additional differences omitted.");
        }
    }

    private static final class BlockCounts {
        private int matching;
        private int missing;
        private int added;
        private int changedIds;
        private int changedStates;
        private int changedBlockEntities;
        private int changedMarkers;
        private int changedEntryTags;
    }

    private static final class EntityCounts {
        private int matching;
        private int missing;
        private int added;
    }

    private record EntityKey(
            List<Double> position,
            StructurePosition blockPosition,
            Object entityNbt,
            Object rawEntryNbt
    ) {
    }

    private enum NullNbt {
        INSTANCE
    }

    private enum EmptyRawNbt {
        INSTANCE
    }
}
