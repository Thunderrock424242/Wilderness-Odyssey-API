package com.thunder.wildernessodysseyapi.worldgen.coast;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the data-pack contract for the six transition beach biomes. */
class CoastalBiomeResourcesTest {

    private static final List<String> BIOMES = List.of(
            "temperate_beach",
            "dune_beach",
            "rocky_coast",
            "cold_beach",
            "glacial_beach",
            "tropical_beach"
    );

    @Test
    void everyBeachUsesCompleteGenerationStagesAndTheSurfaceOwner() throws IOException {
        for (String biomeName : BIOMES) {
            JsonObject biome = readJson("src/main/resources/data/wildernessodysseyapi/"
                    + "worldgen/biome/" + biomeName + ".json");
            JsonArray stages = biome.getAsJsonArray("features");
            assertEquals(11, stages.size(), biomeName);
            assertTrue(contains(stages, "wildernessodysseyapi:coastal_terrain"), biomeName);
            assertTrue(contains(stages, "minecraft:freeze_top_layer"), biomeName);
            assertTrue(biome.getAsJsonObject("spawners").has("monster"), biomeName);
        }
    }

    @Test
    void vanillaBeachTagContainsEveryAuthoredVariant() throws IOException {
        JsonArray values = readJson(
                "src/main/resources/data/minecraft/tags/worldgen/biome/is_beach.json")
                .getAsJsonArray("values");
        assertEquals(
                BIOMES.stream().map(name -> "wildernessodysseyapi:" + name).toList(),
                values.asList().stream().map(element -> element.getAsString()).toList()
        );
    }

    @Test
    void coastalTerrainHasConfiguredAndPlacedResources() throws IOException {
        JsonObject configured = readJson("src/main/resources/data/wildernessodysseyapi/"
                + "worldgen/configured_feature/coastal_terrain.json");
        JsonObject placed = readJson("src/main/resources/data/wildernessodysseyapi/"
                + "worldgen/placed_feature/coastal_terrain.json");
        assertEquals("wildernessodysseyapi:coastal_terrain",
                configured.get("type").getAsString());
        assertEquals("wildernessodysseyapi:coastal_terrain",
                placed.get("feature").getAsString());
        assertEquals(3, placed.getAsJsonArray("placement").size());
    }

    private static boolean contains(JsonArray stages, String expected) {
        for (var stage : stages) {
            if (stage.getAsJsonArray().asList().stream()
                    .anyMatch(element -> expected.equals(element.getAsString()))) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject readJson(String relativePath) throws IOException {
        Path root = Path.of(System.getProperty(
                "wildernessodysseyapi.projectDir", System.getProperty("user.dir")));
        return JsonParser.parseString(Files.readString(root.resolve(relativePath))).getAsJsonObject();
    }
}
