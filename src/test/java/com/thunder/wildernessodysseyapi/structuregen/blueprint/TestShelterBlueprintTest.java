package com.thunder.wildernessodysseyapi.structuregen.blueprint;

import com.thunder.wildernessodysseyapi.structuregen.diagnostic.StructureGenResult;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlock;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureModel;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureSize;
import com.thunder.wildernessodysseyapi.structuregen.validation.BlockStateResolver;
import com.thunder.wildernessodysseyapi.structuregen.validation.BlueprintValidator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression coverage for the tracked Blueprint v1 structure used by the generation task. */
class TestShelterBlueprintTest {

    @Test
    void trackedShelterParsesAndValidatesWithItsStatefulBlocksIntact() {
        Path fixture = projectRoot().resolve("src/main/structure_blueprints/test_shelter.json");
        StructureGenResult<BlueprintDocument> parsed = new BlueprintParser().parse(fixture);

        assertFalse(parsed.hasErrors(), () -> parsed.diagnostics().toString());
        assertNotNull(parsed.value());
        StructureGenResult<StructureModel> validated = new BlueprintValidator(
                (blockId, properties) -> BlockStateResolver.Resolution.valid()
        ).validate(parsed.value());

        assertFalse(validated.hasErrors(), () -> validated.diagnostics().toString());
        StructureModel model = validated.value();
        assertNotNull(model);
        assertEquals("test_shelter", model.name());
        assertEquals(new StructureSize(7, 5, 7), model.size());
        assertEquals(175, model.blocks().size());
        assertEquals(5L, model.blocks().stream().filter(StructureBlock::isExplicitAir).count());
        assertEquals(2L, count(model, "minecraft:oak_door"));
        assertEquals(2L, count(model, "minecraft:oak_stairs"));
        assertEquals(1L, count(model, "minecraft:lantern"));

        Set<String> doorHalves = blocks(model, "minecraft:oak_door").stream()
                .map(block -> block.state().properties().get("half"))
                .collect(Collectors.toSet());
        assertEquals(Set.of("lower", "upper"), doorHalves);
        assertTrue(blocks(model, "minecraft:oak_door").stream()
                .allMatch(block -> "south".equals(block.state().properties().get("facing"))));
        assertTrue(blocks(model, "minecraft:oak_stairs").stream()
                .allMatch(block -> "north".equals(block.state().properties().get("facing"))));
        assertTrue(blocks(model, "minecraft:lantern").getFirst().state().properties()
                .entrySet().containsAll(Set.of(
                        java.util.Map.entry("hanging", "true"),
                        java.util.Map.entry("waterlogged", "false")
                )));
        assertEquals(175L, model.blocks().stream().map(StructureBlock::position).distinct().count());
    }

    private long count(StructureModel model, String blockId) {
        return blocks(model, blockId).size();
    }

    private List<StructureBlock> blocks(StructureModel model, String blockId) {
        return model.blocks().stream()
                .filter(block -> blockId.equals(block.state().blockId()))
                .toList();
    }

    private Path projectRoot() {
        return Path.of(System.getProperty(
                "wildernessodysseyapi.projectDir",
                System.getProperty("user.dir")
        )).toAbsolutePath().normalize();
    }
}
