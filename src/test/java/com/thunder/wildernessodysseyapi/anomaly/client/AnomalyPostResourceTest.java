package com.thunder.wildernessodysseyapi.anomaly.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnomalyPostResourceTest {
    private static final Path SHADERS = Path.of(System.getProperty(
            "wildernessodysseyapi.projectDir", System.getProperty("user.dir")))
            .resolve("src/main/resources/assets/wildernessodysseyapi/shaders");

    @Test
    void gradesThroughAnIndependentIntermediateTarget() throws IOException {
        JsonObject post = JsonParser.parseString(Files.readString(SHADERS.resolve("post/anomaly_atmosphere.json"))).getAsJsonObject();
        var passes = post.getAsJsonArray("passes");
        assertEquals(2, passes.size());
        assertEquals("minecraft:main", passes.get(0).getAsJsonObject().get("intarget").getAsString());
        assertEquals("anomaly_swap", passes.get(0).getAsJsonObject().get("outtarget").getAsString());
        assertEquals("anomaly_swap", passes.get(1).getAsJsonObject().get("intarget").getAsString());
        assertEquals("minecraft:main", passes.get(1).getAsJsonObject().get("outtarget").getAsString());
    }

    @Test
    void shaderHasOneSceneSampleAndExplicitMotionUniform() throws IOException {
        String fragment = Files.readString(SHADERS.resolve("program/anomaly_atmosphere.fsh"));
        assertEquals(1, fragment.split("texture\\(", -1).length - 1);
        assertTrue(fragment.contains("uniform float MotionAmount"));
        assertTrue(fragment.contains("uniform float GradeAmount"));
        assertTrue(fragment.contains("#version 150"));
    }
}
