package com.thunder.wildernessodysseyapi.structuregen.inspection;

import com.thunder.wildernessodysseyapi.structuregen.content.ContentManifestStatus;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlock;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlockState;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureEntity;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureModel;
import com.thunder.wildernessodysseyapi.structuregen.model.StructurePosition;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureSize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers report totals that distinguish stored blocks, explicit air, and occupied volume. */
class StructureInspectorTest {

    @TempDir
    Path tempDirectory;

    @Test
    void reportsExplicitAirStatesBlockEntitiesEntitiesAndCategories() {
        StructureModel model = new StructureModel(
                "inspection_fixture",
                new StructureSize(2, 2, 2),
                List.of(
                        block(0, 0, 0, "minecraft:air", Map.of(), null),
                        block(1, 0, 0, "minecraft:oak_stairs", Map.of("facing", "north"), null),
                        block(0, 1, 0, "minecraft:oak_stairs", Map.of("facing", "south"), null),
                        block(1, 1, 1, "minecraft:chest", Map.of("facing", "north"),
                                "{id:'minecraft:chest',Items:[]}")
                ),
                List.of(new StructureEntity(
                        List.of(0.5D, 1.0D, 0.5D),
                        new StructurePosition(0, 1, 0),
                        "{id:'minecraft:armor_stand',Invisible:1b}",
                        null
                )),
                3955,
                Map.of(),
                List.of(),
                List.of(),
                null,
                List.of("root.custom_fixture")
        );
        Path source = tempDirectory.resolve("inspection-fixture.nbt");

        StructureInspectionReport report = new StructureInspector().inspect(source, model);

        assertEquals(source.toAbsolutePath().normalize().toString(), report.file());
        assertEquals(8L, report.boundingVolume());
        assertEquals(4, report.storedBlocks());
        assertEquals(1, report.explicitAirBlocks());
        assertEquals(3, report.occupiedBlocks());
        assertEquals(0.375D, report.occupiedDensity(), 1.0e-12D);
        assertEquals(1, report.paletteCount());
        assertEquals(4, report.primaryPaletteSize());
        assertEquals(3, report.uniqueBlockTypes());
        assertEquals(4, report.blockStateVariants());
        assertEquals(1, report.blockEntityCount());
        assertEquals(1, report.entityCount());
        assertEquals(List.of("root.custom_fixture"), report.unknownOrUnsupportedTags());

        assertEquals(new StructureInspectionReport.BlockFrequency("minecraft:oak_stairs", 2L),
                report.blockFrequencies().getFirst());
        assertEquals(2L, report.categoryCounts().get("stairs").longValue());
        assertEquals(1L, report.categoryCounts().get("containers").longValue());
        assertEquals(1L, report.categoryCounts().get("functional").longValue());
        assertEquals(1L, report.blockEntityTypes().get("minecraft:chest").longValue());
        assertEquals(1L, report.entityTypes().get("minecraft:armor_stand").longValue());

        assertEquals(new StructureInspectionReport.VerticalLayer(0, 2L, 1L),
                report.verticalDistribution().get(0));
        assertEquals(new StructureInspectionReport.VerticalLayer(1, 2L, 2L),
                report.verticalDistribution().get(1));
        assertTrue(report.palettes().getFirst().entries().stream().anyMatch(entry ->
                entry.block().equals("minecraft:oak_stairs")
                        && entry.properties().equals(Map.of("facing", "north"))));
        assertTrue(report.palettes().getFirst().entries().stream().anyMatch(entry ->
                entry.block().equals("minecraft:oak_stairs")
                        && entry.properties().equals(Map.of("facing", "south"))));
    }

    @Test
    void reportsConcreteBlockTypesAndRecordsByNamespace() {
        StructureModel model = new StructureModel(
                "namespace_fixture",
                new StructureSize(7, 1, 1),
                List.of(
                        block(0, 0, 0, "minecraft:stone", Map.of(), null),
                        block(1, 0, 0, "minecraft:stone", Map.of(), null),
                        block(2, 0, 0, "minecraft:dirt", Map.of(), null),
                        block(3, 0, 0, "wildernessodysseyapi:cryo_tube", Map.of(), null),
                        block(4, 0, 0, "create:fluid_pipe", Map.of(), null),
                        block(5, 0, 0, "create:fluid_pipe", Map.of(), null),
                        block(6, 0, 0, "create:copycat_panel", Map.of(), null)
                ),
                List.of(),
                3955,
                Map.of(),
                List.of(),
                List.of(),
                null,
                List.of()
        );

        StructureInspectionReport report = new StructureInspector().inspect(
                tempDirectory.resolve("namespace-fixture.nbt"), model
        );

        assertEquals(List.of("minecraft", "wildernessodysseyapi", "create"),
                report.namespaceUsage().keySet().stream().toList());
        assertEquals(new StructureInspectionReport.NamespaceUsage(2, 3L),
                report.namespaceUsage().get("minecraft"));
        assertEquals(new StructureInspectionReport.NamespaceUsage(1, 1L),
                report.namespaceUsage().get("wildernessodysseyapi"));
        assertEquals(new StructureInspectionReport.NamespaceUsage(2, 3L),
                report.namespaceUsage().get("create"));
        assertEquals(ContentManifestStatus.ABSENT, report.contentManifestStatus());
        assertEquals(List.of("create"), report.externalNamespacesUsed());

        String textReport = new StructureReportWriter().formatText(report);
        assertTrue(textReport.contains("minecraft: 2 block types / 3 records"));
        assertTrue(textReport.contains("wildernessodysseyapi: 1 block type / 1 record"));
        assertTrue(textReport.contains("create: 2 block types / 3 records"));
        assertTrue(textReport.contains("Content manifest status: absent (no StructureGen content manifest)"));
        assertTrue(textReport.contains("External namespaces used:\n    - create"));
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
}
