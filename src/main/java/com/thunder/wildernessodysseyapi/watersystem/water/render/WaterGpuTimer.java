package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/**
 * Measures SSR-enabled optical water passes with asynchronous GPU timer queries.
 *
 * <p>A small ring prevents a result read from stalling the render thread. If no
 * completed query is available, diagnostics retain the last completed value.</p>
 */
public final class WaterGpuTimer {

    private static final int QUERY_COUNT = 4;
    private static final int[] QUERIES = new int[QUERY_COUNT];
    private static final boolean[] IN_FLIGHT = new boolean[QUERY_COUNT];
    private static int writeIndex;
    private static int activeIndex = -1;
    private static long latestNanos;

    private WaterGpuTimer() {
    }

    /** Starts a query when the next ring slot is available. */
    public static void begin() {
        RenderSystem.assertOnRenderThread();
        ensureQueries();
        pollCompleted();
        if (activeIndex >= 0 || IN_FLIGHT[writeIndex]) {
            return;
        }
        activeIndex = writeIndex;
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, QUERIES[activeIndex]);
    }

    /** Finishes the active query without waiting for its GPU result. */
    public static void end() {
        RenderSystem.assertOnRenderThread();
        if (activeIndex < 0) {
            return;
        }
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        IN_FLIGHT[activeIndex] = true;
        writeIndex = (activeIndex + 1) % QUERY_COUNT;
        activeIndex = -1;
    }

    /** Returns the most recently completed GPU duration in nanoseconds. */
    public static long latestNanos() {
        pollCompleted();
        return latestNanos;
    }

    /** Releases query objects while the client still owns its GL context. */
    public static void release() {
        RenderSystem.assertOnRenderThread();
        if (activeIndex >= 0) {
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            activeIndex = -1;
        }
        for (int index = 0; index < QUERY_COUNT; index++) {
            if (QUERIES[index] != 0) {
                GL15.glDeleteQueries(QUERIES[index]);
                QUERIES[index] = 0;
            }
            IN_FLIGHT[index] = false;
        }
        latestNanos = 0L;
        writeIndex = 0;
    }

    private static void ensureQueries() {
        if (QUERIES[0] != 0) {
            return;
        }
        for (int index = 0; index < QUERY_COUNT; index++) {
            QUERIES[index] = GL15.glGenQueries();
        }
    }

    private static void pollCompleted() {
        if (!RenderSystem.isOnRenderThread()) {
            return;
        }
        for (int index = 0; index < QUERY_COUNT; index++) {
            if (!IN_FLIGHT[index]
                    || GL15.glGetQueryObjecti(QUERIES[index], GL15.GL_QUERY_RESULT_AVAILABLE) == 0) {
                continue;
            }
            latestNanos = Math.max(0L, GL33.glGetQueryObjectui64(QUERIES[index], GL15.GL_QUERY_RESULT));
            IN_FLIGHT[index] = false;
        }
    }
}
