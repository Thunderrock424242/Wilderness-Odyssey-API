package com.thunder.wildernessodysseyapi.weather.client.cloud;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the bounded density-integration contract shared by Java and GLSL. */
class RaymarchedCloudShaderContractTest {

    @Test
    void descriptorPublishesCameraDepthAndBoundedSampleControls() throws IOException {
        String descriptor = readResource(
                "assets/wildernessodysseyapi/shaders/core/raymarched_clouds.json");

        assertTrue(descriptor.contains("\"name\": \"CameraPosition\""));
        assertTrue(descriptor.contains("\"name\": \"RaymarchSteps\""));
        assertTrue(descriptor.contains("\"name\": \"WorldOrigin\""));
        assertTrue(descriptor.contains("\"name\": \"WindOffset\""));
    }

    @Test
    void fragmentUsesOneCameraFacingShellAndHardSampleCap() throws IOException {
        String fragment = readResource(
                "assets/wildernessodysseyapi/shaders/core/raymarched_clouds.fsh");

        assertTrue(fragment.contains("for (int sampleIndex = 0; sampleIndex < 64; sampleIndex++)"));
        assertTrue(fragment.contains("sampleIndex >= RaymarchSteps"));
        assertTrue(fragment.contains("CameraPosition.y < middle && topFace"));
        assertTrue(fragment.contains("transmittance *= 1.0 - sampleAlpha"));
        assertTrue(fragment.contains("densityAt(point, base, depth, coverage, morphology)"));
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = RaymarchedCloudShaderContractTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing test resource " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
