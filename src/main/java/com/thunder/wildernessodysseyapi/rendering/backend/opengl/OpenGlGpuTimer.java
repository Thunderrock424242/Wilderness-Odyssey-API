package com.thunder.wildernessodysseyapi.rendering.backend.opengl;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thunder.wildernessodysseyapi.rendering.backend.RenderBackend;
import org.lwjgl.opengl.ARBTimerQuery;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Non-blocking timestamp-query ring owned entirely by the OpenGL adapter.
 *
 * @deprecated OpenGL query objects are unavailable on the planned Vulkan
 * renderer. Future timing must be supplied by its {@link RenderBackend}.
 */
@Deprecated(forRemoval = true)
final class OpenGlGpuTimer implements RenderBackend.GpuTimer {

    private final int[] startQueries;
    private final int[] endQueries;
    private final boolean[] inFlight;
    private final long[] sourceFrames;
    private final long[] submissionSequences;
    private final boolean coreQueries;
    private final Consumer<OpenGlGpuTimer> closeListener;
    private int writeIndex;
    private int activeIndex = -1;
    private long activeSourceFrame = -1L;
    private long nextSequence = 1L;
    private long latestNanos;
    private RenderBackend.GpuTimingSample pendingSample;
    private boolean available = true;
    private boolean closed;

    OpenGlGpuTimer(int bufferedFrames, Consumer<OpenGlGpuTimer> closeListener) {
        this.startQueries = new int[bufferedFrames];
        this.endQueries = new int[bufferedFrames];
        this.inFlight = new boolean[bufferedFrames];
        this.sourceFrames = new long[bufferedFrames];
        this.submissionSequences = new long[bufferedFrames];
        this.coreQueries = GL.getCapabilities().OpenGL33;
        this.closeListener = closeListener;
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public void begin() {
        begin(-1L);
    }

    @Override
    public void begin(long sourceFrame) {
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
            activeSourceFrame = Math.max(-1L, sourceFrame);
        } catch (RuntimeException failure) {
            available = false;
            activeIndex = -1;
            activeSourceFrame = -1L;
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
            sourceFrames[activeIndex] = activeSourceFrame;
            submissionSequences[activeIndex] = nextSequence++;
            writeIndex = (activeIndex + 1) % inFlight.length;
        } catch (RuntimeException failure) {
            available = false;
        } finally {
            activeIndex = -1;
            activeSourceFrame = -1L;
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
    public Optional<RenderBackend.GpuTimingSample> poll() {
        try {
            pollCompleted();
        } catch (RuntimeException failure) {
            available = false;
        }
        RenderBackend.GpuTimingSample sample = pendingSample;
        pendingSample = null;
        return Optional.ofNullable(sample);
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }
        closed = true;
        activeIndex = -1;
        activeSourceFrame = -1L;
        for (int index = 0; index < inFlight.length; index++) {
            delete(startQueries, index);
            delete(endQueries, index);
            inFlight[index] = false;
            sourceFrames[index] = -1L;
            submissionSequences[index] = 0L;
        }
        writeIndex = 0;
        latestNanos = 0L;
        pendingSample = null;
        available = false;
        closeListener.accept(this);
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
                RenderBackend.GpuTimingSample completed = new RenderBackend.GpuTimingSample(
                        submissionSequences[index],
                        sourceFrames[index],
                        latestNanos
                );
                if (pendingSample == null || completed.sequence() > pendingSample.sequence()) {
                    pendingSample = completed;
                }
            }
            inFlight[index] = false;
            sourceFrames[index] = -1L;
            submissionSequences[index] = 0L;
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
