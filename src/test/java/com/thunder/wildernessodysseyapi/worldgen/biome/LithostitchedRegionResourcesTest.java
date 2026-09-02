package com.thunder.wildernessodysseyapi.worldgen.biome;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Guards the stable Lithostitched regions and the data entry that activates injector callbacks. */
class LithostitchedRegionResourcesTest {

    @Test
    void anomalyAndPolarRegionsTargetTheOverworldWithTheirEstablishedWeights() throws IOException {
        assertRegion("anomaly_overworld", 3);
        assertRegion("polar_glacial_region", 12);
    }

    @Test
    void dataRegistryContainsAUsableBiomeInjectorBootstrap() throws IOException {
        String injectorId = "anomaly_forest_from_forest";
        JsonObject injector = readJson("src/main/resources/data/wildernessodysseyapi/"
                + "lithostitched/biome_injector/" + injectorId + ".json");

        assertEquals("lithostitched:replace_partially", injector.get("type").getAsString(), injectorId);
        assertEquals("minecraft:overworld", injector.get("dimension").getAsString(), injectorId);
        assertEquals(500, injector.get("priority").getAsInt(), injectorId);
        assertEquals(1, injector.getAsJsonArray("targets").size(), injectorId);
        assertEquals("minecraft:forest", injector.getAsJsonArray("targets").get(0).getAsString(), injectorId);
        assertEquals("wildernessodysseyapi:anomaly_forest", injector.get("replacement").getAsString(), injectorId);
        assertEquals(0, injector.getAsJsonObject("parameters").size(), injectorId);
        assertEquals("wildernessodysseyapi:anomaly_overworld", injector.get("region").getAsString(), injectorId);
    }

    private static void assertRegion(String name, int expectedWeight) throws IOException {
        JsonObject region = readJson("src/main/resources/data/wildernessodysseyapi/"
                + "lithostitched/region/" + name + ".json");
        assertEquals("minecraft:overworld", region.get("dimension").getAsString(), name);
        assertEquals(expectedWeight, region.get("weight").getAsInt(), name);
    }

    private static JsonObject readJson(String relativePath) throws IOException {
        Path root = Path.of(System.getProperty(
                "wildernessodysseyapi.projectDir", System.getProperty("user.dir")));
        return JsonParser.parseString(Files.readString(root.resolve(relativePath))).getAsJsonObject();
    }
}
