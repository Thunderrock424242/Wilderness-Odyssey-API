package com.thunder.wildernessodysseyapi.worldgen.biome;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Guards the stable Lithostitched region identities and relative weights. */
class LithostitchedRegionResourcesTest {

    @Test
    void anomalyAndPolarRegionsTargetTheOverworldWithTheirEstablishedWeights() throws IOException {
        assertRegion("anomaly_overworld", 3);
        assertRegion("polar_glacial_region", 12);
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
