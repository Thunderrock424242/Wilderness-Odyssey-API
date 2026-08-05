package com.thunder.wildernessodysseyapi.anomaly;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the data-pack pieces that make the Anomaly a playable dimension. */
class AnomalyDimensionResourcesTest {

    @Test
    void dimensionTypeMatchesOverworldNoiseHeightAndUsesFixedNight() throws IOException {
        JsonObject dimensionType = readJson("src/main/resources/data/wildernessodysseyapi/dimension_type/anomaly_dimension.json");

        assertEquals(18_000, dimensionType.get("fixed_time").getAsInt());
        assertEquals(-64, dimensionType.get("min_y").getAsInt());
        assertEquals(384, dimensionType.get("height").getAsInt());
        assertEquals(384, dimensionType.get("logical_height").getAsInt());
    }

    @Test
    void biomeHasRiftOnlyMonstersAndCompleteForestFeatureStages() throws IOException {
        JsonObject biome = readJson("src/main/resources/data/wildernessodysseyapi/worldgen/biome/anomaly_forest.json");
        JsonArray monsters = biome.getAsJsonObject("spawners").getAsJsonArray("monster");
        Set<String> entityIds = new HashSet<>();
        monsters.forEach(element -> entityIds.add(element.getAsJsonObject().get("type").getAsString()));

        assertEquals(Set.of(
                "wildernessodysseyapi:riftborn",
                "wildernessodysseyapi:rift_listener",
                "wildernessodysseyapi:riftbound_wraith"
        ), entityIds);

        JsonArray features = biome.getAsJsonArray("features");
        assertEquals(11, features.size());
        assertTrue(contains(features.get(6).getAsJsonArray(), "minecraft:ore_diamond"));
        assertTrue(contains(features.get(9).getAsJsonArray(), "minecraft:trees_birch_and_oak"));
        assertTrue(contains(features.get(10).getAsJsonArray(), "minecraft:freeze_top_layer"));
    }

    private static JsonObject readJson(String relativePath) throws IOException {
        Path projectRoot = Path.of(System.getProperty(
                "wildernessodysseyapi.projectDir",
                System.getProperty("user.dir")
        ));
        return JsonParser.parseString(Files.readString(projectRoot.resolve(relativePath))).getAsJsonObject();
    }

    private static boolean contains(JsonArray array, String value) {
        return array.asList().stream().anyMatch(element -> value.equals(element.getAsString()));
    }
}
