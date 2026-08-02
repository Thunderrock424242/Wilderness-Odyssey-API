package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(descriptor.contains("\"name\": \"ScatteringCoefficient\""));
        assertTrue(fragment.contains("texture(SceneColor"));
        assertTrue(fragment.contains("texture(SceneDepth"));
        assertTrue(fragment.contains("SceneCaptureValid > 0.5"));
        assertTrue(fragment.contains("exp(-absorption * travelDistance)"));
    }

    @Test
    void capturedSceneUsesWorldAnchoredDepthReconstruction() throws IOException {
        String descriptor = readResource(
                "assets/wildernessodysseyapi/shaders/core/underwater_optics.json");
        String fragment = readResource(
                "assets/wildernessodysseyapi/shaders/core/underwater_optics.fsh");

        assertTrue(descriptor.contains("\"name\": \"InverseProjMat\""));
        assertTrue(descriptor.contains("\"name\": \"ViewToWorldMat\""));
        assertTrue(descriptor.contains("\"name\": \"CameraAnchor\""));
        assertTrue(descriptor.contains("\"name\": \"SunDirection\""));
        assertTrue(fragment.contains("reconstructViewPosition(refractedUv, refractedDepth)"));
        assertTrue(fragment.contains("length(refractedViewPosition)"));
        assertTrue(fragment.contains("worldCausticField(worldPosition, lightDirection)"));
        assertTrue(fragment.contains("integrateSunShafts("));
        assertTrue(fragment.contains("effectQuality >= 2.0"));
        assertTrue(fragment.contains("receiverBeforeSurface"));
        assertFalse(fragment.contains("stableTimePhase"),
                "Fullscreen animation phases must be reduced once on the CPU");
        assertFalse(fragment.contains("linearizeDepth"),
                "radial Beer-Lambert distance must come from inverse-projected scene depth");
        assertFalse(fragment.contains("causticA = sin((distortedUv"),
                "the capture path must not restore camera-swimming UV caustics");
    }

    @Test
    void completedCaptureIsOpaqueAndFallbackAvoidsInventedSceneDepth() throws IOException {
        String fragment = readResource(
                "assets/wildernessodysseyapi/shaders/core/underwater_optics.fsh");

        assertTrue(fragment.contains("fragColor = vec4(mix(sceneColor"));
        assertTrue(fragment.contains("submersion), 1.0)"));
        assertTrue(fragment.contains("No same-frame water-stage capture"));
        assertTrue(fragment.contains("texture(Sampler0"));
    }

    @Test
    void descriptorExactlyMatchesDeclaredShaderUniforms() throws IOException {
        String descriptor = readResource(
                "assets/wildernessodysseyapi/shaders/core/underwater_optics.json");
        String vertex = readResource(
                "assets/wildernessodysseyapi/shaders/core/underwater_optics.vsh");
        String fragment = readResource(
                "assets/wildernessodysseyapi/shaders/core/underwater_optics.fsh");

        Set<String> descriptorNames = matches(
                descriptor,
                Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([A-Za-z0-9_]+)\\\"")
        );
        Set<String> shaderNames = matches(
                vertex + "\n" + fragment,
                Pattern.compile("uniform\\s+[A-Za-z0-9_]+\\s+([A-Za-z0-9_]+)\\s*;")
        );
        assertEquals(descriptorNames, shaderNames);
    }

    private static Set<String> matches(String source, Pattern pattern) {
        Set<String> values = new HashSet<>();
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
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
