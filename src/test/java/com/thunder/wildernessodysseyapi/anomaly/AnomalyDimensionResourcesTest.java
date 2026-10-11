package com.thunder.wildernessodysseyapi.anomaly;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.level.biome.Climate;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;

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
        assertTrue(contains(features.get(9).getAsJsonArray(), "minecraft:trees_taiga"));
        assertTrue(contains(features.get(10).getAsJsonArray(), "minecraft:freeze_top_layer"));
    }

    @Test
    void dimensionUsesNativeNoiseAndFiveClimateReachableZones() throws IOException {
        JsonObject dimension = readJson("src/main/resources/data/wildernessodysseyapi/dimension/anomaly_dimension.json");
        JsonObject generator = dimension.getAsJsonObject("generator");
        assertEquals("minecraft:overworld", generator.get("settings").getAsString());
        JsonObject source = generator.getAsJsonObject("biome_source");
        assertEquals("minecraft:multi_noise", source.get("type").getAsString());
        Set<String> zones = new HashSet<>();
        source.getAsJsonArray("biomes").forEach(entry -> zones.add(entry.getAsJsonObject().get("biome").getAsString()));
        assertEquals(Set.of("wildernessodysseyapi:anomaly_forest", "wildernessodysseyapi:anomaly_plains",
                "wildernessodysseyapi:anomaly_hills", "wildernessodysseyapi:floating_fringe",
                "wildernessodysseyapi:rift_core"), zones);
    }

    @Test
    void zonesShareFeatureOrderingAndRetainNaturalSpringsAndConifers() throws IOException {
        JsonArray reference = readJson("src/main/resources/data/wildernessodysseyapi/worldgen/biome/anomaly_forest.json")
                .getAsJsonArray("features");
        for (String path : List.of("anomaly_forest", "anomaly_plains", "anomaly_hills", "floating_fringe", "rift_core")) {
            JsonObject biome = readJson("src/main/resources/data/wildernessodysseyapi/worldgen/biome/" + path + ".json");
            JsonArray features = biome.getAsJsonArray("features");
            assertEquals(sharedFeatures(reference), sharedFeatures(features),
                    path + " must not introduce conflicting shared feature order");
            assertTrue(contains(features.get(8).getAsJsonArray(), "minecraft:spring_water"), path);
            assertTrue(contains(features.get(9).getAsJsonArray(), "minecraft:trees_taiga")
                    || contains(features.get(9).getAsJsonArray(), "wildernessodysseyapi:anomaly_conifers_sparse"), path);
            assertTrue(contains(features.get(10).getAsJsonArray(), "wildernessodysseyapi:anomaly_terrain"), path);
        }
    }

    @Test
    void nativeClimateSamplerActuallySelectsEveryZoneWithForestDominant() throws IOException {
        JsonArray entries = readJson("src/main/resources/data/wildernessodysseyapi/dimension/anomaly_dimension.json")
                .getAsJsonObject("generator").getAsJsonObject("biome_source").getAsJsonArray("biomes");
        List<Pair<Climate.ParameterPoint, String>> parameters = new ArrayList<>();
        for (var entry : entries) {
            JsonObject object = entry.getAsJsonObject();
            parameters.add(Pair.of(Climate.ParameterPoint.CODEC.parse(JsonOps.INSTANCE,
                    object.get("parameters")).getOrThrow(), object.get("biome").getAsString()));
        }
        Climate.ParameterList<String> sampler = new Climate.ParameterList<>(parameters);
        assertEquals("wildernessodysseyapi:anomaly_forest", sample(sampler, 0.0F, 0.0F));
        assertEquals("wildernessodysseyapi:anomaly_plains", sample(sampler, 0.9F, 0.0F));
        assertEquals("wildernessodysseyapi:anomaly_hills", sample(sampler, -0.9F, 0.0F));
        assertEquals("wildernessodysseyapi:floating_fringe", sample(sampler, 0.0F, 0.95F));
        assertEquals("wildernessodysseyapi:rift_core", sample(sampler, 0.0F, -0.95F));
        int forests = 0;
        for (int erosion = -10; erosion <= 10; erosion++) {
            for (int weirdness = -10; weirdness <= 10; weirdness++) {
                if (sample(sampler, erosion / 10.0F, weirdness / 10.0F).equals("wildernessodysseyapi:anomaly_forest")) {
                    forests++;
                }
            }
        }
        assertTrue(forests > 220, "The authored climate domain should be mostly forest");
    }

    @Test
    void newZonesRetainSparseNaturalConiferWildlife() throws IOException {
        for (String path : List.of("anomaly_forest", "anomaly_plains", "anomaly_hills", "floating_fringe", "rift_core")) {
            JsonArray spawns = readJson("src/main/resources/data/wildernessodysseyapi/worldgen/biome/" + path + ".json")
                    .getAsJsonObject("spawners").getAsJsonArray("creature");
            assertTrue(spawns.asList().stream().anyMatch(entry -> entry.getAsJsonObject().get("type").getAsString()
                    .equals("minecraft:rabbit")), path);
            assertTrue(spawns.asList().stream().allMatch(entry -> entry.getAsJsonObject().get("weight").getAsInt() <= 8), path);
        }
    }

    @Test
    void sparseConifersHaveAtMostOneAttemptAndUseVanillaTaigaTrees() throws IOException {
        JsonObject placement = readJson("src/main/resources/data/wildernessodysseyapi/worldgen/placed_feature/anomaly_conifers_sparse.json");
        assertEquals("minecraft:trees_taiga", placement.get("feature").getAsString());
        JsonObject count = placement.getAsJsonArray("placement").get(0).getAsJsonObject();
        assertEquals("minecraft:count", count.get("type").getAsString());
        JsonObject provider = count.getAsJsonObject("count");
        assertEquals("minecraft:weighted_list", provider.get("type").getAsString());
        int allWeight = 0;
        int treeWeight = 0;
        for (var entry : provider.getAsJsonArray("distribution")) {
            JsonObject value = entry.getAsJsonObject();
            int attempts = value.get("data").getAsInt();
            assertTrue(attempts == 0 || attempts == 1);
            int weight = value.get("weight").getAsInt();
            allWeight += weight;
            treeWeight += attempts * weight;
        }
        assertTrue(treeWeight > 0 && treeWeight / (double) allWeight <= 0.25);
    }

    private static String sample(Climate.ParameterList<String> sampler, float erosion, float weirdness) {
        return sampler.findValue(Climate.target(0.0F, 0.0F, 0.0F, erosion, 0.0F, weirdness));
    }

    private static List<List<String>> sharedFeatures(JsonArray features) {
        List<List<String>> stages = new ArrayList<>();
        for (var stage : features) {
            stages.add(stage.getAsJsonArray().asList().stream().map(element -> element.getAsString())
                    .filter(id -> !id.equals("minecraft:trees_taiga")
                            && !id.equals("wildernessodysseyapi:anomaly_conifers_sparse"))
                    .toList());
        }
        return stages;
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
