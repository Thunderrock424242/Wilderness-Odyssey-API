package com.thunder.wildernessodysseyapi.compat.create;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies recovery of legacy Create schematics never overwrites or deletes user data. */
class LegacyCreateSchematicMigrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void copiesNestedSchematicsAndRetainsLegacyFiles() throws IOException {
        Path legacy = temporaryDirectory.resolve("legacy");
        Path target = temporaryDirectory.resolve("schematics");
        Path uploaded = target.resolve("uploaded");
        Path source = legacy.resolve("machines/lift.nbt");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "legacy schematic");

        LegacyCreateSchematicMigration.MigrationResult result =
                LegacyCreateSchematicMigration.migrate(legacy, target, uploaded);

        assertEquals(1, result.copiedFiles());
        assertEquals(0, result.conflictingFiles());
        assertTrue(result.completed());
        assertEquals("legacy schematic", Files.readString(uploaded.resolve("machines/lift.nbt")));
        assertEquals("legacy schematic", Files.readString(source));
    }

    @Test
    void restoresRootSchematicsToTheClientVisibleDirectory() throws IOException {
        Path legacy = temporaryDirectory.resolve("legacy");
        Path target = temporaryDirectory.resolve("schematics");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("base.nbt"), "local schematic");

        LegacyCreateSchematicMigration.MigrationResult result =
                LegacyCreateSchematicMigration.migrate(legacy, target, target.resolve("uploaded"));

        assertEquals(1, result.copiedFiles());
        assertEquals("local schematic", Files.readString(target.resolve("base.nbt")));
    }

    @Test
    void neverOverwritesAConflictingDestination() throws IOException {
        Path legacy = temporaryDirectory.resolve("legacy");
        Path target = temporaryDirectory.resolve("schematics");
        Files.createDirectories(legacy);
        Files.createDirectories(target);
        Files.writeString(legacy.resolve("base.nbt"), "legacy");
        Files.writeString(target.resolve("base.nbt"), "current");

        LegacyCreateSchematicMigration.MigrationResult result =
                LegacyCreateSchematicMigration.migrate(legacy, target, target.resolve("uploaded"));

        assertEquals(0, result.copiedFiles());
        assertEquals(1, result.conflictingFiles());
        assertFalse(result.completed());
        assertEquals("current", Files.readString(target.resolve("base.nbt")));
        assertEquals("legacy", Files.readString(legacy.resolve("base.nbt")));
    }

    @Test
    void identicalDestinationsCompleteWithoutBeingRewritten() throws IOException {
        Path legacy = temporaryDirectory.resolve("legacy");
        Path target = temporaryDirectory.resolve("schematics");
        Files.createDirectories(legacy);
        Files.createDirectories(target);
        Files.writeString(legacy.resolve("base.nbt"), "same");
        Files.writeString(target.resolve("base.nbt"), "same");

        LegacyCreateSchematicMigration.MigrationResult result =
                LegacyCreateSchematicMigration.migrate(legacy, target, target.resolve("uploaded"));

        assertEquals(1, result.alreadyPresentFiles());
        assertEquals(0, result.conflictingFiles());
        assertTrue(result.completed());
    }

    @Test
    void completionMarkerMakesLaterPassesNoOps() throws IOException {
        Path legacy = temporaryDirectory.resolve("legacy");
        Path target = temporaryDirectory.resolve("schematics");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("first.nbt"), "first");
        LegacyCreateSchematicMigration.migrate(legacy, target, target.resolve("uploaded"));
        Files.writeString(legacy.resolve("later.nbt"), "later");

        LegacyCreateSchematicMigration.MigrationResult second =
                LegacyCreateSchematicMigration.migrate(legacy, target, target.resolve("uploaded"));

        assertEquals(0, second.copiedFiles());
        assertTrue(second.completed());
        assertFalse(Files.exists(target.resolve("later.nbt")));
        assertTrue(Files.exists(legacy.resolve("later.nbt")));
    }
}
