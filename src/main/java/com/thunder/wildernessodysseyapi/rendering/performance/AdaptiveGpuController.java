package com.thunder.wildernessodysseyapi.rendering.performance;

import com.thunder.wildernessodysseyapi.rendering.GPUCapabilities;
import com.thunder.wildernessodysseyapi.rendering.TemporalFrameData.Resolution;

import java.util.Arrays;

/**
 * Pure render-thread policy for the optional bounded cloud-density shader path.
 *
 * <p>This is a policy owned by the existing rendering framework, not an executor.
 * There is no asynchronous task queue: one admission per observed frame is the
 * hard bound. It never transfers authoritative work or changes saved quality.</p>
 */
public final class AdaptiveGpuController {
    private static final int WARMUP_SAMPLES = 60;
    private static final int SUSTAINED_SAMPLES = 8;
    private static final long MAX_AGE_FRAMES = 8;
    private static final long MAX_GAP_NANOS = 500_000_000L;
    private static final double ALPHA = 0.08;

    // Bounded history preserves the actual age of delayed query results at low FPS.
    private final long[] recentFrameIds = new long[16];
    private final long[] recentFrameNanos = new long[16];

    private Policy policy = Policy.DISABLED;
    private long generation = -1;
    private Resolution resolution;
    private long observedFrame = -1;
    private long admittedFrame = -1;
    private long lastNow;
    private long lastCloudSource = -1;
    private long lastGpuSource = -1;
    private long nextChange;
    private double gpuAverage;
    private double cloudAverage;
    private int samples;
    private int pressureSamples;
    private int recoverySamples;
    private boolean active;
    private Reason reason = Reason.DISABLED;
    private long accepted;
    private long rejected;
    private long fallbacks;

    public AdaptiveGpuController() {
        clearFrameHistory();
    }

    public void observe(Observation input, Policy requestedPolicy) {
        Policy requested = requestedPolicy == null ? Policy.DISABLED : requestedPolicy;
        if (!requested.equals(policy) || generation != input.backendGeneration()
                || !input.resolution().equals(resolution) || input.nowNanos() < lastNow
                || (lastNow != 0 && input.nowNanos() - lastNow > MAX_GAP_NANOS)) {
            fallback(Reason.WARMUP);
            clearMeasurements();
            clearFrameHistory();
            policy = requested;
            generation = input.backendGeneration();
            resolution = input.resolution();
            nextChange = input.nowNanos() + policy.cooldownNanos();
        }
        observedFrame = input.frameIndex();
        lastNow = input.nowNanos();
        int slot = (int) (observedFrame & (recentFrameIds.length - 1));
        if (recentFrameIds[slot] != observedFrame) {
            recentFrameIds[slot] = observedFrame;
            recentFrameNanos[slot] = lastNow;
        }
        if (!policy.enabled() || policy.mode() == Mode.DISABLED) {
            safetyFallback(Reason.DISABLED);
            return;
        }
        if (!input.capabilities().available() || !input.capabilities().supportsGpuTiming()) {
            safetyFallback(Reason.UNSUPPORTED_BACKEND);
            return;
        }
        if (input.externalShaders()) {
            safetyFallback(Reason.EXTERNAL_SHADERS);
            return;
        }
        if (input.heapUsage() >= 0.90 || input.gcFraction() >= 0.15) {
            safetyFallback(Reason.MEMORY_PRESSURE);
            return;
        }
        if (!fresh(input.frameIndex(), input.gpuSourceFrame(), input.gpuFrameNanos())
                || !fresh(input.frameIndex(), input.cloudSourceFrame(), input.cloudGpuNanos())
                || input.cpuFrameNanos() <= 0 || input.cpuFrameNanos() > MAX_GAP_NANOS) {
            safetyFallback(Reason.STALE_TIMING);
            return;
        }
        if (input.gpuSourceFrame() > lastGpuSource) {
            gpuAverage = smooth(gpuAverage, input.gpuFrameNanos());
            lastGpuSource = input.gpuSourceFrame();
        }
        // A cached delayed result is evidence once, never sixty warm-up samples.
        if (input.cloudSourceFrame() <= lastCloudSource) return;
        lastCloudSource = input.cloudSourceFrame();
        cloudAverage = smooth(cloudAverage, input.cloudGpuNanos());
        samples = Math.min(WARMUP_SAMPLES, samples + 1);
        if (samples < WARMUP_SAMPLES) {
            reason = Reason.WARMUP;
            return;
        }
        long budget = cloudBudgetNanos();
        boolean pressure = cloudAverage > budget * 1.12
                || (gpuAverage > policy.targetFrameNanos() * 0.90
                && input.cpuFrameNanos() > policy.targetFrameNanos() * 1.12);
        boolean recovery = cloudAverage < budget * 0.65
                && gpuAverage < policy.targetFrameNanos() * 0.65;
        pressureSamples = pressure ? Math.min(SUSTAINED_SAMPLES, pressureSamples + 1) : 0;
        recoverySamples = recovery ? Math.min(SUSTAINED_SAMPLES, recoverySamples + 1) : 0;
        if (input.nowNanos() < nextChange) {
            reason = Reason.COOLDOWN;
            return;
        }
        if (!active && pressureSamples >= SUSTAINED_SAMPLES) {
            active = true;
            nextChange = input.nowNanos() + policy.cooldownNanos();
        } else if (active && recoverySamples >= SUSTAINED_SAMPLES) {
            fallback(Reason.IDLE);
            nextChange = input.nowNanos() + policy.cooldownNanos();
        }
        reason = active ? Reason.READY : Reason.IDLE;
    }

    /** Called only when the existing cloud shader is actually about to draw. No work is retained. */
    public boolean tryAcquireCloud(long frameIndex) {
        return tryAcquireCloud(frameIndex, true);
    }

    /** A known timer failure must reject this draw, even after a successful frame-start decision. */
    public boolean tryAcquireCloud(long frameIndex, boolean timingHealthy) {
        if (!timingHealthy && policy.enabled()) safetyFallback(Reason.STALE_TIMING);
        if (!active || frameIndex != observedFrame || admittedFrame == frameIndex) {
            rejected++;
            return false;
        }
        admittedFrame = frameIndex;
        accepted++;
        return true;
    }

    public Snapshot snapshot() {
        return new Snapshot(policy.mode(), active, reason, cloudBudgetNanos(), Math.round(gpuAverage),
                Math.round(cloudAverage), accepted, rejected, fallbacks);
    }

    /** Discards all world/context observations and diagnostic counters. */
    public void reset() {
        policy = Policy.DISABLED;
        generation = -1;
        resolution = null;
        observedFrame = -1;
        lastNow = 0;
        nextChange = 0;
        active = false;
        reason = Reason.DISABLED;
        accepted = rejected = fallbacks = 0;
        clearMeasurements();
        clearFrameHistory();
    }

    private void clearFrameHistory() {
        Arrays.fill(recentFrameIds, -1);
    }

    private void clearMeasurements() {
        admittedFrame = -1;
        lastCloudSource = lastGpuSource = -1;
        gpuAverage = cloudAverage = 0;
        samples = pressureSamples = recoverySamples = 0;
    }

    private void safetyFallback(Reason cause) {
        fallback(cause);
        clearMeasurements();
        nextChange = lastNow + policy.cooldownNanos();
    }

    private void fallback(Reason cause) {
        if (active) fallbacks++;
        active = false;
        reason = cause;
    }

    private long cloudBudgetNanos() {
        double fraction = switch (policy.mode()) {
            case PERFORMANCE -> 0.18;
            case QUALITY -> 0.35;
            default -> 0.25;
        };
        return Math.round(policy.targetFrameNanos() * fraction);
    }

    private boolean fresh(long frame, long source, long nanos) {
        if (source < 0 || source > frame || frame - source > MAX_AGE_FRAMES
                || nanos <= 0 || nanos > MAX_GAP_NANOS) return false;
        int slot = (int) (source & (recentFrameIds.length - 1));
        long age = lastNow - recentFrameNanos[slot];
        return recentFrameIds[slot] == source && age >= 0 && age <= MAX_GAP_NANOS;
    }

    private static double smooth(double previous, long sample) {
        return previous == 0 ? sample : previous + (sample - previous) * ALPHA;
    }

    public enum Mode { DISABLED, AUTOMATIC, PERFORMANCE, QUALITY }
    public enum Reason { DISABLED, UNSUPPORTED_BACKEND, EXTERNAL_SHADERS, STALE_TIMING,
        MEMORY_PRESSURE, WARMUP, COOLDOWN, IDLE, READY }

    public record Policy(boolean enabled, Mode mode, long targetFrameNanos, long cooldownNanos) {
        public static final Policy DISABLED = new Policy(false, Mode.DISABLED, 16_670_000, 5_000_000_000L);
        public Policy {
            mode = mode == null ? Mode.DISABLED : mode;
            targetFrameNanos = Math.max(1_000_000, Math.min(100_000_000, targetFrameNanos));
            cooldownNanos = Math.max(0, Math.min(30_000_000_000L, cooldownNanos));
        }
    }

    /** Negative memory/GC values are unknown. Capacity is deliberately absent from the decision. */
    public record Observation(long frameIndex, long nowNanos, long backendGeneration, Resolution resolution,
            GPUCapabilities capabilities, boolean externalShaders, long cpuFrameNanos, long gpuFrameNanos,
            long gpuSourceFrame, long cloudGpuNanos, long cloudSourceFrame, double heapUsage, double gcFraction) {
        public Observation {
            resolution = resolution == null ? Resolution.ONE : resolution;
            capabilities = capabilities == null ? GPUCapabilities.UNAVAILABLE : capabilities;
        }
    }

    public record Snapshot(Mode mode, boolean active, Reason reason, long cloudBudgetNanos,
            long gpuAverageNanos, long cloudAverageNanos, long accepted, long rejected, long fallbacks) { }
}
