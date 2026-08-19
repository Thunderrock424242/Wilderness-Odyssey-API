package com.thunder.wildernessodysseyapi.performance.tickengine;

import java.util.Objects;

/**
 * Applies explicit missed-tick policies without creating an unbounded catch-up spike.
 */
public final class TickDebtManager {
    private volatile boolean enabled = true;
    private volatile int maximumIndividualSteps = 8;

    /** Configures debt collapsing and the bounded individual catch-up limit. */
    public void configure(boolean enabled, int maximumIndividualSteps) {
        this.enabled = enabled;
        this.maximumIndividualSteps = Math.max(1, maximumIndividualSteps);
    }

    /**
     * Advances eligible simulation and returns the tick that is now accounted for.
     *
     * <p>Individual debt that exceeds the per-call cap remains debt because the
     * returned tick advances only by the work actually performed.</p>
     */
    public Result process(TickDebtAware simulation, long lastUpdateTick, long currentTick) {
        Objects.requireNonNull(simulation, "simulation");
        long elapsed = currentTick >= lastUpdateTick ? currentTick - lastUpdateTick : 0L;
        if (elapsed == 0L) {
            return new Result(lastUpdateTick, 0L, 0L);
        }
        if (!enabled) {
            simulation.advanceSimulation(1L);
            return new Result(lastUpdateTick + 1L, 1L, elapsed - 1L);
        }

        return switch (simulation.missedTickPolicy()) {
            case COLLAPSE -> {
                simulation.advanceSimulation(elapsed);
                yield new Result(currentTick, elapsed, 0L);
            }
            case DISCARD -> new Result(currentTick, 0L, 0L);
            case INDIVIDUAL -> processIndividual(simulation, lastUpdateTick, elapsed);
        };
    }

    private Result processIndividual(TickDebtAware simulation, long lastUpdateTick, long elapsed) {
        long steps = Math.min(elapsed, maximumIndividualSteps);
        for (long step = 0L; step < steps; step++) {
            simulation.advanceSimulation(1L);
        }
        return new Result(lastUpdateTick + steps, steps, elapsed - steps);
    }

    /** Result of one bounded debt-processing call. */
    public record Result(long accountedThroughTick, long simulatedTicks, long remainingTicks) {
    }
}
