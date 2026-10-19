package com.thunder.wildernessodysseyapi.wage.performance;

import com.thunder.wildernessodysseyapi.wage.GPUCapabilities;
import com.thunder.wildernessodysseyapi.wage.TemporalFrameData.Resolution;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveGpuControllerTest {
    @Test
    void samplesExpireByElapsedTimeEvenWithinEightSlowFrames() {
        AdaptiveGpuController controller = warm(POLICY);
        assertTrue(controller.snapshot().active());
        for (int frame = 101; frame <= 111; frame++) {
            controller.observe(new AdaptiveGpuController.Observation(frame,
                    1_600_000_000L + (frame - 100) * 100_000_000L, 1, RESOLUTION,
                    CAPABLE, false, 100_000_000, 15_000_000, frame - 8,
                    8_000_000, frame - 8, 0.4, 0), POLICY);
        }
        assertFalse(controller.snapshot().active());
        assertEquals(AdaptiveGpuController.Reason.STALE_TIMING, controller.snapshot().reason());
    }

    @Test
    void timingFailureRejectsTheCurrentDrawBeforeShaderAdmission() {
        AdaptiveGpuController controller = warm(POLICY);
        assertTrue(controller.snapshot().active());
        assertFalse(controller.tryAcquireCloud(100, false));
        assertEquals(AdaptiveGpuController.Reason.STALE_TIMING, controller.snapshot().reason());
        assertFalse(controller.tryAcquireCloud(100, true));
        assertEquals(1, controller.snapshot().fallbacks());
        controller.observe(normal(101), POLICY);
        assertEquals(AdaptiveGpuController.Reason.WARMUP, controller.snapshot().reason());
    }
    private static final GPUCapabilities CAPABLE = new GPUCapabilities(
            GPUCapabilities.GraphicsApi.OPENGL, "any vendor", "device", "driver", 16L << 30,
            GPUCapabilities.MemoryEvidence.DEDICATED_TOTAL, 16384, 8,
            Set.of(GPUCapabilities.Feature.SHADER_PROGRAMS, GPUCapabilities.Feature.GPU_TIMER_QUERIES));
    private static final Resolution RESOLUTION = new Resolution(1920, 1080);
    private static final AdaptiveGpuController.Policy POLICY = new AdaptiveGpuController.Policy(
            true, AdaptiveGpuController.Mode.AUTOMATIC, 16_000_000L, 0L);

    @Test
    void sustainedMeasuredCloudPressureEnablesOneBoundedAdmissionPerFrame() {
        AdaptiveGpuController controller = warm(POLICY);
        assertTrue(controller.snapshot().active());
        assertTrue(controller.tryAcquireCloud(100));
        assertFalse(controller.tryAcquireCloud(100));
        assertFalse(controller.tryAcquireCloud(99));
        assertEquals(1, controller.snapshot().accepted());
        assertEquals(2, controller.snapshot().rejected());
    }

    @Test
    void hardwareCapacityWithoutFreshTimingNeverEnablesAcceleration() {
        AdaptiveGpuController controller = new AdaptiveGpuController();
        for (int frame = 1; frame <= 100; frame++) {
            controller.observe(observation(frame, 1, RESOLUTION, false, -1, -1, 0.4, 0), POLICY);
        }
        assertFalse(controller.snapshot().active());
        assertEquals(AdaptiveGpuController.Reason.STALE_TIMING, controller.snapshot().reason());
    }

    @Test
    void repeatedAndFutureSamplesDoNotSatisfyWarmup() {
        AdaptiveGpuController controller = new AdaptiveGpuController();
        for (int frame = 1; frame <= 70; frame++) {
            controller.observe(observation(frame, 1, RESOLUTION, false, 1, 1, 0.4, 0), POLICY);
        }
        assertFalse(controller.snapshot().active());
        controller.observe(observation(71, 1, RESOLUTION, false, 72, 72, 0.4, 0), POLICY);
        assertEquals(AdaptiveGpuController.Reason.STALE_TIMING, controller.snapshot().reason());
    }

    @Test
    void cooldownPreventsActivationAndSafetyFallbackBypassesIt() {
        var policy = new AdaptiveGpuController.Policy(true, AdaptiveGpuController.Mode.PERFORMANCE,
                16_000_000L, 5_000_000_000L);
        AdaptiveGpuController controller = warm(policy);
        assertFalse(controller.snapshot().active());
        for (int frame = 101; frame <= 400; frame++) controller.observe(normal(frame), policy);
        assertTrue(controller.snapshot().active());
        controller.observe(observation(401, 1, RESOLUTION, true, 400, 400, 0.4, 0), policy);
        assertFalse(controller.snapshot().active());
        assertEquals(AdaptiveGpuController.Reason.EXTERNAL_SHADERS, controller.snapshot().reason());
        assertEquals(1, controller.snapshot().fallbacks());
    }

    @Test
    void heapAndGcPressureImmediatelyUseExistingRenderer() {
        AdaptiveGpuController controller = warm(POLICY);
        controller.observe(observation(101, 1, RESOLUTION, false, 100, 100, 0.95, 0), POLICY);
        assertFalse(controller.snapshot().active());
        assertEquals(AdaptiveGpuController.Reason.MEMORY_PRESSURE, controller.snapshot().reason());
        controller = warm(POLICY);
        controller.observe(observation(101, 1, RESOLUTION, false, 100, 100, 0.4, 0.25), POLICY);
        assertFalse(controller.snapshot().active());
    }

    @Test
    void backendAndResolutionChangesRequireFreshWarmup() {
        AdaptiveGpuController controller = warm(POLICY);
        controller.observe(observation(101, 2, RESOLUTION, false, 100, 100, 0.4, 0), POLICY);
        assertFalse(controller.snapshot().active());
        assertEquals(AdaptiveGpuController.Reason.STALE_TIMING, controller.snapshot().reason());
        controller.observe(observation(102, 2, RESOLUTION, false, 101, 101, 0.4, 0), POLICY);
        assertEquals(AdaptiveGpuController.Reason.WARMUP, controller.snapshot().reason());
        controller = warm(POLICY);
        controller.observe(observation(101, 1, new Resolution(1280, 720), false, 100, 100, 0.4, 0), POLICY);
        assertFalse(controller.snapshot().active());
    }

    @Test
    void disableAndResetDiscardPreviousAdmissionsAndSamples() {
        AdaptiveGpuController controller = warm(POLICY);
        controller.observe(normal(101), AdaptiveGpuController.Policy.DISABLED);
        assertFalse(controller.tryAcquireCloud(101));
        assertEquals(AdaptiveGpuController.Reason.DISABLED, controller.snapshot().reason());
        controller.reset();
        assertEquals(0, controller.snapshot().accepted());
        controller.observe(normal(102), POLICY);
        assertEquals(AdaptiveGpuController.Reason.STALE_TIMING, controller.snapshot().reason());
        controller.observe(normal(103), POLICY);
        assertEquals(AdaptiveGpuController.Reason.WARMUP, controller.snapshot().reason());
    }

    @Test
    void unsupportedTimerAndShaderCapabilitiesFallBack() {
        AdaptiveGpuController controller = new AdaptiveGpuController();
        var observation = new AdaptiveGpuController.Observation(1, 16_000_000L, 1, RESOLUTION,
                GPUCapabilities.UNAVAILABLE, false, 20_000_000, 15_000_000, 1,
                8_000_000, 1, 0.4, 0);
        controller.observe(observation, POLICY);
        assertEquals(AdaptiveGpuController.Reason.UNSUPPORTED_BACKEND, controller.snapshot().reason());
    }

    @Test
    void idleWorkAndModeSpecificBudgetsDoNotUseUniversalGpuThreshold() {
        AdaptiveGpuController controller = new AdaptiveGpuController();
        for (int frame = 1; frame <= 100; frame++) {
            controller.observe(new AdaptiveGpuController.Observation(frame, frame * 16_000_000L, 1,
                    RESOLUTION, CAPABLE, false, 8_000_000, 5_000_000, frame,
                    1_000_000, frame, -1, -1), POLICY);
        }
        assertFalse(controller.snapshot().active());
        long automaticBudget = controller.snapshot().cloudBudgetNanos();
        controller.observe(normal(101), new AdaptiveGpuController.Policy(
                true, AdaptiveGpuController.Mode.QUALITY, 16_000_000, 0));
        assertTrue(controller.snapshot().cloudBudgetNanos() > automaticBudget);
    }

    private static AdaptiveGpuController warm(AdaptiveGpuController.Policy policy) {
        AdaptiveGpuController controller = new AdaptiveGpuController();
        for (int frame = 1; frame <= 100; frame++) controller.observe(normal(frame), policy);
        return controller;
    }

    private static AdaptiveGpuController.Observation normal(int frame) {
        return observation(frame, 1, RESOLUTION, false, frame - 1, frame - 1, 0.4, 0);
    }

    private static AdaptiveGpuController.Observation observation(int frame, long generation,
            Resolution resolution, boolean shaders, long gpuSource, long cloudSource, double heap, double gc) {
        return new AdaptiveGpuController.Observation(frame, frame * 16_000_000L, generation, resolution,
                CAPABLE, shaders, 20_000_000, 15_000_000, gpuSource, 8_000_000, cloudSource, heap, gc);
    }
}
