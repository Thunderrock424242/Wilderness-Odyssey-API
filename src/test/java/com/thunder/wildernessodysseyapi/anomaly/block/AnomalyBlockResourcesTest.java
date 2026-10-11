package com.thunder.wildernessodysseyapi.anomaly.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the packaged resources players see and receive when mining anomaly terrain. */
class AnomalyBlockResourcesTest {
    private static final String NAMESPACE = "wildernessodysseyapi:";
    private static final List<String> BLOCKS = List.of(
            "distorted_stone", "distorted_grass", "distorted_dirt", "distorted_moss",
            "distorted_cliff_stone", "unstable_rift_stone", "anomaly_crystal_block", "fractured_energy_block"
    );

    @Test
    void everyTerrainBlockResolvesItsBlockItemAndTextures() throws IOException {
        for (String block : BLOCKS) {
            JsonObject blockstate = readJson("assets/wildernessodysseyapi/blockstates/" + block + ".json");
            String blockModel = blockstate.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString();
            assertEquals(NAMESPACE + "block/" + block, blockModel);

            JsonObject item = readJson("assets/wildernessodysseyapi/models/item/" + block + ".json");
            assertEquals(blockModel, item.get("parent").getAsString());

            JsonObject model = readJson("assets/wildernessodysseyapi/models/block/" + block + ".json");
            assertTrue(Set.of("minecraft:block/cube_all", "minecraft:block/cube_bottom_top")
                    .contains(model.get("parent").getAsString()), "Terrain should remain an ordinary culled cube: " + block);
            for (var texture : model.getAsJsonObject("textures").entrySet()) {
                String location = texture.getValue().getAsString();
                assertTrue(location.startsWith(NAMESPACE + "block/"), "Texture must be supplied by this mod: " + location);
                assertNaturalTexture(location.substring(NAMESPACE.length()));
            }
        }
    }

    @Test
    void minedTerrainDropsItsOwnItemAndHonorsExplosionDecay() throws IOException {
        for (String block : BLOCKS) {
            JsonObject loot = readJson("data/wildernessodysseyapi/loot_table/blocks/" + block + ".json");
            assertEquals("minecraft:block", loot.get("type").getAsString());
            JsonObject pool = loot.getAsJsonArray("pools").get(0).getAsJsonObject();
            assertEquals(1, pool.get("rolls").getAsInt());
            JsonObject entry = pool.getAsJsonArray("entries").get(0).getAsJsonObject();
            assertEquals("minecraft:item", entry.get("type").getAsString());
            assertEquals(NAMESPACE + block, entry.get("name").getAsString());
            assertTrue(pool.getAsJsonArray("conditions").asList().stream()
                    .anyMatch(condition -> "minecraft:survives_explosion".equals(
                            condition.getAsJsonObject().get("condition").getAsString())));
        }
    }

    @Test
    void terrainSupportsItsNaturalToolsWithoutReplacingExistingTags() throws IOException {
        assertTagContains("mineable/pickaxe", Set.of(
                "anomaly_ore", "anomaly_gateway", "distorted_stone", "distorted_cliff_stone",
                "unstable_rift_stone", "anomaly_crystal_block", "fractured_energy_block"));
        assertTagContains("mineable/shovel", Set.of("distorted_grass", "distorted_dirt"));
        assertTagContains("mineable/hoe", Set.of("distorted_moss"));
        assertTagContains("needs_iron_tool", Set.of("unstable_rift_stone", "fractured_energy_block"));
        assertTagContains("dirt", Set.of("distorted_grass", "distorted_dirt", "distorted_moss"));

        Set<String> diamondRequired = tagValues("needs_diamond_tool");
        assertTrue(diamondRequired.containsAll(Set.of(NAMESPACE + "anomaly_ore", NAMESPACE + "anomaly_gateway")));
        assertFalse(diamondRequired.contains(NAMESPACE + "distorted_stone"));
        assertFalse(diamondRequired.contains(NAMESPACE + "distorted_cliff_stone"));
    }

    private static void assertTagContains(String tag, Set<String> blocks) throws IOException {
        Set<String> entries = tagValues(tag);
        for (String block : blocks) {
            assertTrue(entries.contains(NAMESPACE + block), "Missing tool entry " + block + " in " + tag);
        }
    }

    private static Set<String> tagValues(String tag) throws IOException {
        JsonObject json = readJson("data/minecraft/tags/block/" + tag + ".json");
        assertFalse(json.get("replace").getAsBoolean(), "Tool tags must preserve other mods' entries");
        JsonArray values = json.getAsJsonArray("values");
        Set<String> entries = new HashSet<>();
        values.forEach(value -> entries.add(value.getAsString()));
        return entries;
    }

    private static void assertNaturalTexture(String path) throws IOException {
        Path texture = resourceRoot().resolve("assets/wildernessodysseyapi/textures/" + path + ".png");
        assertTrue(Files.isRegularFile(texture), "Missing texture " + texture);
        BufferedImage image = ImageIO.read(texture.toFile());
        assertNotNull(image, "Texture must decode as PNG: " + texture);
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());

        int energyPixels = 0;
        double luminance = 0.0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                assertEquals(255, pixel >>> 24, "Terrain should stay opaque and culled");
                int red = (pixel >>> 16) & 255;
                int green = (pixel >>> 8) & 255;
                int blue = pixel & 255;
                luminance += red * 0.2126 + green * 0.7152 + blue * 0.0722;
                if ((blue > red + 35 && blue > 110) || (red > green + 35 && red > blue + 35 && red > 110)) {
                    energyPixels++;
                }
            }
        }
        assertTrue(luminance / 256.0 < 115.0, "Natural/dark surfaces must dominate " + texture);
        assertTrue(energyPixels <= 64, "Energy should remain sparse cracks rather than a blanket: " + texture);
    }

    private static JsonObject readJson(String path) throws IOException {
        Path resource = resourceRoot().resolve(path);
        assertTrue(Files.isRegularFile(resource), "Missing resource " + resource);
        return JsonParser.parseString(Files.readString(resource)).getAsJsonObject();
    }

    private static Path resourceRoot() {
        return Path.of(System.getProperty("wildernessodysseyapi.projectDir", System.getProperty("user.dir")))
                .resolve("src/main/resources");
    }
}
