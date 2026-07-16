package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UnderwaterShaderContractTest {

    @Test
    void shaderDeclaresAndSamplesTheSharedSceneCapture() throws IOException {
        String descriptor = readResource(
                "assets/wildernessodysseyapi/shaders/core/underwater_optics.json");
        String fragment = readResource(
                "assets/wildernessodysseyapi/shaders/core/underwater_optics.fsh");

        assertTrue(descriptor.contains("\"name\": \"SceneColor\""));
        assertTrue(descriptor.contains("\"name\": \"SceneDepth\""));
        assertTrue(descriptor.contains("\"name\": \"SceneCaptureValid\""));
        assertTrue(descriptor.contains("\"name\": \"AbsorptionCoefficients\""));
        assertTrue(fragment.contains("texture(SceneColor"));
        assertTrue(fragment.contains("texture(SceneDepth"));
        assertTrue(fragment.contains("SceneCaptureValid > 0.5"));
        assertTrue(fragment.contains("exp(-max(AbsorptionCoefficients"));
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = UnderwaterShaderContractTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing test resource " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
