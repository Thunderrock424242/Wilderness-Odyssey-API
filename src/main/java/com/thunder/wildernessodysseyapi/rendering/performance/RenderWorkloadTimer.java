package com.thunder.wildernessodysseyapi.rendering.performance;

import com.thunder.wildernessodysseyapi.rendering.backend.RenderBackend;

/**
 * One backend-owned four-slot asynchronous timer, scoped to a backend generation.
 * Optional timing failure invalidates evidence and is retried only after a reset.
 */
public final class RenderWorkloadTimer implements AutoCloseable {
    private RenderBackend backend = RenderBackend.UNAVAILABLE;
    private RenderBackend.GpuTimer timer = RenderBackend.GpuTimer.UNAVAILABLE;
    private RenderBackend.GpuTimingSample latest;
    private long generation = -1;
    private long highestBegunFrame = -1;
    private boolean failed;
    private long failures;

    public void begin(RenderBackend nextBackend, long nextGeneration, long frame) {
        if (backend != nextBackend || generation != nextGeneration) {
            close();
            backend = nextBackend;
            generation = nextGeneration;
            try {
                timer = backend.createGpuTimer(4);
            } catch (RuntimeException | LinkageError unavailable) {
                fail();
            }
        }
        if (failed) return;
        try {
            timer.begin(frame);
            highestBegunFrame = Math.max(highestBegunFrame, frame);
            healthy();
        } catch (RuntimeException | LinkageError unavailable) {
            fail();
        }
    }

    public void end() {
        if (failed) return;
        try {
            timer.end();
            healthy();
        } catch (RuntimeException | LinkageError unavailable) {
            fail();
        }
    }

    public RenderBackend.GpuTimingSample poll() {
        if (failed) return null;
        try {
            if (!healthy()) {
                latest = null;
            } else {
                RenderBackend.GpuTimingSample sample = timer.poll().orElse(null);
                if (sample != null) {
                    if (sample.sourceFrame() < 0 || sample.sourceFrame() > highestBegunFrame) {
                        fail();
                    } else if (latest == null || (sample.sequence() > latest.sequence()
                            && sample.sourceFrame() > latest.sourceFrame())) {
                        latest = sample;
                    }
                }
                healthy();
            }
        } catch (RuntimeException | LinkageError unavailable) {
            fail();
        }
        return latest;
    }

    public RenderBackend.GpuTimingSample latest() { return latest; }
    public long failures() { return failures; }

    /** Ring saturation leaves a timer healthy; permanent backend failure invalidates it immediately. */
    public boolean healthy() {
        if (failed || timer == RenderBackend.GpuTimer.UNAVAILABLE) return false;
        try {
            if (timer.available()) return true;
        } catch (RuntimeException | LinkageError unavailable) {
            // The same failure path also handles backends that throw instead of returning false.
        }
        fail();
        return false;
    }

    @Override
    public void close() {
        try {
            timer.close();
        } catch (RuntimeException | LinkageError unavailable) {
            failures++;
        } finally {
            timer = RenderBackend.GpuTimer.UNAVAILABLE;
            backend = RenderBackend.UNAVAILABLE;
            generation = -1;
            highestBegunFrame = -1;
            latest = null;
            failed = false;
        }
    }

    private void fail() {
        if (failed) return;
        failures++;
        latest = null;
        failed = true;
        RenderBackend.GpuTimer failedTimer = timer;
        timer = RenderBackend.GpuTimer.UNAVAILABLE;
        try {
            failedTimer.close();
        } catch (RuntimeException | LinkageError cleanupFailure) {
            failures++;
        }
    }
}
