package com.thunder.wildernessodysseyapi.rendering;

import com.thunder.wildernessodysseyapi.rendering.backend.RenderBackend;
import com.thunder.wildernessodysseyapi.weather.client.WeatherVisualState;

/** Immutable state shared by water, weather, diagnostics, and future integrations for one frame. */
public record RenderFrameContext(
        long frameIndex,
        RenderBackend backend,
        GPUCapabilities gpuCapabilities,
        EnvironmentState environment,
        WeatherVisualState weatherVisualState,
        RenderingQuality quality,
        FrameTiming timing,
        TemporalFrameData temporalData
) {
    public static final RenderFrameContext EMPTY = new RenderFrameContext(
            0L,
            RenderBackend.UNAVAILABLE,
            GPUCapabilities.UNAVAILABLE,
            EnvironmentState.CLEAR,
            WeatherVisualState.CLEAR,
            RenderingQuality.CINEMATIC,
            FrameTiming.UNAVAILABLE,
            TemporalFrameData.unavailable(
                    TemporalFrameData.Resolution.ONE,
                    TemporalFrameData.Resolution.ONE,
                    0L
            )
    );

    public RenderFrameContext {
        frameIndex = Math.max(0L, frameIndex);
        backend = backend == null ? RenderBackend.UNAVAILABLE : backend;
        gpuCapabilities = gpuCapabilities == null ? backend.capabilities() : gpuCapabilities;
        environment = environment == null ? EnvironmentState.CLEAR : environment;
        weatherVisualState = weatherVisualState == null ? WeatherVisualState.CLEAR : weatherVisualState;
        quality = quality == null ? RenderingQuality.CINEMATIC : quality;
        timing = timing == null ? FrameTiming.UNAVAILABLE : timing;
        temporalData = temporalData == null
                ? TemporalFrameData.unavailable(
                        TemporalFrameData.Resolution.ONE,
                        TemporalFrameData.Resolution.ONE,
                        timing.cpuFrameNanos()
                )
                : temporalData;
    }

    /** Retains the original framework construction shape for focused callers. */
    public RenderFrameContext(
            long frameIndex,
            RenderBackend backend,
            GPUCapabilities gpuCapabilities,
            EnvironmentState environment,
            RenderingQuality quality,
            FrameTiming timing,
            TemporalFrameData temporalData
    ) {
        this(
                frameIndex,
                backend,
                gpuCapabilities,
                environment,
                WeatherVisualState.CLEAR,
                quality,
                timing,
                temporalData
        );
    }

    /** CPU timing is always optional; GPU timing remains unavailable until a full-frame hook exists. */
    public record FrameTiming(long cpuFrameNanos, long gpuFrameNanos, long movingAverageNanos) {
        public static final FrameTiming UNAVAILABLE = new FrameTiming(0L, -1L, 0L);

        public FrameTiming {
            cpuFrameNanos = Math.max(0L, cpuFrameNanos);
            gpuFrameNanos = Math.max(-1L, gpuFrameNanos);
            movingAverageNanos = Math.max(0L, movingAverageNanos);
        }
    }
}
