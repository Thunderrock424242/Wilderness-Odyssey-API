package com.thunder.wildernessodysseyapi.rendering.performance;

import com.thunder.wildernessodysseyapi.rendering.GPUCapabilities;
import com.thunder.wildernessodysseyapi.rendering.backend.RenderBackend;
import net.minecraft.client.renderer.ShaderInstance;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class RenderWorkloadTimerTest {
    @Test
    void delayedOlderSamplesCannotReplaceNewerEvidence() {
        RenderWorkloadTimer timer = new RenderWorkloadTimer();
        FakeBackend backend = new FakeBackend();
        timer.begin(backend, 1, 10);
        timer.end();
        assertEquals(10, timer.poll().sourceFrame());
        timer.begin(backend, 1, 11);
        timer.end();
        backend.queued = new RenderBackend.GpuTimingSample(9, 9, 9_000_000);
        assertEquals(10, timer.poll().sourceFrame());
        backend.queued = new RenderBackend.GpuTimingSample(11, 11, 1_000_000);
        assertEquals(11, timer.poll().sourceFrame());
        assertTrue(timer.healthy());
        assertEquals(0, timer.failures());
        timer.close();
    }

    @Test
    void impossibleFutureFrameInvalidatesTheTimerUntilReset() {
        RenderWorkloadTimer timer = new RenderWorkloadTimer();
        FakeBackend backend = new FakeBackend();
        timer.begin(backend, 1, 10);
        timer.end();
        timer.poll();
        timer.begin(backend, 1, 11);
        timer.end();
        backend.queued = new RenderBackend.GpuTimingSample(999, 999, 1_000_000);
        assertNull(timer.poll());
        assertFalse(timer.healthy());
        assertEquals(1, backend.closed);
        timer.begin(backend, 1, 12);
        assertEquals(1, backend.created);
        timer.begin(backend, 2, 13);
        assertTrue(timer.healthy());
        assertEquals(2, backend.created);
        timer.close();
    }

    @Test
    void failuresAfterAllocationCloseOnceAndDoNotRetryTheBrokenGeneration() {
        for (String failure : new String[] {"begin", "end", "poll", "unavailable"}) {
            RenderWorkloadTimer timer = new RenderWorkloadTimer();
            FakeBackend backend = new FakeBackend();
            timer.begin(backend, 1, 10);
            timer.end();
            timer.poll();
            backend.failure = failure;
            timer.begin(backend, 1, 11);
            timer.end();
            timer.poll();
            assertFalse(timer.healthy(), failure);
            assertNull(timer.latest(), failure);
            assertEquals(1, backend.closed, failure);
            assertEquals(1, timer.failures(), failure);
            timer.begin(backend, 1, 12);
            timer.close();
            assertEquals(1, backend.closed, failure);
            assertEquals(1, backend.created, failure);
        }
    }
    @Test
    void replacementAndResetCloseResourcesAndInvalidateOldSamples() {
        RenderWorkloadTimer timer = new RenderWorkloadTimer();
        FakeBackend backend = new FakeBackend();
        timer.begin(backend, 1, 10);
        timer.end();
        assertEquals(10, timer.poll().sourceFrame());
        timer.begin(backend, 2, 11);
        assertEquals(1, backend.closed);
        assertNull(timer.latest());
        timer.end();
        timer.poll();
        timer.close();
        timer.close();
        assertEquals(2, backend.closed);
        assertNull(timer.latest());
    }

    @Test
    void absentAndFailingBackendLeaveExistingRenderPathAvailable() {
        RenderWorkloadTimer timer = new RenderWorkloadTimer();
        assertDoesNotThrow(() -> timer.begin(RenderBackend.UNAVAILABLE, 1, 10));
        timer.end();
        assertNull(timer.poll());
        FakeBackend backend = new FakeBackend();
        backend.fail = true;
        assertDoesNotThrow(() -> timer.begin(backend, 2, 11));
        assertNull(timer.poll());
        assertEquals(1, timer.failures());
        // Repeated draws do not repeatedly allocate/retry a broken timer.
        timer.begin(backend, 2, 12);
        assertEquals(1, timer.failures());
    }

    private static final class FakeBackend implements RenderBackend {
        int closed;
        boolean fail;
        int created;
        String failure = "";
        RenderBackend.GpuTimingSample queued;
        @Override public GPUCapabilities capabilities() { return GPUCapabilities.UNAVAILABLE; }
        @Override public GpuTimer createGpuTimer(int capacity) {
            if (fail) throw new IllegalStateException("unavailable context");
            created++;
            return new GpuTimer() {
                long frame;
                boolean done;
                boolean released;
                @Override public boolean available() { return !released && !failure.equals("unavailable"); }
                @Override public void begin() { }
                @Override public void begin(long sourceFrame) {
                    if (failure.equals("begin")) throw new IllegalStateException("begin failed");
                    frame = sourceFrame;
                }
                @Override public void end() {
                    if (failure.equals("end")) throw new IllegalStateException("end failed");
                    done = true;
                }
                @Override public long latestNanos() { return 1_000_000; }
                @Override public Optional<GpuTimingSample> poll() {
                    if (failure.equals("poll")) throw new IllegalStateException("poll failed");
                    if (queued != null) {
                        GpuTimingSample result = queued;
                        queued = null;
                        return Optional.of(result);
                    }
                    if (!done) return Optional.empty();
                    done = false;
                    return Optional.of(new GpuTimingSample(frame, frame, 1_000_000));
                }
                @Override public void close() { if (!released) closed++; released = true; }
            };
        }
        @Override public boolean isShaderUsable(ShaderInstance shader) { return false; }
        @Override public RenderStateSnapshot captureRenderState() { return RenderStateSnapshot.DEFAULT; }
    }
}
