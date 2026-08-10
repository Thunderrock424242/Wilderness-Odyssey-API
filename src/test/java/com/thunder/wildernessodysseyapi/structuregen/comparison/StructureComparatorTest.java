package com.thunder.wildernessodysseyapi.structuregen.comparison;

import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlock;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlockState;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureEntity;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureModel;
import com.thunder.wildernessodysseyapi.structuregen.model.StructurePosition;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureSize;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers supported-field semantic comparison without relying on list or NBT tag order. */
class StructureComparatorTest {

    private final StructureComparator comparator = new StructureComparator();

    @Test
    void treatsReorderedBlocksEntitiesAndCompoundTagsAsSemanticMatch() {
        StructureBlock stone = block(0, 0, 0, "minecraft:stone", Map.of(),
                "{id:'minecraft:chest',CustomName:'fixture'}");
        StructureBlock stairs = block(1, 0, 0, "minecraft:oak_stairs",
                Map.of("half", "bottom", "facing", "north"), null);
        StructureEntity armorStand = entity(0.5D, 1.0D, 0.5D, 0, 1, 0,
                "{id:'minecraft:armor_stand',Invisible:1b}");
        StructureEntity item = entity(1.5D, 1.0D, 0.5D, 1, 1, 0,
                "{id:'minecraft:item',Age:0s}");

        StructureModel expected = model(
                List.of(stone, stairs),
                List.of(armorStand, item),
                "{fixture:1b,nested:{value:2}}"
        );
        StructureModel actual = model(
                List.of(
                        stairs,
                        block(0, 0, 0, "minecraft:stone", Map.of(),
                                "{CustomName:'fixture',id:'minecraft:chest'}")
                ),
                List.of(
                        entity(1.5D, 1.0D, 0.5D, 1, 1, 0, "{Age:0s,id:'minecraft:item'}"),
                        entity(0.5D, 1.0D, 0.5D, 0, 1, 0,
                                "{Invisible:1b,id:'minecraft:armor_stand'}")
                ),
                "{nested:{value:2},fixture:1b}"
        );

        StructureComparisonReport report = comparator.compare(expected, actual);

        assertTrue(report.semanticallyMatches());
        assertEquals(2, report.matchingBlocks());
        assertEquals(2, report.matchingEntities());
        assertTrue(report.details().isEmpty());
    }

    @Test
    void countsEachSupportedBlockAndEntityDifference() {
        StructureModel expected = model(
                List.of(
                        block(0, 0, 0, "minecraft:stone", Map.of(), null),
                        block(1, 0, 0, "minecraft:oak_stairs", Map.of("facing", "north"), null),
                        block(2, 0, 0, "minecraft:chest", Map.of(),
                                "{id:'minecraft:chest',LootTable:'fixture:a'}"),
                        block(3, 0, 0, "minecraft:gold_block", Map.of(), null),
                        block(4, 0, 0, "minecraft:air", Map.of(), null)
                ),
                List.of(
                        entity(0.5D, 1.0D, 0.5D, 0, 1, 0, "{id:'minecraft:zombie'}"),
                        entity(1.5D, 1.0D, 0.5D, 1, 1, 0, "{id:'minecraft:cow'}")
                ),
                null
        );
        StructureModel actual = model(
                List.of(
                        block(0, 0, 0, "minecraft:dirt", Map.of(), null),
                        block(1, 0, 0, "minecraft:oak_stairs", Map.of("facing", "south"), null),
                        block(2, 0, 0, "minecraft:chest", Map.of(),
                                "{id:'minecraft:chest',LootTable:'fixture:b'}"),
                        block(4, 0, 0, "minecraft:air", Map.of(), null),
                        block(5, 0, 0, "minecraft:diamond_block", Map.of(), null)
                ),
                List.of(
                        entity(0.5D, 1.0D, 0.5D, 0, 1, 0, "{id:'minecraft:zombie'}"),
                        entity(2.5D, 1.0D, 0.5D, 2, 1, 0, "{id:'minecraft:armor_stand'}")
                ),
                null
        );

        StructureComparisonReport report = comparator.compare(expected, actual);

        assertFalse(report.semanticallyMatches());
        assertEquals(1, report.matchingBlocks());
        assertEquals(1, report.missingBlocks());
        assertEquals(1, report.addedBlocks());
        assertEquals(1, report.changedBlockIds());
        assertEquals(1, report.changedBlockStates());
        assertEquals(1, report.changedBlockEntities());
        assertEquals(1, report.matchingEntities());
        assertEquals(1, report.missingEntities());
        assertEquals(1, report.addedEntities());
        assertTrue(report.details().stream().anyMatch(detail -> detail.contains("Block ID changed")));
        assertTrue(report.details().stream().anyMatch(detail -> detail.contains("Entity multiset differs")));
    }

    @Test
    void detectsStructureGenMarkersAndPreservedUnknownEntryTags() {
        StructurePosition position = new StructurePosition(0, 0, 0);
        StructureBlockState state = new StructureBlockState("minecraft:stone", Map.of());
        StructureBlock expectedBlock = new StructureBlock(
                position, state, null, List.of("reference"), "{futureField:1b}", -1
        );
        StructureBlock actualBlock = new StructureBlock(
                position, state, null, List.of("generated"), "{futureField:2b}", -1
        );

        StructureComparisonReport markerReport = comparator.compare(
                model(List.of(expectedBlock), List.of(), null),
                model(List.of(actualBlock), List.of(), null)
        );
        assertFalse(markerReport.semanticallyMatches());
        assertEquals(1, markerReport.changedBlockMarkers());

        StructureBlock matchingMarkers = new StructureBlock(
                position, state, null, List.of("reference"), "{futureField:2b}", -1
        );
        StructureComparisonReport rawTagReport = comparator.compare(
                model(List.of(expectedBlock), List.of(), null),
                model(List.of(matchingMarkers), List.of(), null)
        );
        assertFalse(rawTagReport.semanticallyMatches());
        assertEquals(1, rawTagReport.changedBlockEntryTags());
    }

    @Test
    void ignoresSharedPaletteIndexPermutationButPreservesPaletteColumns() {
        StructureBlockState stone = new StructureBlockState("minecraft:stone", Map.of());
        StructureBlockState dirt = new StructureBlockState("minecraft:dirt", Map.of());
        StructureBlockState gold = new StructureBlockState("minecraft:gold_block", Map.of());
        StructureBlockState diamond = new StructureBlockState("minecraft:diamond_block", Map.of());
        StructureBlock block = new StructureBlock(
                new StructurePosition(0, 0, 0), stone, null, List.of(), null, 0
        );
        StructureModel expected = modelWithPalettes(
                block,
                List.of(List.of(stone, dirt), List.of(gold, diamond))
        );
        StructureModel permuted = modelWithPalettes(
                new StructureBlock(new StructurePosition(0, 0, 0), stone, null, List.of(), null, 1),
                List.of(List.of(dirt, stone), List.of(diamond, gold))
        );

        assertTrue(comparator.compare(expected, permuted).semanticallyMatches());

        StructureModel brokenColumn = modelWithPalettes(
                block,
                List.of(List.of(stone, dirt), List.of(diamond, gold))
        );
        assertFalse(comparator.compare(expected, brokenColumn).sourcePalettesMatch());
    }

    @Test
    void treatsEmptyRawExtensionCompoundsAsEquivalentToOmission() {
        StructurePosition blockPosition = new StructurePosition(0, 0, 0);
        StructureBlockState state = new StructureBlockState("minecraft:stone", Map.of());
        StructureBlock emptyRawBlock = new StructureBlock(
                blockPosition, state, null, List.of(), "{ }", -1
        );
        StructureBlock absentRawBlock = new StructureBlock(
                blockPosition, state, null, List.of(), null, -1
        );
        StructureEntity emptyRawEntity = new StructureEntity(
                List.of(0.5D, 1.0D, 0.5D),
                new StructurePosition(0, 1, 0),
                "{id:'minecraft:armor_stand'}",
                "{}"
        );
        StructureEntity absentRawEntity = new StructureEntity(
                List.of(0.5D, 1.0D, 0.5D),
                new StructurePosition(0, 1, 0),
                "{id:'minecraft:armor_stand'}",
                null
        );

        StructureComparisonReport report = comparator.compare(
                model(List.of(emptyRawBlock), List.of(emptyRawEntity), "{}"),
                model(List.of(absentRawBlock), List.of(absentRawEntity), null)
        );

        assertTrue(report.semanticallyMatches());
        assertEquals(1, report.matchingBlocks());
        assertEquals(1, report.matchingEntities());
    }

    private StructureModel model(
            List<StructureBlock> blocks,
            List<StructureEntity> entities,
            String rawRootSnbt
    ) {
        return new StructureModel(
                "comparison_fixture",
                new StructureSize(8, 4, 4),
                blocks,
                entities,
                3955,
                Map.of("fixture", "true"),
                List.of("test"),
                List.of(),
                rawRootSnbt,
                List.of()
        );
    }

    private StructureModel modelWithPalettes(
            StructureBlock block,
            List<List<StructureBlockState>> palettes
    ) {
        return new StructureModel(
                "comparison_fixture",
                new StructureSize(8, 4, 4),
                List.of(block),
                List.of(),
                3955,
                Map.of("fixture", "true"),
                List.of("test"),
                palettes,
                null,
                List.of()
        );
    }

    private StructureBlock block(
            int x,
            int y,
            int z,
            String blockId,
            Map<String, String> properties,
            String blockEntitySnbt
    ) {
        return new StructureBlock(
                new StructurePosition(x, y, z),
                new StructureBlockState(blockId, properties),
                blockEntitySnbt,
                List.of()
        );
    }

    private StructureEntity entity(
            double x,
            double y,
            double z,
            int blockX,
            int blockY,
            int blockZ,
            String entityNbtSnbt
    ) {
        return new StructureEntity(
                List.of(x, y, z),
                new StructurePosition(blockX, blockY, blockZ),
                entityNbtSnbt,
                null
        );
    }
}
