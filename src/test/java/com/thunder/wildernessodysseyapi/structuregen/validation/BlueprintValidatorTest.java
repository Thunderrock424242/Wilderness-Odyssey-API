package com.thunder.wildernessodysseyapi.structuregen.validation;

import com.thunder.wildernessodysseyapi.structuregen.StructureGenConstants;
import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintBlock;
import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintDocument;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.StructureDiagnostic;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.StructureGenResult;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureModel;
import com.thunder.wildernessodysseyapi.structuregen.model.StructurePosition;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureSize;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the validation boundary that authorizes parsed blueprints for compilation. */
class BlueprintValidatorTest {

    private static final Path SOURCE = Path.of("src", "test", "fixtures", "test.blueprint.json");

    private final BlueprintValidator validator = new BlueprintValidator(new FakeBlockStateResolver());

    @Test
    void convertsValidBlueprintIntoCanonicalModel() {
        BlueprintDocument blueprint = blueprint(
                StructureGenConstants.BLUEPRINT_FORMAT_VERSION,
                "valid_shelter",
                new StructureSize(3, 2, 3),
                List.of(
                        block(0, 0, 0, "minecraft:air", Map.of(), null),
                        block(1, 0, 1, "minecraft:oak_stairs",
                                Map.of("facing", "north", "half", "bottom"), null),
                        block(2, 0, 2, "minecraft:chest", Map.of("facing", "north"),
                                "{id:'minecraft:chest',Items:[]}")
                )
        );

        StructureGenResult<StructureModel> result = validator.validate(blueprint);

        assertFalse(result.hasErrors());
        assertNotNull(result.value());
        StructureModel model = result.value();
        assertEquals("valid_shelter", model.name());
        assertEquals(new StructureSize(3, 2, 3), model.size());
        assertEquals(3, model.blocks().size());
        assertTrue(model.blocks().getFirst().isExplicitAir());
        assertEquals("minecraft:oak_stairs[facing=north,half=bottom]",
                model.blocks().get(1).state().canonicalKey());
    }

    @Test
    void rejectsUnsupportedBlueprintVersion() {
        BlueprintDocument blueprint = blueprint(
                StructureGenConstants.BLUEPRINT_FORMAT_VERSION + 1,
                "future_format",
                new StructureSize(1, 1, 1),
                List.of(block(0, 0, 0, "minecraft:stone", Map.of(), null))
        );

        StructureGenResult<StructureModel> result = validator.validate(blueprint);

        assertInvalid(result, "formatVersion", "Unsupported Blueprint formatVersion");
    }

    @Test
    void rejectsZeroSizedStructure() {
        BlueprintDocument blueprint = blueprint(
                1,
                "zero_size",
                new StructureSize(0, 2, 2),
                List.of(block(0, 0, 0, "minecraft:stone", Map.of(), null))
        );

        StructureGenResult<StructureModel> result = validator.validate(blueprint);

        assertInvalid(result, "size", "greater than zero");
    }

    @Test
    void rejectsStructureDimensionAboveAuthoredLimit() {
        BlueprintDocument blueprint = blueprint(
                1,
                "oversized",
                new StructureSize(StructureGenConstants.MAX_DIMENSION + 1, 1, 1),
                List.of(block(0, 0, 0, "minecraft:stone", Map.of(), null))
        );

        StructureGenResult<StructureModel> result = validator.validate(blueprint);

        assertInvalid(result, "size", "at most " + StructureGenConstants.MAX_DIMENSION);
    }

    @Test
    void rejectsBlockAtUpperExclusiveBound() {
        BlueprintDocument blueprint = blueprint(
                1,
                "out_of_bounds",
                new StructureSize(2, 2, 2),
                List.of(block(2, 1, 1, "minecraft:stone", Map.of(), null))
        );

        StructureGenResult<StructureModel> result = validator.validate(blueprint);

        assertInvalid(result, "blocks[0].pos", "lies outside structure bounds");
    }

    @Test
    void rejectsNegativeBlockCoordinate() {
        BlueprintDocument blueprint = blueprint(
                1,
                "negative_position",
                new StructureSize(2, 2, 2),
                List.of(block(-1, 0, 0, "minecraft:stone", Map.of(), null))
        );

        StructureGenResult<StructureModel> result = validator.validate(blueprint);

        assertInvalid(result, "blocks[0].pos", "[-1, 0, 0]");
    }

    @Test
    void rejectsDuplicateBlockCoordinates() {
        BlueprintDocument blueprint = blueprint(
                1,
                "duplicate_position",
                new StructureSize(2, 2, 2),
                List.of(
                        block(1, 1, 1, "minecraft:stone", Map.of(), null),
                        block(1, 1, 1, "minecraft:air", Map.of(), null)
                )
        );

        StructureGenResult<StructureModel> result = validator.validate(blueprint);

        assertInvalid(result, "blocks[1].pos", "Duplicate block position [1, 1, 1]");
    }

    @Test
    void rejectsInvalidMinecraftResourceLocation() {
        BlueprintDocument blueprint = blueprint(
                1,
                "invalid_id",
                new StructureSize(1, 1, 1),
                List.of(block(0, 0, 0, "minecraft:Stone Bricks", Map.of(), null))
        );

        StructureGenResult<StructureModel> result = validator.validate(blueprint);

        assertInvalid(result, "blocks[0].block", "Invalid Minecraft resource location");
    }

    @Test
    void reportsUnknownPropertyAndInvalidValueFromResolver() {
        BlueprintDocument blueprint = blueprint(
                1,
                "invalid_state",
                new StructureSize(1, 1, 1),
                List.of(block(0, 0, 0, "minecraft:oak_stairs",
                        Map.of("direction", "north", "facing", "sideways"), null))
        );

        StructureGenResult<StructureModel> result = validator.validate(blueprint);

        assertInvalid(result, "blocks[0].properties", "has no property named 'direction'");
        assertInvalid(result, "blocks[0].properties", "Invalid value 'sideways'");
    }

    @Test
    void rejectsMalformedBlockEntitySnbt() {
        BlueprintDocument blueprint = blueprint(
                1,
                "bad_snbt",
                new StructureSize(1, 1, 1),
                List.of(block(0, 0, 0, "minecraft:chest", Map.of(), "{id:"))
        );

        StructureGenResult<StructureModel> result = validator.validate(blueprint);

        assertInvalid(result, "blocks[0].blockEntitySnbt", "Malformed compound SNBT");
    }

    @Test
    void rejectsEmptyStructureAndUnknownRegisteredBlock() {
        StructureGenResult<StructureModel> emptyResult = validator.validate(blueprint(
                1, "empty_blocks", new StructureSize(1, 1, 1), List.of()
        ));
        StructureGenResult<StructureModel> unknownBlockResult = validator.validate(blueprint(
                1,
                "unknown_block",
                new StructureSize(1, 1, 1),
                List.of(block(0, 0, 0, "minecraft:not_a_real_block", Map.of(), null))
        ));

        assertInvalid(emptyResult, "blocks", "at least one explicitly stored block");
        assertInvalid(unknownBlockResult, "blocks[0].properties", "No fake-registry block exists");
    }

    @Test
    void rejectsNegativeDataVersionAndCanonicalFieldsInsideRawSnbt() {
        BlueprintDocument blueprint = new BlueprintDocument(
                SOURCE,
                1,
                "invalid_raw_fields",
                new StructureSize(1, 1, 1),
                -1,
                Map.of(),
                List.of(),
                List.of(new BlueprintBlock(
                        new StructurePosition(0, 0, 0),
                        "minecraft:stone",
                        Map.of(),
                        null,
                        List.of(),
                        "{state:4,custom:1b}"
                )),
                List.of(),
                "{DataVersion:1,structuregen:1b}"
        );

        StructureGenResult<StructureModel> result = validator.validate(blueprint);

        assertInvalid(result, "dataVersion", "zero or greater");
        assertInvalid(result, "blocks[0].rawEntrySnbt.state", "owned by the canonical model");
        assertInvalid(result, "rawRootSnbt.DataVersion", "owned by the canonical model");
        assertInvalid(result, "rawRootSnbt.structuregen", "Must be a compound tag");
    }

    private BlueprintDocument blueprint(
            int formatVersion,
            String name,
            StructureSize size,
            List<BlueprintBlock> blocks
    ) {
        return new BlueprintDocument(
                SOURCE,
                formatVersion,
                name,
                size,
                3955,
                Map.of("fixture", "true"),
                List.of("test"),
                blocks,
                List.of(),
                null
        );
    }

    private BlueprintBlock block(
            int x,
            int y,
            int z,
            String blockId,
            Map<String, String> properties,
            String blockEntitySnbt
    ) {
        return new BlueprintBlock(
                new StructurePosition(x, y, z),
                blockId,
                properties,
                blockEntitySnbt,
                List.of(),
                null
        );
    }

    private void assertInvalid(
            StructureGenResult<StructureModel> result,
            String location,
            String messageFragment
    ) {
        assertTrue(result.hasErrors());
        assertNull(result.value());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                        location.equals(diagnostic.location())
                                && diagnostic.message().contains(messageFragment)),
                () -> "Missing diagnostic at " + location + " containing '" + messageFragment
                        + "': " + result.diagnostics().stream().map(StructureDiagnostic::format).toList());
    }

    /** Small deterministic stand-in for the runtime block registry. */
    private static final class FakeBlockStateResolver implements BlockStateResolver {

        private static final Set<String> KNOWN_BLOCKS = Set.of(
                "minecraft:air",
                "minecraft:chest",
                "minecraft:oak_stairs",
                "minecraft:stone"
        );

        @Override
        public Resolution validate(String blockId, Map<String, String> properties) {
            List<String> errors = new ArrayList<>();
            if (!KNOWN_BLOCKS.contains(blockId)) {
                errors.add("No fake-registry block exists with ID '" + blockId + "'.");
                return new Resolution(errors, List.of());
            }

            Map<String, Set<String>> allowed = switch (blockId) {
                case "minecraft:oak_stairs" -> Map.of(
                        "facing", Set.of("north", "south", "east", "west"),
                        "half", Set.of("top", "bottom"),
                        "waterlogged", Set.of("true", "false")
                );
                case "minecraft:chest" -> Map.of(
                        "facing", Set.of("north", "south", "east", "west")
                );
                default -> Map.of();
            };

            properties.forEach((name, value) -> {
                Set<String> values = allowed.get(name);
                if (values == null) {
                    errors.add(blockId + " has no property named '" + name + "'.");
                } else if (!values.contains(value)) {
                    errors.add("Invalid value '" + value + "' for property '" + name + "' on " + blockId + ".");
                }
            });
            return new Resolution(errors, List.of());
        }
    }
}
