package com.thunder.wildernessodysseyapi.anomaly.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

    @Test
    void finalCopyRetainsSkyColorEvenWhenTheSceneAlphaIsZero() throws IOException {
        JsonObject post = JsonParser.parseString(Files.readString(SHADERS.resolve("post/anomaly_atmosphere.json")))
                .getAsJsonObject();
        var passes = post.getAsJsonArray("passes");
        String programId = passes.get(passes.size() - 1).getAsJsonObject().get("name").getAsString();
        JsonObject program = readProgram(programId);
        assertEquals(ResourceLocation.withDefaultNamespace("blit"),
                ResourceLocation.parse(program.get("fragment").getAsString()), "The final pass only copies the graded scene");

        JsonObject blend = program.getAsJsonObject("blend");
        // PostPass clears its output to black before drawing. Zero-alpha sky
        // pixels still carry visible RGB, so blending must copy them in full.
        double[] sky = {0.60, 0.72, 0.80};
        for (double alpha : new double[]{0.0, 0.25, 1.0}) {
            double sourceFactor = blend == null ? 1.0 : blendFactor(blend.get("srcrgb").getAsString(), alpha);
            for (double channel : sky) {
                assertEquals(channel, channel * sourceFactor, 1.0E-8,
                        "Cleared-target copy lost sky color at scene alpha " + alpha);
            }
        }
        var color = program.getAsJsonArray("uniforms").asList().stream()
                .map(entry -> entry.getAsJsonObject())
                .filter(uniform -> "ColorModulate".equals(uniform.get("name").getAsString()))
                .findFirst().orElseThrow();
        for (var value : color.getAsJsonArray("values")) {
            assertEquals(1.0, value.getAsDouble(), "The copy must not tint or fade the scene again");
        }
    }

    private static double blendFactor(String factor, double alpha) {
        return switch (factor) {
            case "one" -> 1.0;
            case "zero" -> 0.0;
            case "srcalpha" -> alpha;
            case "1-srcalpha" -> 1.0 - alpha;
            default -> throw new AssertionError("Unsupported copy blend factor: " + factor);
        };
    }

    private static JsonObject readProgram(String id) throws IOException {
        String[] parts = id.split(":", 2);
        String path = "shaders/program/" + parts[1] + ".json";
        if ("wildernessodysseyapi".equals(parts[0])) {
            return JsonParser.parseString(Files.readString(SHADERS.resolve("program/" + parts[1] + ".json")))
                    .getAsJsonObject();
        }
        String resource = "assets/" + parts[0] + "/" + path;
        try (InputStream input = AnomalyPostResourceTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing copy program " + resource);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
