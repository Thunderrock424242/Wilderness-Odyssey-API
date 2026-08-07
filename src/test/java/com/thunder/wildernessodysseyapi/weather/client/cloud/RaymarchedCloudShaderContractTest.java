package com.thunder.wildernessodysseyapi.weather.client.cloud;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the bounded density-integration contract shared by Java and GLSL. */
class RaymarchedCloudShaderContractTest {

    @Test
    void descriptorPublishesContinuousFieldsAndBoundedSampleControls() throws IOException {
        String descriptor = readResource(
                "assets/wildernessodysseyapi/shaders/core/raymarched_clouds.json");

        assertTrue(descriptor.contains("\"name\": \"CloudFieldPrevious\""));
        assertTrue(descriptor.contains("\"name\": \"CloudFieldCurrent\""));
        assertTrue(descriptor.contains("\"name\": \"CameraPosition\""));
        assertTrue(descriptor.contains("\"name\": \"RaymarchSteps\""));
        assertTrue(descriptor.contains("\"name\": \"LightingSteps\""));
        assertTrue(descriptor.contains("\"name\": \"PreviousNearField\""));
        assertTrue(descriptor.contains("\"name\": \"CurrentDistantField\""));
        assertTrue(descriptor.contains("\"name\": \"RenderOrigin\""));
        assertTrue(descriptor.contains("\"name\": \"WindOffset\""));
        assertFalse(descriptor.contains("\"name\": \"WorldOrigin\""));
    }

    @Test
    void fragmentInterpolatesAtlasVolumesWithHardPrimaryAndLightingCaps() throws IOException {
        String fragment = readResource(
                "assets/wildernessodysseyapi/shaders/core/raymarched_clouds.fsh");

        assertTrue(fragment.contains("texture(fieldTexture, uv)"));
        assertTrue(fragment.contains("mix(previous, current, clamp(FieldBlend"));
        assertTrue(fragment.contains("sampleMorphology(worldXZ, distant, band)"));
        assertTrue(fragment.contains("intersectBox(CameraPosition, rayDirection"));
        assertTrue(fragment.contains("for (int sampleIndex = 0; sampleIndex < 64; sampleIndex++)"));
        assertTrue(fragment.contains("sampleIndex >= RaymarchSteps"));
        assertTrue(fragment.contains("for (int index = 0; index < 6; index++)"));
        assertTrue(fragment.contains("index >= LightingSteps"));
        assertTrue(fragment.contains("worldXZ + WindOffset"));
        assertTrue(fragment.contains("transmittance *= 1.0 - sampleAlpha"));
        assertTrue(fragment.contains("densityAt(point, distant, band, storm)"));
        assertFalse(fragment.contains("CameraPosition.y < middle && topFace"));
        assertFalse(fragment.contains("worldXZ + vec2(GameTime"));
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
