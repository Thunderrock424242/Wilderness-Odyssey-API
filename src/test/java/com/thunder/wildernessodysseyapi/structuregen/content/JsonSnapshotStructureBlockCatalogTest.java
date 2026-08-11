package com.thunder.wildernessodysseyapi.structuregen.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers strict parsing and deterministic normalization of offline registry snapshots. */
class JsonSnapshotStructureBlockCatalogTest {

    private static final String FINGERPRINT = "a".repeat(64);

    @TempDir
    Path tempDirectory;

    @Test
    void loadsModVersionsAndExactPropertyDomainsInDeterministicOrder() throws IOException {
        JsonSnapshotStructureBlockCatalog first = load("first.json", """
                {
                  "schemaVersion": 2,
                  "environmentFingerprint": "%s",
                  "mods": [
                    {"id": "zeta", "version": "2.4.0"},
                    {"id": "create", "version": "6.0.4"}
                  ],
                  "blocks": [
                    {
                      "id": "zeta:panel",
                      "properties": {},
                      "defaultProperties": {}
                    },
                    {
                      "id": "create:copycat_panel",
                      "properties": {
                        "waterlogged": ["true", "false"],
                        "axis": ["z", "x", "y"]
                      },
                      "defaultProperties": {
                        "waterlogged": "false",
                        "axis": "y"
                      }
                    }
                  ]
                }
                """.formatted(FINGERPRINT));
        JsonSnapshotStructureBlockCatalog second = load("second.json", """
                {
                  "schemaVersion": 2,
                  "environmentFingerprint": "%s",
                  "mods": [
                    {"id": "create", "version": "6.0.4"},
                    {"id": "zeta", "version": "2.4.0"}
                  ],
                  "blocks": [
                    {
                      "id": "create:copycat_panel",
                      "properties": {
                        "axis": ["y", "x", "z"],
                        "waterlogged": ["false", "true"]
                      },
                      "defaultProperties": {
                        "axis": "y",
                        "waterlogged": "false"
                      }
                    },
                    {
                      "id": "zeta:panel",
                      "properties": {},
                      "defaultProperties": {}
                    }
                  ]
                }
                """.formatted(FINGERPRINT));

        assertEquals(FINGERPRINT, first.environmentFingerprint());
        assertEquals(List.of("create", "zeta"), first.installedMods().keySet().stream().toList());
        assertEquals("6.0.4", first.installedMods().get("create"));
        assertEquals(
                List.of("create:copycat_panel", "zeta:panel"),
                first.blocks().keySet().stream().map(Object::toString).toList()
        );

        AvailableBlockDescriptor copycat = first.blocks().values().iterator().next();
        assertEquals(List.of("axis", "waterlogged"), copycat.properties().keySet().stream().toList());
        assertEquals(List.of("x", "y", "z"), copycat.properties().get("axis"));
        assertEquals(List.of("false", "true"), copycat.properties().get("waterlogged"));
        assertEquals(Map.of("axis", "y", "waterlogged", "false"), copycat.defaultProperties());

        assertTrue(first.validate("create:copycat_panel", Map.of("axis", "x")).isValid());
        StructureBlockCatalog.Validation invalidState = first.validate(
                "create:copycat_panel", Map.of("axis", "diagonal")
        );
        assertFalse(invalidState.isValid());
        assertTrue(invalidState.errors().getFirst().contains("Allowed values: [x, y, z]"));

        // Canonical serialization must not depend on registry enumeration or JSON field order.
        assertEquals(
                JsonSnapshotStructureBlockCatalog.toJson(first, FINGERPRINT).toString(),
                JsonSnapshotStructureBlockCatalog.toJson(second, FINGERPRINT).toString()
        );
        assertEquals(
                FINGERPRINT,
                JsonSnapshotStructureBlockCatalog.toJson(first, FINGERPRINT)
                        .get("environmentFingerprint").getAsString()
        );
    }

    @Test
    void rejectsUnknownFieldsMalformedDomainsAndInvalidDefaults() throws IOException {
        IOException unknownField = assertThrows(IOException.class, () -> load("unknown-field.json", """
                {
                  "schemaVersion": 2,
                  "environmentFingerprint": "%s",
                  "mods": [],
                  "blocks": [],
                  "unexpected": true
                }
                """.formatted(FINGERPRINT)));
        assertTrue(unknownField.getMessage().contains("unknown field 'unexpected'"));

        IOException malformedDomain = assertThrows(IOException.class, () -> load("malformed-domain.json", """
                {
                  "schemaVersion": 2,
                  "environmentFingerprint": "%s",
                  "mods": [{"id": "create", "version": "6.0.4"}],
                  "blocks": [{
                    "id": "create:copycat_panel",
                    "properties": {"axis": "x"},
                    "defaultProperties": {"axis": "x"}
                  }]
                }
                """.formatted(FINGERPRINT)));
        assertTrue(malformedDomain.getMessage().contains("properties.axis must be a JSON array"));

        IOException invalidDefault = assertThrows(IOException.class, () -> load("invalid-default.json", """
                {
                  "schemaVersion": 2,
                  "environmentFingerprint": "%s",
                  "mods": [{"id": "create", "version": "6.0.4"}],
                  "blocks": [{
                    "id": "create:copycat_panel",
                    "properties": {"axis": ["x", "y", "z"]},
                    "defaultProperties": {"axis": "diagonal"}
                  }]
                }
                """.formatted(FINGERPRINT)));
        assertTrue(invalidDefault.getMessage().contains("Default value 'diagonal' is not valid"));
    }

    @Test
    void rejectsLegacySchemaAndMissingOrMalformedEnvironmentFingerprint() {
        IOException legacySchema = assertThrows(IOException.class, () -> load("legacy-schema.json", """
                {
                  "schemaVersion": 1,
                  "mods": [],
                  "blocks": []
                }
                """));
        assertTrue(legacySchema.getMessage().contains("schemaVersion 1; expected 2"));

        IOException missing = assertThrows(IOException.class, () -> load("missing-fingerprint.json", """
                {
                  "schemaVersion": 2,
                  "mods": [],
                  "blocks": []
                }
                """));
        assertTrue(missing.getMessage().contains("root.environmentFingerprint must be a string"));

        IOException uppercase = assertThrows(IOException.class, () -> load("uppercase-fingerprint.json", """
                {
                  "schemaVersion": 2,
                  "environmentFingerprint": "%s",
                  "mods": [],
                  "blocks": []
                }
                """.formatted("A".repeat(64))));
        assertTrue(uppercase.getMessage().contains("exactly 64 lowercase hexadecimal characters"));

        assertThrows(
                IllegalArgumentException.class,
                () -> JsonSnapshotStructureBlockCatalog.requireEnvironmentFingerprint("f".repeat(63))
        );
    }

    private JsonSnapshotStructureBlockCatalog load(String filename, String json) throws IOException {
        Path snapshot = tempDirectory.resolve(filename);
        Files.writeString(snapshot, json);
        return JsonSnapshotStructureBlockCatalog.load(snapshot);
    }
}
