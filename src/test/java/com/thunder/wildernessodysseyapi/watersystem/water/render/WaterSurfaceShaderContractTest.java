package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the active surface shader inputs that Java uploads and packs. */
class WaterSurfaceShaderContractTest {

    @Test
    void descriptorDeclaresSpectrumAndBoundedImpulseUniforms() throws IOException {
        String descriptor = readResource(
                "assets/wildernessodysseyapi/shaders/core/gerstner_water.json");

        assertTrue(descriptor.contains("\"name\": \"SpectrumState\""));
        assertTrue(descriptor.contains("\"name\": \"WindSpeed\""));
        assertTrue(descriptor.contains("\"name\": \"ImpulseCount\""));
        assertTrue(descriptor.contains("\"name\": \"ImpulsePosition7\""));
        assertTrue(descriptor.contains("\"name\": \"ImpulseShape7\""));
    }

    @Test
    void vertexShaderDecodesMetadataBeforeVaryingInterpolation() throws IOException {
        String vertex = readResource(
                "assets/wildernessodysseyapi/shaders/core/gerstner_water.vsh");
        String fragment = readResource(
                "assets/wildernessodysseyapi/shaders/core/gerstner_water.fsh");

        assertTrue(vertex.contains("localCurrent = vec2("));
        assertTrue(vertex.contains("decodeSignedPayload(encodedColor.r)"));
        assertTrue(vertex.contains("shoreFactor = decodeUnitPayload(encodedColor.b)"));
        assertTrue(vertex.contains("vertexColor = vec4("));
        assertTrue(fragment.contains("in vec2 localCurrent;"));
        assertFalse(fragment.contains("decodeSignedPayload"));
    }

    @Test
    void shaderUsesSharedSpectrumAndMaterialSurfaceResponses() throws IOException {
        String vertex = readResource(
                "assets/wildernessodysseyapi/shaders/core/gerstner_water.vsh");
        String fragment = readResource(
                "assets/wildernessodysseyapi/shaders/core/gerstner_water.fsh");

        assertTrue(vertex.contains("SpectrumState.z * (0.35 + shape.w * 0.65)"));
        assertTrue(vertex.contains("mix(SpectrumState.x, SpectrumState.y, shape.w)"));
        assertTrue(vertex.contains("float horizontalScale = shape.z * amplitude"));
        assertTrue(vertex.contains("displacedPosition.xz += horizontalDisplacement"));
        assertTrue(vertex.contains("cross(tangentZ, tangentX)"));
        assertTrue(vertex.contains("shoreHorizontalTaper"));
        assertTrue(vertex.contains("accumulateImpulse(worldXZ"));
        assertTrue(fragment.contains("advectedPosition = position - current * GameTime"));
        assertTrue(fragment.contains("float shoreBreaker = shoreFactor"));
        assertTrue(fragment.contains("float impulseFoam = disturbanceStrength"));
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = WaterSurfaceShaderContractTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing test resource " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
