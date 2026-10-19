package com.thunder.wildernessodysseyapi.rendering.performance;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class CloudDensityBoundsTest {
    @Test
    void shaderUsesTheProvenBoundOnlyBehindOptInUniform() throws Exception {
        try (var resource = CloudDensityBoundsTest.class.getResourceAsStream(
                "/assets/wildernessodysseyapi/shaders/core/raymarched_clouds.fsh")) {
            assertNotNull(resource, "The packaged cloud shader must be on the runtime classpath");
            String shader = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(shader.contains("if (DensityBoundsEnabled != 0)"));
            assertTrue(shader.contains("mediumShape * (1.0 - fineWeight)"));
            assertTrue(shader.contains("minimumDetail + fineWeight < threshold - edgeWidth - 0.00001"));
            assertTrue(shader.contains("minimumDetail > threshold + edgeWidth + 0.00001"));
        }
    }

    @Test
    void skippedFineNoiseCannotChangeDensityAcrossThresholds() {
        int skipped = 0;
        int total = 0;
        for (int strength = 0; strength <= 10; strength++) {
            for (int medium = 0; medium <= 100; medium++) {
                for (int threshold = 20; threshold <= 90; threshold++) {
                    double detail = strength / 10.0;
                    double center = threshold / 100.0;
                    double width = 0.12 + (0.075 - 0.12) * detail;
                    double base = medium / 100.0 * (1 - 0.24 * detail);
                    var result = classify(medium / 100.0, detail, center, width);
                    total++;
                    if (result == Result.EVALUATE_FINE) continue;
                    skipped++;
                    for (double fine : new double[] {0, 0.1, 0.5, 0.9, 1}) {
                        double full = smoothstep(center - width, center + width, base + fine * 0.24 * detail);
                        assertEquals(result == Result.EMPTY ? 0 : 1, full, 1.0e-7);
                    }
                }
            }
        }
        assertTrue(skipped > 0);
        System.out.printf("WAGE synthetic density grid: %d/%d fine-noise evaluations avoidable (arithmetic model, not GPU timing)%n",
                skipped, total);
    }

    @Test
    void transitionAndRoundingMarginKeepFullEvaluation() {
        assertEquals(Result.EVALUATE_FINE, classify(0.5, 1, 0.5, 0.1));
        assertEquals(Result.EVALUATE_FINE, classify(Double.NaN, 1, 0.5, 0.1));
        assertEquals(Result.EVALUATE_FINE, classify(0.4, 0, 0.5, 0.1));
    }

    private static double smoothstep(double low, double high, double value) {
        double t = Math.max(0, Math.min(1, (value - low) / (high - low)));
        return t * t * (3 - 2 * t);
    }

    // Executable arithmetic reference for the GLSL resource contract above.
    // This establishes the bound, not shader compilation or hardware equivalence.
    private static Result classify(double medium, double strength, double threshold, double width) {
        double fineWeight = 0.24 * strength;
        double minimum = medium * (1 - fineWeight);
        if (minimum + fineWeight < threshold - width - 0.00001) return Result.EMPTY;
        if (minimum > threshold + width + 0.00001) return Result.SATURATED;
        return Result.EVALUATE_FINE;
    }

    private enum Result { EMPTY, SATURATED, EVALUATE_FINE }
}
