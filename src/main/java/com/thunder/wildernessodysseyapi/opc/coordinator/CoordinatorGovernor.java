package com.thunder.wildernessodysseyapi.opc.coordinator;

/** Pure server-thread policy. No Minecraft state, executor, or persistence is owned here. */
public final class CoordinatorGovernor {
    public enum State { ENTRY, CHUNK_PRESSURE, OVERLOADED, RECOVERY, NORMAL, STOPPING }
    public enum Module { BACKGROUND, SAVE, DATA, TICK }

    private final long maximumBudget;
    private final double headroomFraction;
    private final int stableTicks;
    private final int cooldownTicks;
    private final Watchdog[] watchdogs = java.util.stream.IntStream.range(0, Module.values().length)
            .mapToObj(ignored -> new Watchdog()).toArray(Watchdog[]::new);
    private State state = State.ENTRY;
    private boolean ready;
    private boolean pressureLatched;
    private int healthyTicks;
    private long tick;
    private long chunkPressureUntil;
    private long budget;
    private double recovery;

    public CoordinatorGovernor(long maximumBudget, double headroomFraction, int stableTicks, int cooldownTicks) {
        this.maximumBudget = Math.max(0, maximumBudget);
        this.headroomFraction = Math.max(0, Math.min(0.25, headroomFraction));
        this.stableTicks = Math.max(1, stableTicks);
        this.cooldownTicks = Math.max(1, cooldownTicks);
    }

    public void ready() { ready = true; }
    public void chunkWait(long currentTick) { chunkPressureUntil = Math.max(chunkPressureUntil, currentTick + 100); }
    public void stop() { state = State.STOPPING; budget = 0; }

    /** CPU/GC negative values mean unavailable. Chunk waits are request-pressure signals, not throughput. */
    public void observe(long currentTick, double ewmaMspt, double cpu, double gc, double queuePressure) {
        tick = currentTick;
        if (state == State.STOPPING) return;
        boolean overloaded = !Double.isFinite(ewmaMspt) || ewmaMspt >= 45
                || cpu >= 0.90 || gc >= 0.10 || queuePressure >= 0.75;
        if (overloaded || currentTick < chunkPressureUntil) {
            pressureLatched = true;
            healthyTicks = 0;
            recovery = 0;
            budget = 0;
            state = overloaded ? State.OVERLOADED : State.CHUNK_PRESSURE;
            return;
        }
        if (!ready) { state = State.ENTRY; budget = 0; return; }
        if (ewmaMspt < 40 && cpu < 0.80 && gc < 0.05 && queuePressure < 0.50) healthyTicks++;
        else healthyTicks = 0;
        if (healthyTicks < stableTicks && (pressureLatched || recovery == 0)) {
            state = pressureLatched ? State.RECOVERY : State.ENTRY;
            budget = 0;
            return;
        }
        pressureLatched = false;
        recovery = Math.min(1, recovery + 0.02);
        budget = (long) (Math.min(maximumBudget, Math.max(0, 50 - ewmaMspt) * 1_000_000 * headroomFraction) * recovery);
        state = recovery < 1 ? State.RECOVERY : State.NORMAL;
        for (Watchdog watchdog : watchdogs) {
            if (tick >= watchdog.pausedUntil && watchdog.strikes >= 3) {
                watchdog.strikes = 0;
                watchdog.multiplier = 0.25;
            }
            if (tick >= watchdog.pausedUntil) watchdog.multiplier = Math.min(1, watchdog.multiplier + 0.005);
        }
    }

    public long budgetNanos() { return budget; }
    public double recoveryMultiplier() { return recovery; }
    public State state() { return state; }
    public long moduleBudget(Module module) {
        Watchdog watchdog = watchdogs[module.ordinal()];
        return tick < watchdog.pausedUntil ? 0 : (long) (budget * watchdog.multiplier);
    }

    /** Non-preemptible operations are measured after returning; repeated overruns stop later admissions. */
    public void recordCost(Module module, long cost, long admittedBudget, long currentTick) {
        Watchdog watchdog = watchdogs[module.ordinal()];
        watchdog.totalCost += Math.max(0, cost);
        watchdog.maximumCost = Math.max(watchdog.maximumCost, cost);
        if (cost > Math.max(500_000, admittedBudget + admittedBudget / 4)) {
            watchdog.multiplier *= 0.5;
            if (++watchdog.strikes >= 3) watchdog.pausedUntil = currentTick + cooldownTicks;
        }
    }

    public ModuleSnapshot snapshot(Module module) {
        Watchdog watchdog = watchdogs[module.ordinal()];
        return new ModuleSnapshot(moduleBudget(module), watchdog.totalCost, watchdog.maximumCost,
                watchdog.strikes, Math.max(0, watchdog.pausedUntil - tick));
    }

    public record ModuleSnapshot(long budgetNanos, long totalCostNanos, long maximumCostNanos, int strikes, long cooldownRemainingTicks) { }
    private static final class Watchdog {
        private double multiplier = 1;
        private int strikes;
        private long pausedUntil;
        private long totalCost;
        private long maximumCost;
    }
}
