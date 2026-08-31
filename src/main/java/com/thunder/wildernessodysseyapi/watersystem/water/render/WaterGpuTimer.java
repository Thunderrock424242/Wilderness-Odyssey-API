package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.rendering.backend.RenderBackend;

import java.util.Optional;

/**
 * Water-facing facade for the active backend's asynchronous GPU timer.
 *
 * <p>OpenGL query ownership lives in the OpenGL backend adapter, so water
 * diagnostics no longer depend directly on LWJGL. A future backend can supply
 * equivalent timestamps without changing this renderer.</p>
 */
public final class WaterGpuTimer {

    private static final int BUFFERED_FRAMES = 4;
    private static RenderBackend timerBackend = RenderBackend.UNAVAILABLE;
    private static RenderBackend.GpuTimer timer = RenderBackend.GpuTimer.UNAVAILABLE;

    private WaterGpuTimer() {
    }

    public static void begin(RenderBackend backend, long sourceFrame) {
        timer(backend).begin(sourceFrame);
    }

    public static void end() {
        timer().end();
    }

    public static long latestNanos() {
        return timer.latestNanos();
    }

    /** Returns one newly completed water timing sample at most once. */
    public static Optional<RenderBackend.GpuTimingSample> poll() {
        return timer.poll();
    }

    /** Releases backend resources while Minecraft still owns its graphics context. */
    public static void release() {
        timer.close();
        timer = RenderBackend.GpuTimer.UNAVAILABLE;
        timerBackend = RenderBackend.UNAVAILABLE;
    }

    private static RenderBackend.GpuTimer timer(RenderBackend activeBackend) {
        activeBackend = activeBackend == null ? RenderBackend.UNAVAILABLE : activeBackend;
        if (activeBackend == timerBackend) {
            return timer;
        }
        timer.close();
        timerBackend = activeBackend;
        timer = activeBackend.createGpuTimer(BUFFERED_FRAMES);
        return timer;
    }
}
