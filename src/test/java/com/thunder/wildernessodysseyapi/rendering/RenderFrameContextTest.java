package com.thunder.wildernessodysseyapi.rendering;

import com.thunder.wildernessodysseyapi.rendering.backend.RenderBackend;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RenderFrameContextTest {

    @Test
    void frameTimingPreservesFreshGpuSampleIdentity() {
        RenderFrameContext.FrameTiming timing = new RenderFrameContext.FrameTiming(
                14_000_000L,
                11_000_000L,
                13_000_000L,
                42L
        );

        assertEquals(14_000_000L, timing.cpuFrameNanos());
        assertEquals(11_000_000L, timing.gpuFrameNanos());
        assertEquals(13_000_000L, timing.movingAverageNanos());
        assertEquals(42L, timing.gpuSampleFrame());
    }

    @Test
    void unavailableGpuDurationCannotClaimACompletedSourceFrame() {
        RenderFrameContext.FrameTiming timing = new RenderFrameContext.FrameTiming(-2L, -4L, -6L, 99L);

        assertEquals(0L, timing.cpuFrameNanos());
        assertEquals(-1L, timing.gpuFrameNanos());
        assertEquals(0L, timing.movingAverageNanos());
        assertEquals(-1L, timing.gpuSampleFrame());
    }

    @Test
    void contextClampsBackendGenerationWithoutReplacingTheCapturedAdapter() {
        RenderFrameContext context = new RenderFrameContext(
                7L,
                RenderBackend.UNAVAILABLE,
                GPUCapabilities.UNAVAILABLE,
                EnvironmentState.CLEAR,
                null,
                RenderingQuality.HIGH,
                RenderFrameContext.FrameTiming.UNAVAILABLE,
                TemporalFrameData.unavailable(
                        TemporalFrameData.Resolution.ONE,
                        TemporalFrameData.Resolution.ONE,
                        0L
                ),
                -3L
        );

        assertSame(RenderBackend.UNAVAILABLE, context.backend());
        assertEquals(0L, context.backendGeneration());
    }
}
