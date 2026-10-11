package com.thunder.wildernessodysseyapi.worldgen.anomaly;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Guards source templates before the normal StructureGen task publishes NBT. */
class AnomalyIslandBlueprintTest {
    @Test
    void threeSmallVariantsUseBoundedVanillaContentWithoutFluidsOrBlockEntities() throws Exception {
        Path root = Path.of(System.getProperty("wildernessodysseyapi.projectDir", System.getProperty("user.dir")));
        for (int variant = 1; variant <= 3; variant++) {
            String name = "anomaly_island_small_0" + variant;
            Path path = root.resolve("src/main/structure_blueprints/" + name + ".json");
            assertTrue(Files.isRegularFile(path), name + " must have a source blueprint");
            JsonObject blueprint = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            assertEquals(name, blueprint.get("name").getAsString());
            JsonArray size = blueprint.getAsJsonArray("size");
            assertTrue(size.get(0).getAsInt() <= 12 && size.get(2).getAsInt() <= 12, name);
            assertTrue(size.get(1).getAsInt() <= 16, name);
            Set<String> occupied = new HashSet<>();
            for (var entry : blueprint.getAsJsonArray("blocks")) {
                JsonObject block = entry.getAsJsonObject();
                String id = block.get("block").getAsString();
                assertTrue(id.startsWith("minecraft:"), name);
                assertFalse(Set.of("minecraft:water", "minecraft:lava", "minecraft:chest",
                        "minecraft:barrel", "minecraft:spawner").contains(id), name);
                assertFalse(block.has("blockEntitySnbt"), name);
                if (id.equals("minecraft:spruce_leaves")) {
                    assertEquals("true", block.getAsJsonObject("properties").get("persistent").getAsString(), name);
                }
                JsonArray position = block.getAsJsonArray("pos");
                for (int axis = 0; axis < 3; axis++) {
                    assertTrue(position.get(axis).getAsInt() >= 0
                            && position.get(axis).getAsInt() < size.get(axis).getAsInt(), name);
                }
                assertTrue(occupied.add(position.toString()), name + " contains duplicate blocks");
            }
            assertFalse(occupied.isEmpty(), name);
        }
    }
}
