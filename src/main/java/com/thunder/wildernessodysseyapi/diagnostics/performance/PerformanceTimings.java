package com.thunder.wildernessodysseyapi.diagnostics.performance;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Bounded primitive/string evidence; synchronized because chunk requests can originate off-thread. */
public final class PerformanceTimings {
    private final int phaseLimit;
    private final int waitLimit;
    private final long waitThresholdNanos;
    private final long captureIntervalNanos;
    private final Map<String, Phase> phases = new LinkedHashMap<>();
    private final ArrayDeque<ChunkWait> waits = new ArrayDeque<>();
    private long slowChunkWaitSpans;
    private long maximumChunkWaitNanos;
    private long lastCaptureNanos;
    private boolean captured;
    private long omittedPhaseMeasurements;

    public PerformanceTimings(int phaseLimit, int waitLimit, long waitThresholdNanos, long captureIntervalNanos) {
        if (phaseLimit < 1 || waitLimit < 1 || waitThresholdNanos < 0 || captureIntervalNanos < 0)
            throw new IllegalArgumentException("Invalid diagnostic bounds");
        this.phaseLimit = phaseLimit;
        this.waitLimit = waitLimit;
        this.waitThresholdNanos = waitThresholdNanos;
        this.captureIntervalNanos = captureIntervalNanos;
    }

    public synchronized void recordPhase(String name, long nanos, boolean successful) {
        if (nanos < 0) return;
        Phase previous = phases.get(name);
        if (previous == null && phases.size() >= phaseLimit) {
            omittedPhaseMeasurements++;
            return;
        }
        if (previous == null) previous = new Phase(0, 0, 0, 0, 0);
        phases.put(name, new Phase(previous.calls() + 1, previous.failures() + (successful ? 0 : 1),
                previous.totalNanos() + nanos, Math.max(previous.maximumNanos(), nanos), nanos));
    }

    /** Checks the threshold and global capture rate before asking for an optional stack. */
    public synchronized boolean recordChunkWait(String dimension, int x, int z, long nanos, String thread,
                                                 long nowNanos, Supplier<List<String>> stack) {
        if (nanos < waitThresholdNanos) return false;
        slowChunkWaitSpans++;
        maximumChunkWaitNanos = Math.max(maximumChunkWaitNanos, nanos);
        if (captured && nowNanos - lastCaptureNanos < captureIntervalNanos) return false;
        captured = true;
        lastCaptureNanos = nowNanos;
        List<String> frames;
        try {
            frames = stack.get().stream().limit(16).map(PerformanceTimings::bounded).toList();
        } catch (RuntimeException | LinkageError unavailable) {
            frames = List.of();
        }
        if (waits.size() == waitLimit) waits.removeFirst();
        waits.addLast(new ChunkWait(bounded(dimension), x, z, nanos, bounded(thread), frames));
        return true;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(Map.copyOf(phases), List.copyOf(waits), slowChunkWaitSpans, maximumChunkWaitNanos,
                omittedPhaseMeasurements);
    }

    private static String bounded(String value) {
        return value == null ? "unknown" : value.substring(0, Math.min(256, value.length()));
    }

    public record Phase(long calls, long failures, long totalNanos, long maximumNanos, long lastNanos) { }
    public record ChunkWait(String dimension, int x, int z, long durationNanos, String thread, List<String> stack) { }
    public record Snapshot(Map<String, Phase> phases, List<ChunkWait> waits, long slowChunkWaitSpans,
                           long maximumChunkWaitNanos, long omittedPhaseMeasurements) { }
}
