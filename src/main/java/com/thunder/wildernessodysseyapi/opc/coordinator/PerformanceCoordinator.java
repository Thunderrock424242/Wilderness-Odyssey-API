package com.thunder.wildernessodysseyapi.opc.coordinator;

import com.thunder.wildernessodysseyapi.opc.core.OdysseyPerformanceCore;
import java.util.Map;
import java.util.concurrent.Executor;

/** Compatibility entry point; Odyssey Performance Core owns the single runtime session. */
public final class PerformanceCoordinator {
    private PerformanceCoordinator() { }
    public static void start(Object server, CoordinatorConfig.Values settings) { OdysseyPerformanceCore.start(server, settings); }
    public static void ready(Object server) { OdysseyPerformanceCore.ready(server); }
    public static void beginTick(Object server, long tick, long now) { OdysseyPerformanceCore.beginTick(server, tick, now); }
    public static void observe(Object server, double ewma, double cpu, double gc, double queues) { OdysseyPerformanceCore.observe(server, ewma, cpu, gc, queues); }
    public static void chunkWait(Object server) { OdysseyPerformanceCore.chunkWait(server); }
    public static long constrainBudget(long requested) { return OdysseyPerformanceCore.constrainBudget(requested); }
    public static boolean governsAsync() { return OdysseyPerformanceCore.governsAsync(); }
    public static boolean backgroundAllowed() { return OdysseyPerformanceCore.backgroundAllowed(); }
    public static long budget(CoordinatorGovernor.Module module, long requested) { return OdysseyPerformanceCore.budget(module, requested); }
    public static void recordCost(CoordinatorGovernor.Module module, long cost, long admitted) { OdysseyPerformanceCore.recordCost(module, cost, admitted); }
    public static boolean submitDisposable(Executor executor, Runnable work) { return OdysseyPerformanceCore.submitDisposable(executor, work); }
    public static void stopping(Object server) { OdysseyPerformanceCore.stopping(server); }
    public static void clear() { OdysseyPerformanceCore.clear(); }
    public static String state() { return OdysseyPerformanceCore.state(); }
    public static Map<String, Long> metrics() { return OdysseyPerformanceCore.metrics(); }
}