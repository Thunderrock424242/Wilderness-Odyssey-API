package com.thunder.wildernessodysseyapi.watersystem.ocean.coast;

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

/** Guards resource-pack tuning and stable crash sound alternatives. */
class CoastalClientResourcesTest {

    private static final List<String> PROFILE_NAMES = List.of(
            "temperate", "dune", "rocky", "cold", "glacial", "tropical");
    private static final List<String> PROFILE_FIELDS = List.of(
            "waveHeightMultiplier",
            "waveFrequencyMultiplier",
            "breakerDistanceBlocks",
            "breakerStrength",
            "runUpDistanceBlocks",
            "retreatSpeed",
            "foamAmount",
            "crashSoundVolume",
            "crashSoundRadiusBlocks",
            "turbulence",
            "shorelineWetnessDurationTicks"
    );

    @Test
    void everyShoreHasACompleteReloadableWaveProfile() throws IOException {
        JsonObject profiles = readJson("src/main/resources/assets/wildernessodysseyapi/"
                + "coastal_wave_profiles.json");
        assertEquals(PROFILE_NAMES, profiles.keySet().stream().toList());
        for (String profileName : PROFILE_NAMES) {
            JsonObject profile = profiles.getAsJsonObject(profileName);
            assertEquals(PROFILE_FIELDS, profile.keySet().stream().toList(), profileName);
            PROFILE_FIELDS.forEach(field -> assertTrue(
                    profile.get(field).isJsonPrimitive(), profileName + ":" + field));
        }
    }

    @Test
    void builtInProfilesMatchThePackagedDefaults() throws IOException {
        JsonObject profiles = readJson("src/main/resources/assets/wildernessodysseyapi/"
                + "coastal_wave_profiles.json");
        for (var type : CoastalWaveProfile.ShoreType.values()) {
            var expected = CoastalWaveProfile.forType(type);
            JsonObject actual = profiles.getAsJsonObject(type.name().toLowerCase(java.util.Locale.ROOT));
            float[] values = {
                    expected.waveHeightMultiplier(), expected.waveFrequencyMultiplier(),
                    expected.breakerDistanceBlocks(), expected.breakerStrength(),
                    expected.runUpDistanceBlocks(), expected.retreatSpeed(), expected.foamAmount(),
                    expected.crashSoundVolume(), expected.crashSoundRadiusBlocks(),
                    expected.turbulence(), expected.shorelineWetnessDurationTicks()
            };
            for (int index = 0; index < PROFILE_FIELDS.size(); index++) {
                String field = PROFILE_FIELDS.get(index);
                assertEquals(values[index], actual.get(field).getAsFloat(), 0.0001f,
                        type + ":" + field);
            }
        }
    }

    @Test
    void everyCrashAlternativeIsAnAudibleWaterImpact() throws IOException {
        JsonObject sounds = readJson(
                "src/main/resources/assets/wildernessodysseyapi/sounds.json");
        for (String soundName : List.of(
                "coast_wash_soft", "coast_break", "coast_break_rocky", "coast_break_storm")) {
            JsonArray alternatives = sounds.getAsJsonObject(soundName).getAsJsonArray("sounds");
            assertTrue(alternatives.size() > 0, soundName);
            alternatives.forEach(alternative -> {
                JsonObject sound = alternative.getAsJsonObject();
                assertEquals("event", sound.get("type").getAsString(), soundName);
                assertTrue(List.of("minecraft:entity.generic.splash",
                        "minecraft:entity.player.splash.high_speed").contains(
                        sound.get("name").getAsString()), soundName);
                assertTrue(sound.get("volume").getAsFloat() >= 0.85f, soundName);
            });
        }
    }

    private static JsonObject readJson(String relativePath) throws IOException {
        Path root = Path.of(System.getProperty(
                "wildernessodysseyapi.projectDir", System.getProperty("user.dir")));
        return JsonParser.parseString(Files.readString(root.resolve(relativePath))).getAsJsonObject();
    }
}
