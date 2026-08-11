package com.thunder.wildernessodysseyapi.structuregen.blueprint;

import com.thunder.wildernessodysseyapi.structuregen.diagnostic.StructureDiagnostic;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.StructureGenResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers strict JSON-shape parsing before blueprints reach semantic validation. */
class BlueprintParserTest {

    private final BlueprintParser parser = new BlueprintParser();

    @TempDir
    Path tempDirectory;

    @Test
    void parsesCompleteValidBlueprintFromUtf8File() throws IOException {
        Path source = tempDirectory.resolve("test_shelter.json");
        Files.writeString(source, """
                {
                  "formatVersion": 1,
                  "name": "test_shelter",
                  "size": [3, 2, 3],
                  "dataVersion": 3955,
                  "metadata": {"author": "structuregen-test"},
                  "markers": ["entry"],
                  "blocks": [
                    {
                      "pos": [1, 0, 1],
                      "block": "minecraft:oak_stairs",
                      "properties": {"facing": "north", "half": "bottom"},
                      "blockEntitySnbt": "{CustomName:'fixture'}",
                      "usageIntent": "functional",
                      "requiredSystem": "minecraft:container",
                      "markers": ["seat"]
                    }
                  ],
                  "entities": [
                    {
                      "pos": [1.5, 1.0, 1.5],
                      "blockPos": [1, 1, 1],
                      "nbtSnbt": "{id:'minecraft:armor_stand'}"
                    }
                  ],
                  "rawRootSnbt": "{structuregen_fixture:1b}"
                }
                """);

        StructureGenResult<BlueprintDocument> result = parser.parse(source);

        assertFalse(result.hasErrors());
        assertNotNull(result.value());
        BlueprintDocument blueprint = result.value();
        assertEquals(source, blueprint.source());
        assertEquals(1, blueprint.formatVersion());
        assertEquals("test_shelter", blueprint.name());
        assertEquals(18L, blueprint.size().volume());
        assertEquals(Map.of("author", "structuregen-test"), blueprint.metadata());
        assertEquals("minecraft:oak_stairs", blueprint.blocks().getFirst().blockId());
        assertEquals(Map.of("facing", "north", "half", "bottom"),
                blueprint.blocks().getFirst().properties());
        assertEquals("functional", blueprint.blocks().getFirst().usageIntent());
        assertEquals("minecraft:container", blueprint.blocks().getFirst().requiredSystem());
        assertEquals(1, blueprint.entities().size());
        assertEquals("{structuregen_fixture:1b}", blueprint.rawRootSnbt());
        assertEquals(BlueprintContentPolicy.defaults(), blueprint.contentPolicy());
        assertTrue(blueprint.materials().isEmpty());
    }

    @Test
    void parsesContentPolicyAndSemanticMaterialsInDeterministicOrder() {
        Path source = tempDirectory.resolve("mod-aware.json");
        StructureGenResult<BlueprintDocument> result = parser.parse("""
                {
                  "formatVersion": 1,
                  "name": "mod_aware",
                  "size": [1, 1, 1],
                  "contentPolicy": {
                    "allowInstalledModBlocks": false,
                    "preferredDecorativeMods": ["supplementaries", "create"],
                    "requiredMods": ["create"],
                    "enabledFunctionalSystems": ["create:kinetics"]
                  },
                  "materials": {
                    "z_detail": {
                      "intent": "decorative",
                      "preferred": [{"block": "minecraft:chain"}]
                    },
                    "industrial_detail": {
                      "requiredSystem": "create:kinetics",
                      "preferred": [
                        {
                          "block": "create:fluid_pipe",
                          "requiresMod": "create",
                          "properties": {"waterlogged": "false", "axis": "x"}
                        }
                      ],
                      "fallbacks": [{"block": "minecraft:iron_bars"}]
                    }
                  },
                  "blocks": [
                    {"pos": [0, 0, 0], "block": "$industrial_detail"}
                  ]
                }
                """, source);

        assertFalse(result.hasErrors());
        BlueprintDocument blueprint = result.value();
        assertNotNull(blueprint);
        assertFalse(blueprint.contentPolicy().allowInstalledModBlocks());
        assertEquals(List.of("supplementaries", "create"),
                blueprint.contentPolicy().preferredDecorativeMods());
        assertEquals(List.of("create"), blueprint.contentPolicy().requiredMods());
        assertEquals(List.of("create:kinetics"), blueprint.contentPolicy().enabledFunctionalSystems());
        assertEquals(List.of("industrial_detail", "z_detail"),
                List.copyOf(blueprint.materials().keySet()));

        BlueprintMaterialDefinition industrial = blueprint.materials().get("industrial_detail");
        assertEquals(BlueprintMaterialDefinition.DEFAULT_INTENT, industrial.intent());
        assertEquals("create:kinetics", industrial.requiredSystem());
        assertEquals("create:fluid_pipe", industrial.preferred().getFirst().blockId());
        assertEquals("create", industrial.preferred().getFirst().requiresMod());
        assertEquals(Map.of("axis", "x", "waterlogged", "false"),
                industrial.preferred().getFirst().properties());
        assertEquals("minecraft:iron_bars", industrial.fallbacks().getFirst().blockId());
        assertEquals("$industrial_detail", blueprint.blocks().getFirst().blockId());
    }

    @Test
    void reportsMalformedJsonWithoutProducingDocument() {
        Path source = tempDirectory.resolve("malformed.json");

        StructureGenResult<BlueprintDocument> result = parser.parse("{\"formatVersion\": 1,", source);

        assertTrue(result.hasErrors());
        assertNull(result.value());
        assertDiagnostic(result, "$", "Malformed JSON");
    }

    @Test
    void reportsEveryMissingRequiredTopLevelField() {
        Path source = tempDirectory.resolve("missing.json");

        StructureGenResult<BlueprintDocument> result = parser.parse("{}", source);

        assertTrue(result.hasErrors());
        assertNull(result.value());
        assertDiagnostic(result, "$.formatVersion", "Required field is missing");
        assertDiagnostic(result, "$.name", "Required field is missing");
        assertDiagnostic(result, "size", "exactly three integers");
        assertDiagnostic(result, "blocks", "Required field is missing");
    }

    @Test
    void reportsMalformedBlockEntriesIndependently() {
        Path source = tempDirectory.resolve("bad-blocks.json");
        String json = """
                {
                  "formatVersion": 1,
                  "name": "bad_blocks",
                  "size": [2, 2, 2],
                  "blocks": [
                    12,
                    {"pos": [0, 0], "block": "minecraft:stone"},
                    {"pos": [0, 0, 0], "block": 42}
                  ]
                }
                """;

        StructureGenResult<BlueprintDocument> result = parser.parse(json, source);

        assertTrue(result.hasErrors());
        assertNull(result.value());
        assertDiagnostic(result, "blocks[0]", "must be an object");
        assertDiagnostic(result, "blocks[1].pos", "exactly three integers");
        assertDiagnostic(result, "blocks[2].block", "Must be a string");
    }

    @Test
    void rejectsUnknownFieldsAndNonStringMetadataValues() {
        Path source = tempDirectory.resolve("unknown-fields.json");
        StructureGenResult<BlueprintDocument> result = parser.parse("""
                {
                  "formatVersion": 1,
                  "name": "strict_schema",
                  "size": [1, 1, 1],
                  "metdata": {},
                  "metadata": {"numeric": 4},
                  "blocks": [
                    {"pos": [0, 0, 0], "block": "minecraft:stone", "propeties": {}}
                  ]
                }
                """, source);

        assertTrue(result.hasErrors());
        assertNull(result.value());
        assertDiagnostic(result, "$.metdata", "Unknown Blueprint v1 field");
        assertDiagnostic(result, "metadata.numeric", "Must be a string");
        assertDiagnostic(result, "blocks[0].propeties", "Unknown Blueprint v1 field");
    }

    @Test
    void rejectsMalformedAndUnknownNestedContentFields() {
        Path source = tempDirectory.resolve("bad-content.json");
        StructureGenResult<BlueprintDocument> result = parser.parse("""
                {
                  "formatVersion": 1,
                  "name": "bad_content",
                  "size": [1, 1, 1],
                  "contentPolicy": {
                    "allowInstalledModBlocks": "yes",
                    "preferredDecorativeMods": "create",
                    "surprise": true
                  },
                  "materials": {
                    "industrial_detail": {
                      "intent": 7,
                      "unexpected": [],
                      "preferred": [
                        {"properties": {"axis": 3}, "extra": true},
                        "create:fluid_pipe"
                      ],
                      "fallbacks": {}
                    }
                  },
                  "blocks": [{"pos": [0, 0, 0], "block": "$industrial_detail"}]
                }
                """, source);

        assertTrue(result.hasErrors());
        assertNull(result.value());
        assertDiagnostic(result, "contentPolicy.surprise", "Unknown Blueprint v1 field");
        assertDiagnostic(result, "contentPolicy.allowInstalledModBlocks", "Must be a boolean");
        assertDiagnostic(result, "contentPolicy.preferredDecorativeMods", "array of strings");
        assertDiagnostic(result, "materials.industrial_detail.unexpected", "Unknown Blueprint v1 field");
        assertDiagnostic(result, "materials.industrial_detail.intent", "Must be a string");
        assertDiagnostic(result, "materials.industrial_detail.preferred[0].extra", "Unknown Blueprint v1 field");
        assertDiagnostic(result, "materials.industrial_detail.preferred[0].block", "Required field is missing");
        assertDiagnostic(result, "materials.industrial_detail.preferred[0].properties.axis", "Must be a string");
        assertDiagnostic(result, "materials.industrial_detail.preferred[1]", "must be an object");
        assertDiagnostic(result, "materials.industrial_detail.fallbacks", "array of material candidate objects");
    }

    @Test
    void explicitlyWarnsWhenExportOnlyPaletteExtensionsAreNotImported() {
        Path source = tempDirectory.resolve("export-reference.json");
        StructureGenResult<BlueprintDocument> result = parser.parse("""
                {
                  "formatVersion": 1,
                  "name": "export_reference",
                  "size": [1, 1, 1],
                  "blocks": [
                    {
                      "pos": [0, 0, 0],
                      "block": "minecraft:stone",
                      "sourcePaletteIndex": 0
                    }
                  ],
                  "sourcePalettes": [[{"block": "minecraft:stone"}]],
                  "unsupportedFields": ["palette[0].future"]
                }
                """, source);

        assertFalse(result.hasErrors());
        assertNotNull(result.value());
        assertDiagnostic(result, "sourcePalettes", "Export-only palette data");
        assertDiagnostic(result, "blocks[*].sourcePaletteIndex", "not imported");
        assertDiagnostic(result, "unsupportedFields", "not model data");
    }

    private void assertDiagnostic(
            StructureGenResult<?> result,
            String location,
            String messageFragment
    ) {
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                        location.equals(diagnostic.location())
                                && diagnostic.message().contains(messageFragment)),
                () -> "Missing diagnostic at " + location + " containing '" + messageFragment
                        + "': " + result.diagnostics().stream().map(StructureDiagnostic::format).toList());
    }
}
