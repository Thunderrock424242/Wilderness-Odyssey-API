package com.thunder.wildernessodysseyapi.environment.glacial;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the data-pack contract for the connected polar biome family. */
class PolarGlacialResourcesTest {

    private static final List<String> BIOMES = List.of(
            "iceberg_coast",
            "glacial_meltwater_valley",
            "polar_glacial_basin",
            "glacial_highlands",
            "polar_ice_sheet"
    );
    private static final List<String> FEATURES = List.of(
            "glacial_terrain",
            "glacial_river",
            "glacial_crevasse",
            "glacial_ice_cave",
            "glacial_waterfall",
            "iceberg_formation"
    );

    @Test
    void allBiomesAreColdBarrenAndUseCompleteGenerationStages() throws IOException {
        for (String biomeName : BIOMES) {
            JsonObject biome = readJson("src/main/resources/data/wildernessodysseyapi/worldgen/biome/"
                    + biomeName + ".json");
            assertTrue(biome.get("temperature").getAsDouble() < 0.0, biomeName);
            assertEquals(11, biome.getAsJsonArray("features").size(), biomeName);
            JsonObject spawners = biome.getAsJsonObject("spawners");
            for (String group : spawners.keySet()) {
                assertEquals(0, spawners.getAsJsonArray(group).size(), biomeName + ":" + group);
            }
            assertTrue(containsAny(biome.getAsJsonArray("features"),
                    "wildernessodysseyapi:glacial_terrain"), biomeName);
            assertTrue(containsAny(biome.getAsJsonArray("features"),
                    "minecraft:freeze_top_layer"), biomeName);
        }
    }

    @Test
    void everyCustomGeneratorHasConfiguredAndPlacedData() throws IOException {
        for (String featureName : FEATURES) {
            JsonObject configured = readJson(
                    "src/main/resources/data/wildernessodysseyapi/worldgen/configured_feature/"
                            + featureName + ".json");
            JsonObject placed = readJson(
                    "src/main/resources/data/wildernessodysseyapi/worldgen/placed_feature/"
                            + featureName + ".json");
            assertEquals("wildernessodysseyapi:" + featureName,
                    configured.get("type").getAsString());
            assertEquals("wildernessodysseyapi:" + featureName,
                    placed.get("feature").getAsString());
            assertTrue(placed.getAsJsonArray("placement").size() >= 3);
        }
    }

    @Test
    void familyTagContainsTheFiveStableBiomeIdsInCoastToInteriorOrder() throws IOException {
        JsonArray values = readJson(
                "src/main/resources/data/wildernessodysseyapi/tags/worldgen/biome/is_glacial.json")
                .getAsJsonArray("values");
        assertEquals(BIOMES.stream().map(name -> "wildernessodysseyapi:" + name).toList(),
                values.asList().stream().map(element -> element.getAsString()).toList());
    }

    @Test
    void everyFamilyMemberHasADistinctHighContrastGlacialColorGrade() throws IOException {
        Set<Integer> fogColors = new HashSet<>();
        Set<Integer> waterColors = new HashSet<>();
        for (String biomeName : BIOMES) {
            JsonObject effects = readJson(
                    "src/main/resources/data/wildernessodysseyapi/worldgen/biome/"
                            + biomeName + ".json"
            ).getAsJsonObject("effects");
            int fog = effects.get("fog_color").getAsInt();
            int water = effects.get("water_color").getAsInt();
            fogColors.add(fog);
            waterColors.add(water);
            assertTrue(channel(water, 0) > channel(water, 16) + 120, biomeName);
            assertTrue(effects.get("water_fog_color").getAsInt() < 0x0A0000, biomeName);
        }
        assertEquals(BIOMES.size(), fogColors.size());
        assertEquals(BIOMES.size(), waterColors.size());
    }

    private static boolean containsAny(JsonArray stages, String target) {
        for (var stage : stages) {
            if (stage.getAsJsonArray().asList().stream()
                    .anyMatch(element -> target.equals(element.getAsString()))) {
                return true;
            }
        }
        return false;
    }

    private static int channel(int color, int shift) {
        return color >>> shift & 0xFF;
    }

    private static JsonObject readJson(String relativePath) throws IOException {
        Path root = Path.of(System.getProperty(
                "wildernessodysseyapi.projectDir", System.getProperty("user.dir")));
        return JsonParser.parseString(Files.readString(root.resolve(relativePath))).getAsJsonObject();
    }
}
