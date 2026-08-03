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
    private static final int[] START_QUERIES = new int[QUERY_COUNT];
    private static final int[] END_QUERIES = new int[QUERY_COUNT];
    private static final boolean[] IN_FLIGHT = new boolean[QUERY_COUNT];
    private static int writeIndex;
    private static int activeIndex = -1;
    private static long latestNanos;

    private WaterGpuTimer() {
    }

    /**
     * Records the start timestamp when the next ring slot is available.
     *
     * <p>Timestamp pairs are intentionally used instead of a
     * {@code GL_TIME_ELAPSED} begin/end scope. OpenGL permits only one active
     * elapsed-time query per target, so a nested profiler supplied by Sodium,
     * a shader mod, or a driver tool would otherwise make both owners corrupt
     * each other's query lifecycle.</p>
     */
    public static void begin() {
        RenderSystem.assertOnRenderThread();
        ensureQueries();
        pollCompleted();
        if (activeIndex >= 0 || IN_FLIGHT[writeIndex]) {
            return;
        }
        GL33.glQueryCounter(START_QUERIES[writeIndex], GL33.GL_TIMESTAMP);
        activeIndex = writeIndex;
    }

    /** Records the end timestamp without waiting for its GPU result. */
    public static void end() {
        RenderSystem.assertOnRenderThread();
        if (activeIndex < 0) {
            return;
        }
        GL33.glQueryCounter(END_QUERIES[activeIndex], GL33.GL_TIMESTAMP);
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
        activeIndex = -1;
        for (int index = 0; index < QUERY_COUNT; index++) {
            deleteQuery(START_QUERIES, index);
            deleteQuery(END_QUERIES, index);
            IN_FLIGHT[index] = false;
        }
        latestNanos = 0L;
        writeIndex = 0;
    }

    private static void ensureQueries() {
        if (START_QUERIES[0] != 0) {
            return;
        }
        for (int index = 0; index < QUERY_COUNT; index++) {
            START_QUERIES[index] = GL15.glGenQueries();
            END_QUERIES[index] = GL15.glGenQueries();
        }
    }

    private static void pollCompleted() {
        if (!RenderSystem.isOnRenderThread()) {
            return;
        }
        for (int index = 0; index < QUERY_COUNT; index++) {
            if (!IN_FLIGHT[index]
                    || GL15.glGetQueryObjecti(END_QUERIES[index], GL15.GL_QUERY_RESULT_AVAILABLE) == 0) {
                continue;
            }
            long start = GL33.glGetQueryObjectui64(START_QUERIES[index], GL15.GL_QUERY_RESULT);
            long end = GL33.glGetQueryObjectui64(END_QUERIES[index], GL15.GL_QUERY_RESULT);
            if (end >= start) {
                latestNanos = end - start;
            }
            IN_FLIGHT[index] = false;
        }
    }

    private static void deleteQuery(int[] queries, int index) {
        if (queries[index] == 0) {
            return;
        }
        GL15.glDeleteQueries(queries[index]);
        queries[index] = 0;
    }
}
