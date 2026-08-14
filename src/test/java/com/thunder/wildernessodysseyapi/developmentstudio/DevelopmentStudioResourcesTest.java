package com.thunder.wildernessodysseyapi.developmentstudio;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the data-driven World Type contract and natural Overworld delegation. */
class DevelopmentStudioResourcesTest {

    @Test
    void presetUsesNormalOverworldNoiseAndBiomeSource() throws IOException {
        JsonObject preset = readJson(
                "src/main/resources/data/wildernessodysseyapi/worldgen/world_preset/development_studio.json"
        );
        JsonObject dimensions = preset.getAsJsonObject("dimensions");
        JsonObject overworld = dimensions.getAsJsonObject("minecraft:overworld");
        JsonObject generator = overworld.getAsJsonObject("generator");
        JsonObject biomeSource = generator.getAsJsonObject("biome_source");

        assertEquals("minecraft:overworld", overworld.get("type").getAsString());
        assertEquals("minecraft:noise", generator.get("type").getAsString());
        assertEquals("wildernessodysseyapi:development_studio", generator.get("settings").getAsString());
        assertEquals("minecraft:multi_noise", biomeSource.get("type").getAsString());
        assertEquals("minecraft:overworld", biomeSource.get("preset").getAsString());
        assertTrue(dimensions.has("minecraft:the_nether"));
        assertTrue(dimensions.has("minecraft:the_end"));
    }

    @Test
    void generatedMarkerUsesNormalOverworldNoiseBehavior() throws IOException {
        JsonObject settings = readJson(
                "src/generated/resources/data/wildernessodysseyapi/worldgen/noise_settings/development_studio.json"
        );

        JsonObject noise = settings.getAsJsonObject("noise");
        assertEquals(-64, noise.get("min_y").getAsInt());
        assertEquals(384, noise.get("height").getAsInt());
        assertEquals(63, settings.get("sea_level").getAsInt());
        assertTrue(settings.get("aquifers_enabled").getAsBoolean());
        assertTrue(settings.get("ore_veins_enabled").getAsBoolean());
        assertFalse(settings.get("disable_mob_generation").getAsBoolean());
        assertFalse(settings.get("legacy_random_source").getAsBoolean());
        assertTrue(settings.has("noise_router"));
        assertTrue(settings.has("surface_rule"));
        assertTrue(settings.has("spawn_target"));
    }

    @Test
    void normalPresetTagAndTranslationExposeWorldTypeInCreateWorld() throws IOException {
        JsonObject tag = readJson(
                "src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json"
        );
        JsonArray values = tag.getAsJsonArray("values");
        assertTrue(values.asList().stream()
                .anyMatch(value -> "wildernessodysseyapi:development_studio".equals(value.getAsString())));

        JsonObject language = readJson("src/main/resources/assets/wildernessodysseyapi/lang/en_us.json");
        assertEquals(
                "Wilderness Odyssey Development Studio",
                language.get("generator.wildernessodysseyapi.development_studio").getAsString()
        );
    }

    @Test
    void phaseTwoLabFixtureFitsTheRegisteredStructurePad() throws IOException {
        JsonObject fixture = readJson(
                "src/main/structure_blueprints/development_studio_lab_fixture.json"
        );
        JsonArray size = fixture.getAsJsonArray("size");
        assertEquals(5, size.get(0).getAsInt());
        assertEquals(4, size.get(1).getAsInt());
        assertEquals(5, size.get(2).getAsInt());
        assertEquals("development_studio_lab_fixture", fixture.get("name").getAsString());
        assertFalse(fixture.getAsJsonArray("blocks").isEmpty());
    }

    private static JsonObject readJson(String relativePath) throws IOException {
        Path projectRoot = Path.of(System.getProperty(
                "wildernessodysseyapi.projectDir",
                System.getProperty("user.dir")
        ));
        return JsonParser.parseString(Files.readString(projectRoot.resolve(relativePath))).getAsJsonObject();
    }
}
