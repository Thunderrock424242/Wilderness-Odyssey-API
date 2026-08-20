package com.thunder.wildernessodysseyapi.gpuprofiler.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import org.lwjgl.opengl.ARBTimerQuery;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLDebugMessageCallback;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.KHRDebug;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Graphical diagnostics attached to a {@link GpuProfiler} session.
 * All expensive work is opt-in and only runs while the profiler is active.
 */
public final class GpuDiagnostics {

    private static final Object LOCK = new Object();
    private static final int MAX_DEBUG_EVENTS = 2_048;
    private static final int MAX_STATE_LEAKS = 1_024;
    private static final int MAX_PENDING_TIMERS = 768;
    private static final int MAX_TIMER_SAMPLES_PER_FRAME = 96;
    private static final int DRAW_SAMPLE_STRIDE = 8;
    private static final long MAX_REASONABLE_DRAW_NANOS = 10_000_000_000L;

    private static final ThreadLocal<Deque<ScopeFrame>> SCOPES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<DrawToken> ACTIVE_DRAW = new ThreadLocal<>();
    private static final Deque<DebugEvent> DEBUG_EVENTS = new ArrayDeque<>();
    private static final Deque<StateLeak> STATE_LEAKS = new ArrayDeque<>();
    private static final Deque<PendingTimer> PENDING_TIMERS = new ArrayDeque<>();
    private static final Map<TimingKey, TimingAggregate> GPU_TIMINGS = new HashMap<>();
    private static final Map<String, ScopeAggregate> SCOPE_STATS = new HashMap<>();

    private static volatile boolean active;
    private static volatile boolean timerAvailable;
    private static volatile String debugProvider = "unavailable";
    private static GLDebugMessageCallback debugCallback;
    private static boolean debugCallbackInstalled;
    private static boolean debugOutputWasEnabled;
    private static boolean debugSyncWasEnabled;
    private static boolean useCoreTimerQueries;
    private static long frameIndex;
    private static long drawSequence;
    private static long observedDrawCalls;
    private static long timedDrawSamples;
    private static long droppedTimerSamples;
    private static int timerErrors;
    private static int timerSamplesThisFrame;

    private GpuDiagnostics() {
    }

    static void start() {
        synchronized (LOCK) {
            DEBUG_EVENTS.clear();
            STATE_LEAKS.clear();
            PENDING_TIMERS.clear();
            GPU_TIMINGS.clear();
            SCOPE_STATS.clear();
            frameIndex = 0L;
            drawSequence = 0L;
            observedDrawCalls = 0L;
            timedDrawSamples = 0L;
            droppedTimerSamples = 0L;
            timerErrors = 0;
            timerSamplesThisFrame = 0;
            timerAvailable = false;
            debugProvider = "unavailable";
            active = true;
        }

        try {
            GLCapabilities capabilities = GL.getCapabilities();
            useCoreTimerQueries = capabilities.OpenGL33;
            timerAvailable = capabilities.OpenGL33 || capabilities.GL_ARB_timer_query;
            installDebugCallback(capabilities);
        } catch (Throwable ignored) {
            timerAvailable = false;
            debugProvider = "no current OpenGL context";
        }
    }

    static void stop() {
        active = false;
        removeDebugCallback();
        cleanupTimers();
        SCOPES.remove();
        ACTIVE_DRAW.remove();
    }

    static void onFrame() {
        if (!active) {
            return;
        }
        synchronized (LOCK) {
            frameIndex++;
            timerSamplesThisFrame = 0;
        }
        resolveTimerQueries();
    }

    /** Opens a named scope for one Wilderness Odyssey rendering subsystem. */
    public static Scope scope(String systemId) {
        if (!active || systemId == null || systemId.isBlank()) {
            return Scope.NOOP;
        }

        GpuProfiler.DiagnosticAttribution attribution = GpuProfiler.captureDiagnosticAttribution();
        StateSnapshot before = StateSnapshot.capture();
        boolean pushedDebugGroup = false;
        if (debugCallbackInstalled) {
            try {
                KHRDebug.glPushDebugGroup(KHRDebug.GL_DEBUG_SOURCE_APPLICATION, 0, "WO:" + systemId);
                pushedDebugGroup = true;
            } catch (Throwable ignored) {
                // Debug groups are a convenience for external captures, not a requirement.
            }
        }

        ScopeFrame frame = new ScopeFrame(systemId, System.nanoTime(), before, attribution, pushedDebugGroup);
        SCOPES.get().push(frame);
        return new Scope(frame);
    }

    /** Called by the RenderSystem mixin immediately before a draw. */
    public static void beginDraw(int mode, int count, int indexType) {
        if (!active || count <= 0 || ACTIVE_DRAW.get() != null) {
            return;
        }

        long sequence;
        long currentFrame;
        synchronized (LOCK) {
            observedDrawCalls++;
            sequence = drawSequence++;
            currentFrame = frameIndex;
            if (!timerAvailable
                    || timerSamplesThisFrame >= MAX_TIMER_SAMPLES_PER_FRAME
                    || PENDING_TIMERS.size() >= MAX_PENDING_TIMERS
                    || Math.floorMod(sequence + currentFrame, DRAW_SAMPLE_STRIDE) != 0L) {
                return;
            }
            timerSamplesThisFrame++;
        }

        ScopeFrame scope = currentScopeFrame();
        GpuProfiler.DiagnosticAttribution attribution = GpuProfiler.captureDiagnosticAttribution();
        String scopeId = scope == null ? "" : scope.systemId;
        String foldedStack = foldedStack(attribution.stack(), scopeId);
        TimingKey key = scope == null
                ? new TimingKey(attribution.modId(), "", attribution.label(), attribution.fileName(),
                attribution.lineNumber(), foldedStack)
                : new TimingKey(ModConstants.MOD_ID, scope.systemId, scope.systemId, attribution.fileName(),
                attribution.lineNumber(), foldedStack);

        int startQuery = 0;
        int endQuery = 0;
        try {
            startQuery = GL15.glGenQueries();
            endQuery = GL15.glGenQueries();
            queryCounter(startQuery);
            ACTIVE_DRAW.set(new DrawToken(startQuery, endQuery, key, mode, count, indexType, currentFrame));
        } catch (Throwable ignored) {
            safeDeleteQuery(startQuery);
            safeDeleteQuery(endQuery);
            synchronized (LOCK) {
                timerErrors++;
            }
        }
    }

    /** Called by the RenderSystem mixin immediately after a draw. */
    public static void endDraw() {
        if (!active) {
            return;
        }
        DrawToken token = ACTIVE_DRAW.get();
        if (token == null) {
            return;
        }
        ACTIVE_DRAW.remove();
        try {
            queryCounter(token.endQuery);
            synchronized (LOCK) {
                PENDING_TIMERS.addLast(new PendingTimer(token.startQuery, token.endQuery, token.key,
                        token.mode, token.indexCount, token.indexType, token.frame));
            }
        } catch (Throwable ignored) {
            safeDeleteQuery(token.startQuery);
            safeDeleteQuery(token.endQuery);
            synchronized (LOCK) {
                timerErrors++;
            }
        }
    }

    public static List<GpuTimingSummary> topGpu(int limit) {
        synchronized (LOCK) {
            long allSampledNanos = GPU_TIMINGS.values().stream().mapToLong(value -> value.totalNanos).sum();
            return GPU_TIMINGS.values().stream()
                    .map(value -> value.toSummary(allSampledNanos))
                    .sorted(Comparator.comparingLong(GpuTimingSummary::totalNanos).reversed())
                    .limit(Math.max(1, limit))
                    .toList();
        }
    }

    /** Returns Spark-style sampled GPU-time totals grouped by owning mod. */
    public static List<GpuModSummary> topGpuMods(int limit) {
        synchronized (LOCK) {
            return gpuModSummaries(Math.max(1, limit));
        }
    }

    public static List<DebugEvent> debugEvents(int limit) {
        synchronized (LOCK) {
            return DEBUG_EVENTS.stream().sorted(Comparator.comparingLong(DebugEvent::elapsedNanos).reversed())
                    .limit(Math.max(1, limit)).toList();
        }
    }

    public static List<StateLeak> stateLeaks(int limit) {
        synchronized (LOCK) {
            return STATE_LEAKS.stream().sorted(Comparator.comparingLong(StateLeak::elapsedNanos).reversed())
                    .limit(Math.max(1, limit)).toList();
        }
    }

    public static DiagnosticsStatus status() {
        synchronized (LOCK) {
            return new DiagnosticsStatus(debugProvider, timerAvailable, observedDrawCalls, timedDrawSamples,
                    PENDING_TIMERS.size(), droppedTimerSamples, timerErrors, DEBUG_EVENTS.size(), STATE_LEAKS.size());
        }
    }

    static List<String> debugLines() {
        if (!active) {
            return List.of();
        }
        DiagnosticsStatus status = status();
        List<String> lines = new ArrayList<>();
        lines.add("[WO GPU] sampled draws " + status.timedDrawSamples + " / " + status.observedDrawCalls
                + " | GL messages " + status.debugEvents + " | state leaks " + status.stateLeaks);
        List<GpuModSummary> top = topGpuMods(1);
        if (!top.isEmpty()) {
            GpuModSummary timing = top.getFirst();
            lines.add("  top GPU mod: " + timing.modId + " " + formatMillis(timing.totalNanos) + " ms sampled ("
                    + String.format("%.1f", timing.percentOfSamples) + "%)");
        }
        return lines;
    }

    static Map<String, Object> reportData() {
        synchronized (LOCK) {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("status", status());
            long allSampledNanos = GPU_TIMINGS.values().stream().mapToLong(value -> value.totalNanos).sum();
            report.put("samplingModel", "Spark-style folded Java call stacks weighted by asynchronous GPU timestamp samples; percentages cover sampled draw time only.");
            report.put("gpuMods", gpuModSummaries(Integer.MAX_VALUE));
            report.put("gpuTimings", GPU_TIMINGS.values().stream()
                    .map(value -> value.toSummary(allSampledNanos))
                    .sorted(Comparator.comparingLong(GpuTimingSummary::totalNanos).reversed()).toList());
            report.put("foldedStacks", foldedStackSamples());
            report.put("debugEvents", List.copyOf(DEBUG_EVENTS));
            report.put("stateLeaks", List.copyOf(STATE_LEAKS));
            report.put("scopeStats", SCOPE_STATS.values().stream().map(ScopeAggregate::toSummary)
                    .sorted(Comparator.comparingLong(ScopeSummary::cpuNanos).reversed()).toList());
            return report;
        }
    }

    static List<String> foldedStackLines() {
        synchronized (LOCK) {
            return foldedStackSamples().stream().map(sample -> {
                String modRoot = sanitizeFoldedFrame("mod:" + sample.modId);
                String stack = sanitizeFoldedStack(sample.foldedStack);
                return modRoot + ";" + stack + " " + Math.max(1L, sample.gpuNanos);
            }).toList();
        }
    }

    // Aggregates the detailed sampled callsites into the mod-level view shown in chat.
    private static List<GpuModSummary> gpuModSummaries(int limit) {
        long allSampledNanos = GPU_TIMINGS.values().stream().mapToLong(value -> value.totalNanos).sum();
        Map<String, ModTimingAggregate> modTotals = new HashMap<>();
        for (TimingAggregate timing : GPU_TIMINGS.values()) {
            String modId = timing.key.modId == null || timing.key.modId.isBlank() ? "unknown" : timing.key.modId;
            modTotals.computeIfAbsent(modId, ModTimingAggregate::new).add(timing);
        }
        return modTotals.values().stream()
                .map(value -> value.toSummary(allSampledNanos))
                .sorted(Comparator.comparingLong(GpuModSummary::totalNanos).reversed())
                .limit(limit)
                .toList();
    }

    // Folded stacks can be consumed directly by flamegraph tooling after JSON export.
    private static List<FoldedStackSample> foldedStackSamples() {
        Map<FoldedStackKey, FoldedStackAggregate> stacks = new HashMap<>();
        for (TimingAggregate timing : GPU_TIMINGS.values()) {
            FoldedStackKey key = new FoldedStackKey(timing.key.modId, timing.key.foldedStack);
            stacks.computeIfAbsent(key, FoldedStackAggregate::new).add(timing.totalNanos, timing.samples);
        }
        return stacks.values().stream()
                .map(FoldedStackAggregate::toSummary)
                .sorted(Comparator.comparingLong(FoldedStackSample::gpuNanos).reversed())
                .toList();
    }

    private static void closeScope(ScopeFrame frame) {
        Deque<ScopeFrame> scopes = SCOPES.get();
        if (!scopes.isEmpty() && scopes.peek() == frame) {
            scopes.pop();
        } else {
            scopes.remove(frame);
        }
        if (scopes.isEmpty()) {
            SCOPES.remove();
        }

        if (frame.pushedDebugGroup && debugCallbackInstalled) {
            try {
                KHRDebug.glPopDebugGroup();
            } catch (Throwable ignored) {
                // Do not let diagnostics affect rendering.
            }
        }

        long elapsedCpu = Math.max(0L, System.nanoTime() - frame.startedNanos);
        StateSnapshot after = StateSnapshot.capture();
        Map<String, String> differences = frame.before.diff(after);
        synchronized (LOCK) {
            SCOPE_STATS.computeIfAbsent(frame.systemId, ScopeAggregate::new).add(elapsedCpu, !differences.isEmpty());
            if (!differences.isEmpty()) {
                addBounded(STATE_LEAKS, new StateLeak(frame.systemId, currentElapsedNanos(), frameIndex,
                        Map.copyOf(differences), frame.attribution.modId(), frame.attribution.location()), MAX_STATE_LEAKS);
            }
        }
    }

    private static ScopeFrame currentScopeFrame() {
        Deque<ScopeFrame> scopes = SCOPES.get();
        return scopes.peek();
    }

    private static String currentScopeName() {
        ScopeFrame scope = currentScopeFrame();
        return scope == null ? "" : scope.systemId;
    }

    private static void installDebugCallback(GLCapabilities capabilities) {
        if (!capabilities.GL_KHR_debug) {
            debugProvider = "KHR_debug unavailable";
            return;
        }
        try {
            long existingCallback = GL11.glGetPointer(KHRDebug.GL_DEBUG_CALLBACK_FUNCTION);
            if (existingCallback != 0L) {
                debugProvider = "existing callback preserved";
                return;
            }

            debugOutputWasEnabled = GL11.glIsEnabled(KHRDebug.GL_DEBUG_OUTPUT);
            debugSyncWasEnabled = GL11.glIsEnabled(KHRDebug.GL_DEBUG_OUTPUT_SYNCHRONOUS);
            debugCallback = GLDebugMessageCallback.create((source, type, id, severity, length, message, userParam) -> {
                if (!active || severity == KHRDebug.GL_DEBUG_SEVERITY_NOTIFICATION
                        || type == KHRDebug.GL_DEBUG_TYPE_PUSH_GROUP || type == KHRDebug.GL_DEBUG_TYPE_POP_GROUP
                        || type == KHRDebug.GL_DEBUG_TYPE_MARKER) {
                    return;
                }
                String text;
                try {
                    text = MemoryUtil.memUTF8(message, length);
                } catch (Throwable ignored) {
                    text = "<unable to decode driver message>";
                }
                GpuProfiler.DiagnosticAttribution attribution = GpuProfiler.captureDiagnosticAttribution();
                DebugEvent event = new DebugEvent(currentElapsedNanos(), frameIndex, debugSource(source),
                        debugType(type), id, debugSeverity(severity), text, currentScopeName(),
                        attribution.modId(), attribution.location());
                synchronized (LOCK) {
                    addBounded(DEBUG_EVENTS, event, MAX_DEBUG_EVENTS);
                }
            });
            KHRDebug.glDebugMessageCallback(debugCallback, 0L);
            debugCallbackInstalled = true;
            GL11.glEnable(KHRDebug.GL_DEBUG_OUTPUT);
            GL11.glEnable(KHRDebug.GL_DEBUG_OUTPUT_SYNCHRONOUS);
            debugProvider = "KHR_debug";
        } catch (Throwable ignored) {
            removeDebugCallback();
            debugProvider = "KHR_debug setup failed";
        }
    }

    private static void removeDebugCallback() {
        if (!debugCallbackInstalled && debugCallback == null) {
            return;
        }
        try {
            if (debugCallbackInstalled) {
                KHRDebug.glDebugMessageCallback(null, 0L);
                if (!debugSyncWasEnabled) {
                    GL11.glDisable(KHRDebug.GL_DEBUG_OUTPUT_SYNCHRONOUS);
                }
                if (!debugOutputWasEnabled) {
                    GL11.glDisable(KHRDebug.GL_DEBUG_OUTPUT);
                }
            }
        } catch (Throwable ignored) {
            // Context teardown may already be underway.
        } finally {
            debugCallbackInstalled = false;
            if (debugCallback != null) {
                debugCallback.free();
                debugCallback = null;
            }
        }
    }

    private static void resolveTimerQueries() {
        if (!timerAvailable) {
            return;
        }
        while (true) {
            PendingTimer timer;
            synchronized (LOCK) {
                timer = PENDING_TIMERS.peekFirst();
            }
            if (timer == null) {
                return;
            }
            try {
                if (GL15.glGetQueryObjecti(timer.endQuery, GL15.GL_QUERY_RESULT_AVAILABLE) == 0) {
                    return;
                }
                long start = queryResult(timer.startQuery);
                long end = queryResult(timer.endQuery);
                long elapsed = end >= start ? end - start : -1L;
                safeDeleteQuery(timer.startQuery);
                safeDeleteQuery(timer.endQuery);
                synchronized (LOCK) {
                    PENDING_TIMERS.removeFirstOccurrence(timer);
                    if (elapsed >= 0L && elapsed <= MAX_REASONABLE_DRAW_NANOS) {
                        GPU_TIMINGS.computeIfAbsent(timer.key, TimingAggregate::new).add(elapsed, timer.indexCount);
                        timedDrawSamples++;
                    } else {
                        droppedTimerSamples++;
                    }
                }
            } catch (Throwable ignored) {
                safeDeleteQuery(timer.startQuery);
                safeDeleteQuery(timer.endQuery);
                synchronized (LOCK) {
                    PENDING_TIMERS.removeFirstOccurrence(timer);
                    timerErrors++;
                }
            }
        }
    }

    private static void cleanupTimers() {
        DrawToken activeDraw = ACTIVE_DRAW.get();
        if (activeDraw != null) {
            safeDeleteQuery(activeDraw.startQuery);
            safeDeleteQuery(activeDraw.endQuery);
        }
        ACTIVE_DRAW.remove();
        synchronized (LOCK) {
            for (PendingTimer timer : PENDING_TIMERS) {
                safeDeleteQuery(timer.startQuery);
                safeDeleteQuery(timer.endQuery);
            }
            PENDING_TIMERS.clear();
        }
    }

    private static void queryCounter(int query) {
        if (useCoreTimerQueries) {
            GL33.glQueryCounter(query, GL33.GL_TIMESTAMP);
        } else {
            ARBTimerQuery.glQueryCounter(query, ARBTimerQuery.GL_TIMESTAMP);
        }
    }

    private static long queryResult(int query) {
        return useCoreTimerQueries
                ? GL33.glGetQueryObjectui64(query, GL15.GL_QUERY_RESULT)
                : ARBTimerQuery.glGetQueryObjectui64(query, GL15.GL_QUERY_RESULT);
    }

    private static void safeDeleteQuery(int query) {
        if (query <= 0) {
            return;
        }
        try {
            GL15.glDeleteQueries(query);
        } catch (Throwable ignored) {
            // Never surface cleanup failures into rendering.
        }
    }

    private static long currentElapsedNanos() {
        return GpuProfiler.status().elapsedNanos();
    }

    private static String debugSource(int source) {
        return switch (source) {
            case KHRDebug.GL_DEBUG_SOURCE_API -> "api";
            case KHRDebug.GL_DEBUG_SOURCE_WINDOW_SYSTEM -> "window";
            case KHRDebug.GL_DEBUG_SOURCE_SHADER_COMPILER -> "shader_compiler";
            case KHRDebug.GL_DEBUG_SOURCE_THIRD_PARTY -> "third_party";
            case KHRDebug.GL_DEBUG_SOURCE_APPLICATION -> "application";
            default -> "other";
        };
    }

    private static String debugType(int type) {
        return switch (type) {
            case KHRDebug.GL_DEBUG_TYPE_ERROR -> "error";
            case KHRDebug.GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR -> "deprecated";
            case KHRDebug.GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR -> "undefined_behavior";
            case KHRDebug.GL_DEBUG_TYPE_PORTABILITY -> "portability";
            case KHRDebug.GL_DEBUG_TYPE_PERFORMANCE -> "performance";
            default -> "other";
        };
    }

    private static String debugSeverity(int severity) {
        return switch (severity) {
            case KHRDebug.GL_DEBUG_SEVERITY_HIGH -> "high";
            case KHRDebug.GL_DEBUG_SEVERITY_MEDIUM -> "medium";
            case KHRDebug.GL_DEBUG_SEVERITY_LOW -> "low";
            default -> "notification";
        };
    }

    private static <T> void addBounded(Deque<T> deque, T value, int maximum) {
        while (deque.size() >= maximum) {
            deque.removeFirst();
        }
        deque.addLast(value);
    }

    private static String foldedStack(List<String> stack, String scope) {
        List<String> frames = new ArrayList<>(stack == null ? List.of() : stack);
        Collections.reverse(frames);
        if (frames.isEmpty()) {
            frames.add("unknown");
        }
        if (scope != null && !scope.isBlank()) {
            frames.add("WO scope:" + scope);
        }
        return String.join(";", frames);
    }

    private static String sanitizeFoldedFrame(String value) {
        return String.valueOf(value)
                .replace(';', ':')
                .replace(' ', '_')
                .replace('\t', '_')
                .replace('\r', '_')
                .replace('\n', '_');
    }

    private static String sanitizeFoldedStack(String value) {
        return String.valueOf(value)
                .replace(' ', '_')
                .replace('\t', '_')
                .replace('\r', '_')
                .replace('\n', '_');
    }

    private static double formatMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private record ScopeFrame(String systemId, long startedNanos, StateSnapshot before,
                              GpuProfiler.DiagnosticAttribution attribution, boolean pushedDebugGroup) {
    }

    private record DrawToken(int startQuery, int endQuery, TimingKey key, int mode, int indexCount,
                             int indexType, long frame) {
    }

    private record PendingTimer(int startQuery, int endQuery, TimingKey key, int mode, int indexCount,
                                int indexType, long frame) {
    }

    private record TimingKey(String modId, String scope, String label, String fileName, int lineNumber,
                             String foldedStack) {
    }

    private static final class TimingAggregate {
        private final TimingKey key;
        private final Deque<Long> recentSamples = new ArrayDeque<>();
        private long totalNanos;
        private long maximumNanos;
        private long sampledIndices;
        private int samples;

        private TimingAggregate(TimingKey key) {
            this.key = key;
        }

        private void add(long nanos, int indices) {
            this.totalNanos += nanos;
            this.maximumNanos = Math.max(this.maximumNanos, nanos);
            this.sampledIndices += Math.max(0, indices);
            this.samples++;
            if (this.recentSamples.size() >= 256) {
                this.recentSamples.removeFirst();
            }
            this.recentSamples.addLast(nanos);
        }

        private GpuTimingSummary toSummary(long allSampledNanos) {
            List<Long> sorted = this.recentSamples.stream().sorted().toList();
            long p95 = sorted.isEmpty() ? 0L : sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * 0.95) - 1));
            double percent = allSampledNanos <= 0L ? 0.0 : this.totalNanos * 100.0 / allSampledNanos;
            return new GpuTimingSummary(this.key.modId, this.key.scope, this.key.label, this.key.fileName,
                    this.key.lineNumber, this.totalNanos, this.samples == 0 ? 0L : this.totalNanos / this.samples,
                    p95, this.maximumNanos, this.samples, this.sampledIndices, percent, this.key.foldedStack);
        }
    }

    private static final class ModTimingAggregate {
        private final String modId;
        private long totalNanos;
        private int samples;
        private TimingAggregate hottest;

        private ModTimingAggregate(String modId) {
            this.modId = modId == null || modId.isBlank() ? "unknown" : modId;
        }

        private void add(TimingAggregate timing) {
            this.totalNanos += timing.totalNanos;
            this.samples += timing.samples;
            if (this.hottest == null || timing.totalNanos > this.hottest.totalNanos) {
                this.hottest = timing;
            }
        }

        private GpuModSummary toSummary(long allSampledNanos) {
            double percent = allSampledNanos <= 0L ? 0.0 : this.totalNanos * 100.0 / allSampledNanos;
            String hottestLocation = this.hottest == null ? "unknown" : timingLocation(this.hottest.key);
            return new GpuModSummary(this.modId, this.totalNanos, this.samples, percent, hottestLocation);
        }

        private static String timingLocation(TimingKey key) {
            String location = key.lineNumber > 0 ? key.fileName + ":" + key.lineNumber : key.fileName;
            String name = key.scope == null || key.scope.isBlank() ? key.label : key.scope;
            return name == null || name.isBlank() ? location : name + " @ " + location;
        }
    }

    private record FoldedStackKey(String modId, String foldedStack) {
    }

    private static final class FoldedStackAggregate {
        private final FoldedStackKey key;
        private long gpuNanos;
        private int samples;

        private FoldedStackAggregate(FoldedStackKey key) {
            this.key = key;
        }

        private void add(long nanos, int sampleCount) {
            this.gpuNanos += nanos;
            this.samples += sampleCount;
        }

        private FoldedStackSample toSummary() {
            return new FoldedStackSample(this.key.modId, this.key.foldedStack, this.gpuNanos, this.samples);
        }
    }

    private static final class ScopeAggregate {
        private final String systemId;
        private long cpuNanos;
        private long maximumCpuNanos;
        private int invocations;
        private int stateLeaks;

        private ScopeAggregate(String systemId) {
            this.systemId = systemId;
        }

        private void add(long elapsed, boolean leaked) {
            this.cpuNanos += elapsed;
            this.maximumCpuNanos = Math.max(this.maximumCpuNanos, elapsed);
            this.invocations++;
            if (leaked) {
                this.stateLeaks++;
            }
        }

        private ScopeSummary toSummary() {
            return new ScopeSummary(this.systemId, this.cpuNanos, this.maximumCpuNanos, this.invocations, this.stateLeaks);
        }
    }

    private record StateSnapshot(Map<String, String> values) {
        private static StateSnapshot capture() {
            if (!active || !RenderSystem.isOnRenderThreadOrInit()) {
                return new StateSnapshot(Map.of());
            }
            try {
                Map<String, String> values = new LinkedHashMap<>();
                values.put("program", String.valueOf(GL11.glGetInteger(35725)));
                values.put("vertexArray", String.valueOf(GL11.glGetInteger(34229)));
                values.put("drawFramebuffer", String.valueOf(GL11.glGetInteger(36006)));
                values.put("activeTexture", String.valueOf(GL11.glGetInteger(34016)));
                values.put("texture2D", String.valueOf(GL11.glGetInteger(32873)));
                values.put("blend", String.valueOf(GL11.glIsEnabled(GL11.GL_BLEND)));
                values.put("depthTest", String.valueOf(GL11.glIsEnabled(GL11.GL_DEPTH_TEST)));
                values.put("depthMask", String.valueOf(GL11.glGetBoolean(2930)));
                values.put("cull", String.valueOf(GL11.glIsEnabled(GL11.GL_CULL_FACE)));
                values.put("scissor", String.valueOf(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)));
                int[] viewport = new int[4];
                GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
                values.put("viewport", viewport[0] + "," + viewport[1] + "," + viewport[2] + "," + viewport[3]);
                return new StateSnapshot(Map.copyOf(values));
            } catch (Throwable ignored) {
                return new StateSnapshot(Map.of());
            }
        }

        private Map<String, String> diff(StateSnapshot after) {
            if (this.values.isEmpty() || after.values.isEmpty()) {
                return Map.of();
            }
            Map<String, String> differences = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : this.values.entrySet()) {
                String later = after.values.get(entry.getKey());
                if (later != null && !entry.getValue().equals(later)) {
                    differences.put(entry.getKey(), entry.getValue() + " -> " + later);
                }
            }
            return differences;
        }
    }

    public static final class Scope implements AutoCloseable {
        private static final Scope NOOP = new Scope(null);
        private final ScopeFrame frame;
        private boolean closed;

        private Scope(ScopeFrame frame) {
            this.frame = frame;
        }

        @Override
        public void close() {
            if (this.frame == null || this.closed) {
                return;
            }
            this.closed = true;
            closeScope(this.frame);
        }
    }

    public record GpuTimingSummary(String modId, String scope, String label, String fileName, int lineNumber,
                                   long totalNanos, long averageNanos, long p95Nanos, long maximumNanos,
                                   int samples, long sampledIndices, double percentOfSamples, String foldedStack) {
        public String owner() {
            return this.scope == null || this.scope.isBlank() ? this.modId : this.modId + "/" + this.scope;
        }

        public String location() {
            return this.lineNumber > 0 ? this.fileName + ":" + this.lineNumber : this.fileName;
        }
    }

    /** One mod's share of all resolved asynchronous GPU timestamp samples. */
    public record GpuModSummary(String modId, long totalNanos, int samples, double percentOfSamples,
                                String hottestLocation) {
    }

    /** Flamegraph-ready folded Java stack weighted by sampled GPU nanoseconds. */
    public record FoldedStackSample(String modId, String foldedStack, long gpuNanos, int samples) {
    }

    public record DebugEvent(long elapsedNanos, long frame, String source, String type, int id, String severity,
                             String message, String scope, String modId, String location) {
    }

    public record StateLeak(String systemId, long elapsedNanos, long frame, Map<String, String> differences,
                            String modId, String location) {
    }

    public record DiagnosticsStatus(String debugProvider, boolean timerAvailable, long observedDrawCalls,
                                    long timedDrawSamples, int pendingTimers, long droppedTimerSamples,
                                    int timerErrors, int debugEvents, int stateLeaks) {
    }

    public record ScopeSummary(String systemId, long cpuNanos, long maximumCpuNanos,
                               int invocations, int stateLeaks) {
    }
}
