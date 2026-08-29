package com.thunder.wildernessodysseyapi.rendering.performance;

import com.thunder.wildernessodysseyapi.rendering.RenderingQuality;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdaptiveQualityControllerTest {

    @Test
    void sustainedSlowFramesReduceQualityWithinConfiguredBounds() {
        AdaptiveQualityController controller = new AdaptiveQualityController();
        AdaptiveQualityController.Policy policy = new AdaptiveQualityController.Policy(
                true,
                16_670_000L,
                RenderingQuality.MEDIUM,
                RenderingQuality.CINEMATIC,
                0L
        );

        long now = 0L;
        for (int frame = 0; frame < 180; frame++) {
            now += 16_670_000L;
            controller.recordFrame(30_000_000L, now, policy);
        }

        assertEquals(RenderingQuality.MEDIUM, controller.quality());
    }

    @Test
    void cooldownPreventsImmediateRepeatedChanges() {
        AdaptiveQualityController controller = new AdaptiveQualityController();
        AdaptiveQualityController.Policy policy = new AdaptiveQualityController.Policy(
                true,
                16_670_000L,
                RenderingQuality.LOW,
                RenderingQuality.CINEMATIC,
                5_000_000_000L
        );

        long now = 0L;
        for (int frame = 0; frame < 61; frame++) {
            now += 100_000_000L;
            controller.recordFrame(30_000_000L, now, policy);
        }
        assertEquals(RenderingQuality.HIGH, controller.quality());

        controller.recordFrame(30_000_000L, now + 1_000_000L, policy);
        assertEquals(RenderingQuality.HIGH, controller.quality());
    }

    @Test
    void disabledPolicyRestoresNativeCeiling() {
        AdaptiveQualityController controller = new AdaptiveQualityController();
        controller.recordFrame(40_000_000L, 1L, AdaptiveQualityController.Policy.DISABLED);

        assertEquals(RenderingQuality.CINEMATIC, controller.quality());
    }
}
