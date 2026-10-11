package com.thunder.wildernessodysseyapi.performance.coordinator;

import com.thunder.wildernessodysseyapi.diagnostics.performance.PerformanceDiagnosticsConfig.Mode;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server-session coordinator for WO-owned optional work. Policy/costs belong to
 * the server thread; workers read only admission and publish detached pressure.
 * No chunk lifecycle or third-party executor is controlled by this service.
 */
public final class PerformanceCoordinator {
    private static volatile Session current;

    private PerformanceCoordinator() { }

    public static void start(Object server, CoordinatorConfig.Values settings) {
        clear();
        current = new Session(server, settings);
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

    /** Constrains existing optional work, with one shared ledger across schedulers and saving. */
    public static long constrainBudget(long requested) {
        Session session = current;
        if (session == null || session.settings.asyncGovernor() == Mode.OFF) return requested;
        return budget(CoordinatorGovernor.Module.BACKGROUND, requested);
    }

    public static boolean governsAsync() {
        Session session = current;
        return session != null && session.settings.asyncGovernor() != Mode.OFF;
    }
    public static boolean backgroundAllowed() {
        Session session = current;
        return session == null || session.settings.asyncGovernor() == Mode.OFF || session.allowDisposable;
    }

    public static long budget(CoordinatorGovernor.Module module, long requested) {
        Session session = current;
        if (session == null) return 0;
        long wallRemaining = Math.max(0, 50_000_000 - Math.max(0, System.nanoTime() - session.tickStart));
        long allowance = Math.max(0, session.governor.moduleBudget(module) - session.consumed);
        return Math.min(Math.max(0, requested), Math.min(wallRemaining, allowance));
    }

    public static void recordCost(CoordinatorGovernor.Module module, long cost, long admitted) {
        Session session = current;
        if (session == null || module == CoordinatorGovernor.Module.BACKGROUND && session.settings.asyncGovernor() == Mode.OFF) return;
        session.consumed += Math.max(0, cost);
        session.governor.recordCost(module, cost, admitted, session.tick);
    }

    /** Returns false on pressure/capacity/stop. Only callers whose work may be discarded use this API. */
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

    public static String state() {
        Session session = current;
        return session == null ? "DISABLED" : session.governor.state().name();
    }

    /** Cold-path snapshots for commands/export; no tick percentile sorting or live Minecraft access. */
    public static Map<String, Long> metrics() {
        Session session = current;
        if (session == null) return Map.of();
        var work = session.disposable.snapshot();
        var background = session.governor.snapshot(CoordinatorGovernor.Module.BACKGROUND);
        var save = session.governor.snapshot(CoordinatorGovernor.Module.SAVE);
        return Map.ofEntries(
                Map.entry("coordinatorOptionalBudgetNanos", session.governor.budgetNanos()),
                Map.entry("coordinatorConsumedNanos", session.consumed),
                Map.entry("coordinatorBackgroundCostNanos", background.totalCostNanos()),
                Map.entry("coordinatorBackgroundCooldownTicks", background.cooldownRemainingTicks()),
                Map.entry("coordinatorSaveCostNanos", save.totalCostNanos()),
                Map.entry("coordinatorSaveCooldownTicks", save.cooldownRemainingTicks()),
                Map.entry("coordinatorDisposableInFlight", (long) work.inFlight()),
                Map.entry("coordinatorDisposableRejected", work.rejected()),
                Map.entry("coordinatorDisposableCancelled", work.cancelled()),
                Map.entry("coordinatorDisposableCostNanos", work.completedCostNanos()));
    }

    private static Session owned(Object server) {
        Session session = current;
        return session != null && session.server == server ? session : null;
    }

    private static final class Session {
        private final Object server;
        private final CoordinatorConfig.Values settings;
        private final CoordinatorGovernor governor;
        private final DisposableWorkTracker disposable = new DisposableWorkTracker(2);
        private final AtomicBoolean chunkPressure = new AtomicBoolean();
        private volatile boolean allowDisposable;
        private long tick;
        private long tickStart;
        private long consumed;

        private Session(Object server, CoordinatorConfig.Values settings) {
            this.server = server;
            this.settings = settings;
            governor = new CoordinatorGovernor((long) (settings.maximumOptionalMillis() * 1_000_000), settings.headroomFraction(),
                    settings.entryStableTicks(), settings.cooldownTicks());
        }
    }
}
