package com.thunder.wildernessodysseyapi.opc.core;

import com.thunder.wildernessodysseyapi.opc.diagnostics.PerformanceDiagnosticsConfig.Mode;
import com.thunder.wildernessodysseyapi.opc.coordinator.CoordinatorConfig;
import com.thunder.wildernessodysseyapi.opc.coordinator.CoordinatorGovernor;
import com.thunder.wildernessodysseyapi.opc.coordinator.DisposableWorkTracker;
import com.thunder.wildernessodysseyapi.opc.tickengine.TickPressure;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Session owner for WO optional admission, measured budgets and recovery. Server-thread policy
 * never owns vanilla ticking, chunks, serialization, third-party pools or mutable worker inputs.
 * The legacy PerformanceCoordinator API forwards here; there is only one control session.
 */
public final class OdysseyPerformanceCore {
    private static final DeferredWorkScheduler DEFERRED = new DeferredWorkScheduler();
    private static final int MODULE_COUNT = CoordinatorGovernor.Module.values().length;
    private static volatile Session current;

    private OdysseyPerformanceCore() { }

    public static DeferredWorkScheduler deferred() { return DEFERRED; }

    public static void start(Object server, CoordinatorConfig.Values settings) { start(server, settings, false); }

    public static void start(Object server, CoordinatorConfig.Values settings, boolean unified) {
        clear();
        current = new Session(server, settings, unified);
    }

    public static void ready(Object server) {
        Session session = owned(server);
        if (session != null) session.governor.ready();
    }

    public static void beginTick(Object server, long tick, long now) {
        Session session = owned(server);
        if (session == null) return;
        session.tick = tick;
        session.tickStart = now;
        session.consumed = 0;
        java.util.Arrays.fill(session.moduleCosts, 0);
        if (session.chunkPressure.getAndSet(false)) session.governor.chunkWait(tick);
    }

    public static void observe(Object server, double ewma, double cpu, double gc, double queues) {
        Session session = owned(server);
        if (session == null) return;
        session.governor.observe(session.tick, ewma, cpu, gc, queues);
        session.allowDisposable = session.governor.moduleBudget(CoordinatorGovernor.Module.BACKGROUND) > 0;
    }

    public static void chunkWait(Object server) {
        Session session = owned(server);
        if (session != null) session.chunkPressure.set(true);
    }

    public static boolean adaptive() { Session session = current; return session != null && session.unified && governsAsync(); }
    public static double recoveryMultiplier() { Session session = current; return session == null ? 1 : session.governor.recoveryMultiplier(); }

    public static long constrainBudget(long requested) {
        return governsAsync() ? budget(CoordinatorGovernor.Module.BACKGROUND, requested) : requested;
    }

    /** Only explicitly optional processing uses this API; required updates retain their native path. */
    public static long optionalBudget(CoordinatorGovernor.Module module, long requested) {
        return adaptive() ? budget(module, requested) : requested;
    }

    public static boolean governsAsync() {
        Session session = current;
        return session != null && session.settings.asyncGovernor() != Mode.OFF;
    }

    public static boolean backgroundAllowed() {
        Session session = current;
        return session == null || session.settings.asyncGovernor() == Mode.OFF || session.allowDisposable;
    }

    /** Consistent pressure for all adaptive optional lanes; legacy interval policy remains available. */
    public static TickPressure pressure() {
        Session session = current;
        if (session == null) return TickPressure.RELAXED;
        return switch (session.governor.state()) {
            case ENTRY, CHUNK_PRESSURE, STOPPING -> TickPressure.CRITICAL;
            case OVERLOADED -> TickPressure.OVERLOADED;
            case RECOVERY -> TickPressure.HIGH;
            case NORMAL -> TickPressure.RELAXED;
        };
    }

    public static long budget(CoordinatorGovernor.Module module, long requested) {
        Session session = current;
        if (session == null || session.tickStart == 0) return 0;
        long wallRemaining = Math.max(0, 50_000_000 - Math.max(0, System.nanoTime() - session.tickStart));
        long totalRemaining = Math.max(0, session.governor.budgetNanos() - session.consumed);
        // Fixed module ceilings reserve a fair opportunity even for a lane visited later in the tick.
        // Unused allowance is not banked: recovery cannot trigger a deferred-work burst.
        long moduleLimit = session.governor.moduleBudget(module);
        if (session.unified) moduleLimit = Math.min(moduleLimit, session.governor.budgetNanos() / MODULE_COUNT);
        long moduleRemaining = Math.max(0, moduleLimit - session.moduleCosts[module.ordinal()]);
        return Math.min(Math.max(0, requested), Math.min(wallRemaining, Math.min(totalRemaining, moduleRemaining)));
    }

    public static void recordCost(CoordinatorGovernor.Module module, long cost, long admitted) {
        Session session = current;
        if (session == null || module != CoordinatorGovernor.Module.SAVE && session.settings.asyncGovernor() == Mode.OFF) return;
        long measured = Math.max(0, cost);
        session.consumed = saturatedAdd(session.consumed, measured);
        session.moduleCosts[module.ordinal()] = saturatedAdd(session.moduleCosts[module.ordinal()], measured);
        session.governor.recordCost(module, measured, admitted, session.tick);
    }

    public static boolean submitDisposable(Executor executor, Runnable work) {
        Session session = current;
        if (session == null || session.settings.asyncGovernor() == Mode.OFF) {
            try { executor.execute(work); return true; }
            catch (java.util.concurrent.RejectedExecutionException rejected) { return false; }
        }
        return session.disposable.submit(executor, work, session.allowDisposable);
    }

    public static void stopping(Object server) {
        Session session = owned(server);
        if (session == null) return;
        session.governor.stop();
        session.allowDisposable = false;
        session.disposable.stop();
    }

    public static void clear() {
        Session prior = current;
        current = null;
        if (prior != null) prior.disposable.stop();
    }

    public static String state() { Session session = current; return session == null ? "DISABLED" : session.governor.state().name(); }

    /** Cold-path counters; no world inspection, percentile sorting, or queue traversal. */
    public static Map<String, Long> metrics() {
        Session session = current;
        if (session == null) return Map.of();
        var work = session.disposable.snapshot();
        Map<String, Long> values = new HashMap<>();
        values.put("coordinatorOptionalBudgetNanos", session.governor.budgetNanos());
        values.put("coordinatorConsumedNanos", session.consumed);
        values.put("opcUnifiedBudget", session.unified ? 1L : 0L);
        for (var module : CoordinatorGovernor.Module.values()) {
            var stats = session.governor.snapshot(module);
            String name = module.name().substring(0, 1) + module.name().substring(1).toLowerCase(java.util.Locale.ROOT);
            values.put("coordinator" + name + "CostNanos", stats.totalCostNanos());
            values.put("coordinator" + name + "CooldownTicks", stats.cooldownRemainingTicks());
            values.put("opc" + name + "TickCostNanos", session.moduleCosts[module.ordinal()]);
        }
        values.put("coordinatorDisposableInFlight", (long) work.inFlight());
        values.put("coordinatorDisposableRejected", work.rejected());
        values.put("coordinatorDisposableCancelled", work.cancelled());
        values.put("coordinatorDisposableCostNanos", work.completedCostNanos());
        return Map.copyOf(values);
    }

    private static long saturatedAdd(long value, long increment) { return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment; }
    private static Session owned(Object server) { Session session = current; return session != null && session.server == server ? session : null; }

    private static final class Session {
        private final Object server;
        private final CoordinatorConfig.Values settings;
        private final boolean unified;
        private final CoordinatorGovernor governor;
        private final DisposableWorkTracker disposable = new DisposableWorkTracker(2);
        private final AtomicBoolean chunkPressure = new AtomicBoolean();
        private final long[] moduleCosts = new long[CoordinatorGovernor.Module.values().length];
        private volatile boolean allowDisposable;
        private long tick;
        private long tickStart;
        private long consumed;

        private Session(Object server, CoordinatorConfig.Values settings, boolean unified) {
            this.server = server;
            this.settings = settings;
            this.unified = unified;
            governor = new CoordinatorGovernor((long) (settings.maximumOptionalMillis() * 1_000_000), settings.headroomFraction(),
                    settings.entryStableTicks(), settings.cooldownTicks());
        }
    }
}
