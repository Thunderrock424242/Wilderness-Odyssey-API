package com.thunder.wildernessodysseyapi.performance.background;

/**
 * Opt-in elapsed-time simulation contract for systems that can collapse sleep.
 *
 * <p>Implementations receive elapsed game ticks once. The framework never
 * expands the value into a loop of missed per-tick calls.</p>
 */
@FunctionalInterface
public interface ElapsedTimeSimulation {

    /** Advances the implementation by a caller-validated elapsed tick count. */
    void simulateElapsedTime(long elapsedTicks);

    /** Computes monotonic elapsed game time and safely treats rollback as no elapsed time. */
    static long elapsedTicks(long lastSimulationTick, long currentTick) {
        return currentTick >= lastSimulationTick ? currentTick - lastSimulationTick : 0L;
    }
}
