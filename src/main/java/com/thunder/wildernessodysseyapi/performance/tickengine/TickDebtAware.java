package com.thunder.wildernessodysseyapi.performance.tickengine;

/**
 * Opt-in simulation contract describing how a subsystem handles missed updates.
 */
public interface TickDebtAware {

    /** Declares whether missed updates collapse, remain individual, or may be discarded. */
    MissedTickPolicy missedTickPolicy();

    /** Advances one collapsed interval or one individual tick, depending on the declared policy. */
    void advanceSimulation(long elapsedTicks);

    enum MissedTickPolicy {
        COLLAPSE,
        INDIVIDUAL,
        DISCARD
    }
}
