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

/** Guards World Type removal, legacy-save compatibility, and Studio assets. */
class DevelopmentStudioResourcesTest {

    @Test
    void worldTypeIsNotExposed() throws IOException {
        Path projectRoot = projectRoot();
        assertFalse(Files.exists(projectRoot.resolve(
                "src/main/resources/data/wildernessodysseyapi/worldgen/world_preset/development_studio.json"
        )));

        Path normalPresetTag = projectRoot.resolve(
                "src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json"
        );
        if (Files.exists(normalPresetTag)) {
            JsonArray values = JsonParser.parseString(Files.readString(normalPresetTag))
                    .getAsJsonObject()
                    .getAsJsonArray("values");
            assertFalse(values.asList().stream()
                    .anyMatch(value -> "wildernessodysseyapi:development_studio".equals(value.getAsString())));
        }

        JsonObject language = readJson("src/main/resources/assets/wildernessodysseyapi/lang/en_us.json");
        assertFalse(language.has("generator.wildernessodysseyapi.development_studio"));
    }

    @Test
    void legacyMarkerUsesNormalOverworldNoiseBehavior() throws IOException {
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

    @Test
    void campusBlueprintIsAnOperationalMultiFacilitySite() throws IOException {
        JsonObject campus = readJson(
                "src/main/structure_blueprints/development_studio_campus.json"
        );
        JsonArray size = campus.getAsJsonArray("size");
        assertEquals(65, size.get(0).getAsInt());
        assertEquals(15, size.get(1).getAsInt());
        assertEquals(65, size.get(2).getAsInt());
        assertEquals("2", campus.getAsJsonObject("metadata").get("campusVersion").getAsString());
        assertTrue(campus.getAsJsonArray("blocks").size() > 20_000);
        assertTrue(campus.getAsJsonArray("markers").asList().stream()
                .anyMatch(marker -> "campus_v2".equals(marker.getAsString())));
    }

    private static JsonObject readJson(String relativePath) throws IOException {
        return JsonParser.parseString(Files.readString(projectRoot().resolve(relativePath))).getAsJsonObject();
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty(
                "wildernessodysseyapi.projectDir",
                System.getProperty("user.dir")
        ));
    }
}
