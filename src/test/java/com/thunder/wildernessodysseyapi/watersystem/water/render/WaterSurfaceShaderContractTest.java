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
        assertTrue(descriptor.contains("\"name\": \"RegionalSeaStateEnabled\""));
        assertTrue(descriptor.contains("\"name\": \"RegionalSeaStateCorners\""));
        assertTrue(descriptor.contains("\"name\": \"RegionalSpectrumCorners\""));
        assertTrue(descriptor.contains("\"name\": \"ChunkOrigin\""));
        assertTrue(descriptor.contains("\"name\": \"ImpulseCount\""));
        assertTrue(descriptor.contains("\"name\": \"ImpulseChunkIndex\""));
        assertTrue(descriptor.contains("\"name\": \"ImpulsePosition7\""));
        assertTrue(descriptor.contains("\"name\": \"ImpulseShape7\""));
        assertFalse(descriptor.contains("\"name\": \"TimeFrameLow\""));
        assertFalse(descriptor.contains("\"name\": \"TimeFrameHigh\""));
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
    void blockAtlasCannotCreateOrAnimateSurfaceCoverage() throws IOException {
        String vertex = readResource(
                "assets/wildernessodysseyapi/shaders/core/gerstner_water.vsh");
        String fragment = readResource(
                "assets/wildernessodysseyapi/shaders/core/gerstner_water.fsh");

        assertTrue(vertex.contains("texCoord0 = UV0;"),
                "The stock fallback interface must receive the unmodified block UV");
        assertTrue(vertex.indexOf("texCoord0 = UV0;") == vertex.lastIndexOf("texCoord0 ="),
                "The block UV must have one exact passthrough assignment and no animated replacement");
        assertFalse(vertex.contains("texCoord0 = UV0 +"),
                "World-space water animation must never offset block-atlas UVs");
        assertFalse(fragment.contains("waterTexture.a"),
                "Atlas alpha must not decide whether custom water geometry exists");
        assertFalse(fragment.contains("texture(Sampler0, texCoord0).a"),
                "Direct atlas-alpha coverage tests can reopen block-shaped holes");
        assertTrue(fragment.contains("texture(Sampler0, texCoord0).rgb"),
                "The no-capture path should retain stock water-texture color compatibility");
        assertTrue(fragment.indexOf("texture(Sampler0, texCoord0).rgb")
                        == fragment.lastIndexOf("texture(Sampler0"),
                "Sampler0 may only provide RGB to the safe no-capture fallback");
    }

    @Test
    void shaderUsesSharedSpectrumAndMaterialSurfaceResponses() throws IOException {
        String vertex = readResource(
                "assets/wildernessodysseyapi/shaders/core/gerstner_water.vsh");
        String fragment = readResource(
                "assets/wildernessodysseyapi/shaders/core/gerstner_water.fsh");

        assertTrue(vertex.contains("float directionalEnergy = mix(1.0, alignedEnergy, regionalSpectrum.z)"));
        assertTrue(vertex.contains("mix(regionalSpectrum.x, regionalSpectrum.y, shape.w)"));
        assertTrue(vertex.contains("vec2 direction = baseDirection"));
        assertFalse(vertex.contains("mix(baseDirection, wind"));
        assertTrue(vertex.contains("float horizontalScale = shape.z * amplitude"));
        assertTrue(vertex.contains("displacedPosition.xz += horizontalDisplacement"));
        assertTrue(vertex.contains("cross(tangentZ, tangentX)"));
        assertTrue(vertex.contains("shoreHorizontalTaper"));
        assertTrue(vertex.contains("accumulateImpulse(localXZ"));
        assertTrue(vertex.contains("relativeChunkOrigin - impulse.xy"));
        assertTrue(vertex.contains("float persistentFoam = max(ring, center * 0.30) * shape.w"));
        assertTrue(vertex.contains("length(gradient) * 0.20 + persistentFoam"));
        assertTrue(vertex.contains("bodyWeight <= 0.000001 || shape.x <= 0.000001"));
        assertTrue(vertex.contains("flowRelativeDirection(baseDirection, flowDirection)"));
        assertTrue(vertex.contains("stableLinearPhase(localXZ"));
        assertTrue(vertex.contains("phaseLocalXZ = localXZ;"));
        assertTrue(vertex.contains("phaseChunkIndex = ChunkOrigin / PHASE_CHUNK_SPAN"));
        assertTrue(vertex.contains("resolveRegionalOceanState(localXZ, frameSeaState, frameSpectrum)"));
        assertTrue(vertex.contains("mix(corners[0], corners[3], blend.x)"));
        assertTrue(vertex.contains("mix(corners[1], corners[2], blend.x)"));
        assertTrue(vertex.contains("regionalSeaState = sea;"));
        assertTrue(fragment.contains("flat in vec2 phaseChunkIndex"));
        assertTrue(fragment.contains("in float regionalSeaState;"));
        assertTrue(fragment.contains("in vec2 regionalWindDirection;"));
        assertTrue(vertex.contains("regionalWindSpeed = max(0.0, frameSeaState.w);"));
        assertTrue(fragment.contains("in float regionalWindSpeed;"));
        assertTrue(fragment.contains("float windDetailScale = mix(0.88, 1.14, windEnergy);"));
        assertTrue(fragment.contains("in vec4 regionalSpectrumState;"));
        assertFalse(fragment.contains("uniform float SeaState;"));
        assertFalse(fragment.contains("uniform vec2 WindDirection;"));
        assertFalse(fragment.contains("uniform vec4 SpectrumState;"));
        assertTrue(fragment.contains("animatedStablePhase"));
        assertTrue(fragment.contains("phaseStableDirectionalWeight"));
        assertTrue(fragment.contains("phaseBandLimit"));
        assertTrue(fragment.contains(") * 0.66;"));
        assertFalse(fragment.contains(") * (0.42 + sea * 0.48)"));
        assertTrue(fragment.contains("stableWorldPhase(SHORE_BREAK_DIRECTION"));
        assertFalse(fragment.contains("stableTimePhase"));
        assertFalse(fragment.contains("currentAdvectionPhase"));
        assertFalse(vertex.contains("stableTimePhase(0.24 + WindSpeed"));
        assertFalse(vertex.contains("stableTimePhase(0.7 + length(localCurrent)"));
        assertFalse(vertex.contains("stableTimePhase"));
        assertFalse(vertex.contains("TimeFrameLow"));
        assertFalse(vertex.contains("TimeFrameHigh"));
        assertFalse(fragment.contains("dot(worldPosition.xz"));
        assertTrue(fragment.contains("float shoreBreaker = shoreFactor"));
        assertTrue(fragment.contains("float impulseFoam = disturbanceStrength"));
        assertTrue(vertex.contains("float waveFreedom = 1.0 - frozen * 0.94"));
        assertTrue(fragment.contains("float iceCoverage = smoothstep"));
        assertTrue(fragment.contains("Weather.w"));
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
