package com.thunder.wildernessodysseyapi.rendering.backend.opengl;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thunder.wildernessodysseyapi.rendering.backend.RenderBackend;
import org.lwjgl.opengl.ARBTimerQuery;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/** Non-blocking timestamp-query ring owned entirely by the OpenGL adapter. */
final class OpenGlGpuTimer implements RenderBackend.GpuTimer {

    private final int[] startQueries;
    private final int[] endQueries;
    private final boolean[] inFlight;
    private final boolean coreQueries;
    private int writeIndex;
    private int activeIndex = -1;
    private long latestNanos;
    private boolean available = true;

    OpenGlGpuTimer(int bufferedFrames) {
        this.startQueries = new int[bufferedFrames];
        this.endQueries = new int[bufferedFrames];
        this.inFlight = new boolean[bufferedFrames];
        this.coreQueries = GL.getCapabilities().OpenGL33;
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public void begin() {
        RenderSystem.assertOnRenderThread();
        if (!available) {
            return;
        }
        try {
            ensureQueries();
            pollCompleted();
            if (activeIndex >= 0 || inFlight[writeIndex]) {
                return;
            }
            queryCounter(startQueries[writeIndex]);
            activeIndex = writeIndex;
        } catch (RuntimeException failure) {
            available = false;
            activeIndex = -1;
        }
    }

    @Override
    public void end() {
        RenderSystem.assertOnRenderThread();
        if (!available || activeIndex < 0) {
            return;
        }
        try {
            queryCounter(endQueries[activeIndex]);
            inFlight[activeIndex] = true;
            writeIndex = (activeIndex + 1) % inFlight.length;
        } catch (RuntimeException failure) {
            available = false;
        } finally {
            activeIndex = -1;
        }
    }

    @Override
    public long latestNanos() {
        try {
            pollCompleted();
        } catch (RuntimeException failure) {
            available = false;
        }
        return latestNanos;
    }

    @Override
    public void close() {
        if (!RenderSystem.isOnRenderThread()) {
            return;
        }
        activeIndex = -1;
        for (int index = 0; index < inFlight.length; index++) {
            delete(startQueries, index);
            delete(endQueries, index);
            inFlight[index] = false;
        }
        writeIndex = 0;
        latestNanos = 0L;
        available = false;
    }

    private void ensureQueries() {
        if (startQueries[0] != 0) {
            return;
        }
        for (int index = 0; index < inFlight.length; index++) {
            startQueries[index] = GL15.glGenQueries();
            endQueries[index] = GL15.glGenQueries();
        }
    }

    private void pollCompleted() {
        if (!available || !RenderSystem.isOnRenderThread()) {
            return;
        }
        for (int index = 0; index < inFlight.length; index++) {
            if (!inFlight[index]
                    || GL15.glGetQueryObjecti(endQueries[index], GL15.GL_QUERY_RESULT_AVAILABLE) == 0) {
                continue;
            }
            long start = queryResult(startQueries[index]);
            long end = queryResult(endQueries[index]);
            if (end >= start) {
                latestNanos = end - start;
            }
            inFlight[index] = false;
        }
    }

    private void queryCounter(int query) {
        if (coreQueries) {
            GL33.glQueryCounter(query, GL33.GL_TIMESTAMP);
        } else {
            ARBTimerQuery.glQueryCounter(query, ARBTimerQuery.GL_TIMESTAMP);
        }
    }

    private long queryResult(int query) {
        return coreQueries
                ? GL33.glGetQueryObjectui64(query, GL15.GL_QUERY_RESULT)
                : ARBTimerQuery.glGetQueryObjectui64(query, GL15.GL_QUERY_RESULT);
    }

    private static void delete(int[] queries, int index) {
        if (queries[index] == 0) {
            return;
        }
        GL15.glDeleteQueries(queries[index]);
        queries[index] = 0;
    }
}
