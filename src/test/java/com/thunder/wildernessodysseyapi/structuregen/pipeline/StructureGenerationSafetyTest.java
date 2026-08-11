package com.thunder.wildernessodysseyapi.structuregen.pipeline;

import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintBlock;
import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintDocument;
import com.thunder.wildernessodysseyapi.structuregen.content.ContentManifestStatus;
import com.thunder.wildernessodysseyapi.structuregen.content.StructureContentManifest;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.StructureGenResult;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureModel;
import com.thunder.wildernessodysseyapi.structuregen.model.StructurePosition;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureSize;
import com.thunder.wildernessodysseyapi.structuregen.nbt.MinecraftStructureNbtReader;
import com.thunder.wildernessodysseyapi.structuregen.validation.BlockStateResolver;
import com.thunder.wildernessodysseyapi.structuregen.validation.BlueprintValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards StructureGen's fail-closed path, collision, and pre-publication validation boundaries.
 */
class StructureGenerationSafetyTest {

    @TempDir
    Path tempDirectory;

    @Test
    void rejectsTraversalAndUnsafeNamesBeforeTheyCanBecomeGeneratedTargets() throws IOException {
        StructureGenPaths paths = paths(tempDirectory.resolve("traversal-project"));
        BlueprintValidator validator = validator();

        assertThrows(IllegalArgumentException.class, () -> paths.generatedStructure("../escape"));
        for (String invalidName : List.of("../escape", "nested/escape", "Uppercase", "bunker")) {
            StructureGenResult<StructureModel> result = validator.validate(blueprint(invalidName));

            assertTrue(result.hasErrors(), () -> "Expected invalid name to fail: " + invalidName);
            assertNull(result.value());
            assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.location().equals("name")));
        }
    }

    @Test
    void rejectsEveryPackagedStructureResourceCollision() throws IOException {
        Path projectRoot = tempDirectory.resolve("collision-project");
        StructureGenPaths paths = paths(projectRoot);
        Path namespaceRoot = projectRoot.resolve("src/main/resources/data/wildernessodysseyapi");
        Path singular = namespaceRoot.resolve("structure/manual_singular.nbt");
        Path plural = namespaceRoot.resolve("structures/manual_plural.nbt");
        Path generatedData = projectRoot.resolve(
                "src/generated/resources/data/wildernessodysseyapi/structure/generated_data.nbt"
        );
        Path gameTestTemplate = projectRoot.resolve("src/main/resources/data/minecraft/structures/empty.nbt");
        Files.createDirectories(singular.getParent());
        Files.createDirectories(plural.getParent());
        Files.createDirectories(generatedData.getParent());
        Files.createDirectories(gameTestTemplate.getParent());
        Files.writeString(singular, "manual singular fixture", StandardCharsets.UTF_8);
        Files.writeString(plural, "manual plural fixture", StandardCharsets.UTF_8);
        Files.writeString(generatedData, "generated data fixture", StandardCharsets.UTF_8);
        Files.writeString(gameTestTemplate, "GameTest fixture", StandardCharsets.UTF_8);

        IllegalArgumentException singularFailure = assertThrows(
                IllegalArgumentException.class,
                () -> paths.requireNoHandAuthoredCollision("manual_singular")
        );
        IllegalArgumentException pluralFailure = assertThrows(
                IllegalArgumentException.class,
                () -> paths.requireNoHandAuthoredCollision("manual_plural")
        );
        IllegalArgumentException generatedDataFailure = assertThrows(
                IllegalArgumentException.class,
                () -> paths.requireNoHandAuthoredCollision("generated_data")
        );
        IllegalArgumentException emptyFailure = assertThrows(
                IllegalArgumentException.class,
                () -> paths.requireNoHandAuthoredCollision("empty")
        );

        assertTrue(singularFailure.getMessage().contains(singular.toAbsolutePath().normalize().toString()));
        assertTrue(pluralFailure.getMessage().contains(plural.toAbsolutePath().normalize().toString()));
        assertTrue(generatedDataFailure.getMessage().contains(generatedData.toAbsolutePath().normalize().toString()));
        assertTrue(emptyFailure.getMessage().contains(gameTestTemplate.toAbsolutePath().normalize().toString()));
    }

    @Test
    void duplicateBlueprintNamesAbortBeforeAnyOutputIsPublished() throws IOException {
        Path projectRoot = tempDirectory.resolve("duplicate-name-project");
        StructureGenPaths paths = paths(projectRoot);
        String blueprint = """
                {
                  "formatVersion": 1,
                  "name": "same_target",
                  "size": [1, 1, 1],
                  "blocks": [{"pos": [0, 0, 0], "block": "minecraft:stone"}]
                }
                """;
        Files.writeString(paths.blueprintRoot().resolve("first.json"), blueprint, StandardCharsets.UTF_8);
        Files.writeString(paths.blueprintRoot().resolve("second.json"), blueprint, StandardCharsets.UTF_8);

        StructureGenerationResult result = new StructureGenerationPipeline(paths, validator(), ignored -> {
        }).generate();

        assertEquals(2, result.blueprintsFound());
        assertEquals(2, result.validated());
        assertTrue(result.generated().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.message().contains("Duplicate generated structure name")));
        assertTrue(Files.notExists(paths.generatedStructure("same_target")));
    }

    @Test
    void invalidBlueprintBatchLeavesExistingGeneratedTargetUntouched() throws IOException {
        Path projectRoot = tempDirectory.resolve("pipeline-project");
        StructureGenPaths paths = paths(projectRoot);
        Files.createDirectories(paths.blueprintRoot());
        Path blueprint = paths.blueprintRoot().resolve("invalid.json");
        Files.writeString(blueprint, """
                {
                  "formatVersion": 1,
                  "name": "existing_target",
                  "size": [1, 1, 1],
                  "blocks": [
                    {"pos": [0, 0, 0], "block": "minecraft:stone"},
                    {"pos": [0, 0, 0], "block": "minecraft:dirt"}
                  ]
                }
                """, StandardCharsets.UTF_8);

        Path existingTarget = paths.generatedStructure("existing_target");
        Files.createDirectories(existingTarget.getParent());
        byte[] originalBytes = "known-good generated artifact".getBytes(StandardCharsets.UTF_8);
        Files.write(existingTarget, originalBytes);

        StructureGenerationResult result = new StructureGenerationPipeline(paths, validator(), ignored -> {
        }).generate();

        assertEquals(1, result.blueprintsFound());
        assertEquals(0, result.validated());
        assertTrue(result.generated().isEmpty());
        assertTrue(result.errorCount() > 0L);
        assertArrayEquals(originalBytes, Files.readAllBytes(existingTarget));
    }

    @Test
    void safeOutputPreservesPartialManifestWithoutUpgradingItsTrustStatus() throws IOException {
        Path projectRoot = tempDirectory.resolve("partial-manifest-project");
        StructureGenPaths paths = paths(projectRoot);
        StructureGenResult<StructureModel> validation = validator().validate(blueprint("partial_manifest_output"));
        assertFalse(validation.hasErrors(), () -> "Unexpected validation errors: " + validation.diagnostics());
        StructureModel validated = validation.value();
        assertNotNull(validated);
        StructureContentManifest partialManifest = new StructureContentManifest(
                StructureContentManifest.CURRENT_SCHEMA_VERSION,
                true,
                List.of("decorations"),
                List.of("required_platform"),
                List.of(),
                List.of(),
                ContentManifestStatus.PARTIAL
        );
        StructureModel partialModel = new StructureModel(
                validated.name(),
                validated.size(),
                validated.blocks(),
                validated.entities(),
                validated.dataVersion(),
                validated.metadata(),
                validated.markers(),
                validated.sourcePalettes(),
                validated.rawRootSnbt(),
                validated.unsupportedFields(),
                partialManifest
        );

        GeneratedStructure generated = new SafeStructureOutput(paths).writeVerified(partialModel);
        StructureModel reread = new MinecraftStructureNbtReader().read(generated.output());

        assertTrue(generated.verification().semanticallyMatches());
        assertEquals(partialManifest, reread.contentManifest());
        assertEquals(ContentManifestStatus.PARTIAL, reread.contentManifest().provenanceStatus());
        assertEquals(List.of("decorations"), reread.contentManifest().preferredDecorativeMods());
        assertEquals(List.of("required_platform"), reread.contentManifest().requiredMods());
    }

    @Test
    void removesObsoleteOutputAfterCurrentBatchSucceedsWithoutTouchingNewManualResource() throws IOException {
        Path projectRoot = tempDirectory.resolve("obsolete-output-project");
        StructureGenPaths paths = paths(projectRoot);
        Path retainedBlueprint = paths.blueprintRoot().resolve("retained.json");
        Path obsoleteBlueprint = paths.blueprintRoot().resolve("obsolete.json");
        Files.writeString(retainedBlueprint, oneBlockBlueprint("retained"), StandardCharsets.UTF_8);
        Files.writeString(obsoleteBlueprint, oneBlockBlueprint("obsolete"), StandardCharsets.UTF_8);

        StructureGenerationPipeline pipeline = new StructureGenerationPipeline(paths, validator(), ignored -> {
        });
        StructureGenerationResult first = pipeline.generate();
        Path retainedOutput = paths.generatedStructure("retained");
        Path obsoleteOutput = paths.generatedStructure("obsolete");
        assertTrue(first.successful());
        assertTrue(Files.isRegularFile(retainedOutput));
        assertTrue(Files.isRegularFile(obsoleteOutput));

        Files.delete(obsoleteBlueprint);
        Path manualResource = projectRoot.resolve(
                "src/main/resources/data/wildernessodysseyapi/structure/obsolete.nbt"
        );
        Files.createDirectories(manualResource.getParent());
        byte[] manualBytes = "new hand-authored structure".getBytes(StandardCharsets.UTF_8);
        Files.write(manualResource, manualBytes);

        StructureGenerationResult second = pipeline.generate();

        assertTrue(second.successful());
        assertTrue(Files.isRegularFile(retainedOutput));
        assertFalse(Files.exists(obsoleteOutput));
        assertArrayEquals(manualBytes, Files.readAllBytes(manualResource));
    }

    @Test
    void removesNestedAndLegacyPluralNbtWithoutDeletingOtherGeneratedResources() throws IOException {
        Path projectRoot = tempDirectory.resolve("nested-obsolete-output-project");
        StructureGenPaths paths = paths(projectRoot);
        Path retainedBlueprint = paths.blueprintRoot().resolve("retained.json");
        Files.writeString(retainedBlueprint, oneBlockBlueprint("retained"), StandardCharsets.UTF_8);

        Path nestedStale = paths.generatedStructureRoot().resolve("old/nested_stale.nbt");
        Path legacyStale = paths.outputResourceRoot().resolve(
                "data/wildernessodysseyapi/structures/legacy_stale.nbt"
        );
        Path unrelatedGeneratedResource = paths.outputResourceRoot().resolve(
                "data/wildernessodysseyapi/worldgen/retained.json"
        );
        Files.createDirectories(nestedStale.getParent());
        Files.createDirectories(legacyStale.getParent());
        Files.createDirectories(unrelatedGeneratedResource.getParent());
        Files.writeString(nestedStale, "stale nested nbt", StandardCharsets.UTF_8);
        Files.writeString(legacyStale, "stale plural nbt", StandardCharsets.UTF_8);
        byte[] unrelatedBytes = "unrelated generated data".getBytes(StandardCharsets.UTF_8);
        Files.write(unrelatedGeneratedResource, unrelatedBytes);

        StructureGenerationResult result = new StructureGenerationPipeline(paths, validator(), ignored -> {
        }).generate();

        assertTrue(result.successful(), () -> "Unexpected diagnostics: " + result.diagnostics());
        assertTrue(Files.isRegularFile(paths.generatedStructure("retained")));
        assertFalse(Files.exists(nestedStale));
        assertFalse(Files.exists(legacyStale));
        assertArrayEquals(unrelatedBytes, Files.readAllBytes(unrelatedGeneratedResource));
    }

    private StructureGenPaths paths(Path projectRoot) throws IOException {
        Path blueprintRoot = projectRoot.resolve("src/main/structure_blueprints");
        Path outputRoot = projectRoot.resolve("build/generated/structuregen/resources");
        Files.createDirectories(blueprintRoot);
        Files.createDirectories(outputRoot);
        return new StructureGenPaths(projectRoot, blueprintRoot, outputRoot);
    }

    private BlueprintValidator validator() {
        return new BlueprintValidator((blockId, properties) -> BlockStateResolver.Resolution.valid());
    }

    private String oneBlockBlueprint(String name) {
        return """
                {
                  "formatVersion": 1,
                  "name": "%s",
                  "size": [1, 1, 1],
                  "blocks": [{"pos": [0, 0, 0], "block": "minecraft:stone"}]
                }
                """.formatted(name);
    }

    private BlueprintDocument blueprint(String name) {
        return new BlueprintDocument(
                tempDirectory.resolve(name.replace('/', '_') + ".json"),
                1,
                name,
                new StructureSize(1, 1, 1),
                3955,
                Map.of(),
                List.of(),
                List.of(new BlueprintBlock(
                        new StructurePosition(0, 0, 0),
                        "minecraft:stone",
                        Map.of(),
                        null,
                        List.of(),
                        null
                )),
                List.of(),
                null
        );
    }
}
