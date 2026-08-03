package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the physical-optics and completed-scene composition paths in the surface shader. */
class WaterSurfaceOpticsShaderContractTest {

    @Test
    void capturedSceneResolvesCoverageBeforeOpaqueFramebufferReplacement() throws IOException {
        String fragment = readSurfaceFragment();
        int captureBranch = fragment.indexOf("if (capturedScene) {");
        int fallbackBranch = fragment.indexOf("} else {", captureBranch);

        assertTrue(captureBranch >= 0, "Missing captured-scene composition branch");
        assertTrue(fallbackBranch > captureBranch, "Missing no-capture fallback branch");
        String capturedComposition = fragment.substring(captureBranch, fallbackBranch);
        assertTrue(capturedComposition.contains(
                "completedColor = mix(capturedBackground, color, captureCoverage)"));
        assertTrue(fragment.contains("float opticalDensityScale ="));
        assertFalse(capturedComposition.contains("SurfaceOpacityStrength"),
                "Opacity control must not change geometric ownership coverage");
        assertTrue(capturedComposition.contains("fragColor = vec4(completedColor, 1.0)"));
        assertFalse(capturedComposition.contains("fallbackAlpha"),
                "Captured scene must not be alpha-composited a second time");
        assertTrue(fragment.substring(fallbackBranch).contains(
                "fragColor = vec4(fallbackColor, fallbackAlpha)"));
    }

    @Test
    void refractionUsesSnellLawAndMeasuresTheRefractedOpticalPath() throws IOException {
        String fragment = readSurfaceFragment();

        assertTrue(fragment.contains("const float AIR_TO_WATER_ETA = 1.0 / 1.333"));
        assertTrue(fragment.contains("faceforward(normal, incidentRay, normal)"));
        assertTrue(fragment.contains("refract(incidentRay, interfaceNormal, AIR_TO_WATER_ETA)"));
        assertTrue(fragment.contains("ProjMat * vec4(refractedEndpoint, 1.0)"));
        assertTrue(fragment.contains("candidateTravel = dot(candidateOffset, refractedRay)"));
        assertTrue(fragment.contains("candidateMiss <= candidateMissLimit"));
        assertTrue(fragment.contains("clamp(OpticalQuality.y, 0.0, 1.0)"));
        assertTrue(fragment.contains("thickness = clamp(candidateTravel, 0.0, 64.0)"));
        assertFalse(fragment.contains("length(sceneView) - length(viewPosition)"),
                "Optical thickness must be measured along the active ray");
    }

    @Test
    void foregroundTerrainOccludesDisplacedWaterWithStableViewSpaceTolerance() throws IOException {
        String fragment = readSurfaceFragment();
        int depthSample = fragment.indexOf("float sceneDepth =");
        int terrainRejection = fragment.indexOf(
                "sceneViewDepth + depthTolerance < surfaceViewDepth");
        int opticalDepth = fragment.indexOf("bool validDepth =", terrainRejection);

        assertTrue(depthSample >= 0, "Missing captured scene depth sample");
        assertTrue(terrainRejection > depthSample,
                "Foreground terrain must be compared after sampling captured depth");
        assertTrue(opticalDepth > terrainRejection,
                "Foreground rejection must run before behind-water optical depth");
        assertTrue(fragment.substring(terrainRejection, opticalDepth).contains("discard;"),
                "Foreground terrain must reject the hidden water fragment");
        assertTrue(fragment.contains(
                "float depthTolerance = max(0.015, surfaceViewDepth * 0.0005)"));
        assertTrue(fragment.contains(
                "sceneViewDepth > surfaceViewDepth + depthTolerance"));
        assertFalse(fragment.contains("sceneDepth > gl_FragCoord.z + 0.00001"),
                "Non-linear depth thresholds shimmer and lose precision at long range");
    }

    @Test
    void screenSpaceReflectionUsesBoundedRefinementAndConfidence() throws IOException {
        String fragment = readSurfaceFragment();

        assertTrue(fragment.contains("refinementIndex < 4"));
        assertTrue(fragment.contains("residualConfidence"));
        assertTrue(fragment.contains("continuityConfidence"));
        assertTrue(fragment.contains("distanceConfidence"));
        assertTrue(fragment.contains("screenEdgeConfidence(hitUv)"));
        assertTrue(fragment.contains("clamp(hitConfidence, 0.0, 1.0)"));
        assertTrue(fragment.contains("fresnel > 0.024"));
        assertTrue(fragment.contains("-reconstructViewPosition"));
        assertFalse(fragment.contains("return vec4(texture(SceneColor, uv).rgb, 1.0)"),
                "A raw binary SSR hit reintroduces edge popping and depth leaks");
    }

    @Test
    void environmentFallbackRespondsToDirectionAndWeather() throws IOException {
        String fragment = readSurfaceFragment();

        assertTrue(fragment.contains("dot(normalize(reflectionDirection)"));
        assertTrue(fragment.contains("float overcast ="));
        assertTrue(fragment.contains("float opticalRoughness ="));
        assertTrue(fragment.contains("float weatherVisibility ="));
    }

    @Test
    void capturedTerrainIsNotRelitOrRefoggedAsWaterMaterial() throws IOException {
        String fragment = readSurfaceFragment();

        assertTrue(fragment.contains("vec3 transmittedScene = sceneColor * transmission"));
        assertTrue(fragment.contains("vec3 foggedMaterial = mix(materialColor"));
        assertFalse(fragment.contains("color *= waterLighting"));
        assertTrue(fragment.contains("dot(halfVector, halfVector) > 0.000001"));
    }

    @Test
    void materialEnergySeparatesFresnelReflectionAbsorptionAndFoamCoverage() throws IOException {
        String fragment = readSurfaceFragment();

        assertTrue(fragment.contains("vec3 waterMaterialNumerator ="));
        assertTrue(fragment.contains(
                "mediumColor * (vec3(1.0) - transmission) * (1.0 - fresnel)"));
        assertTrue(fragment.contains("+ reflectedRadiance * fresnel"));
        assertTrue(fragment.contains("float foamCoverage ="));
        assertTrue(fragment.contains("waterMaterialWeight * (1.0 - foamCoverage)"));
        assertTrue(fragment.contains("* (1.0 - foamCoverage);"));
        assertFalse(fragment.contains("mix(absorbedColor, reflectedColor, fresnel)"),
                "Fresnel reflection must not be multiplied by material coverage twice");
    }

    private static String readSurfaceFragment() throws IOException {
        String path = "assets/wildernessodysseyapi/shaders/core/gerstner_water.fsh";
        try (InputStream input = WaterSurfaceOpticsShaderContractTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing test resource " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
