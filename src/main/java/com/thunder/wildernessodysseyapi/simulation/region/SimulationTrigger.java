package com.thunder.wildernessodysseyapi.simulation.region;

/** Identifies why a region entered the optional orchestration queue. */
public enum SimulationTrigger {
    SYSTEM_RELEVANCE(0),
    PLAYER_INTEREST(1),
    EXPLICIT_REQUEST(2),
    WORLD_DISTURBANCE(3);

    private final int urgency;

    SimulationTrigger(int urgency) {
        this.urgency = urgency;
    }

    /** Retains the more consequential cause when duplicate requests coalesce. */
    public static SimulationTrigger moreUrgent(SimulationTrigger first, SimulationTrigger second) {
        return first.urgency >= second.urgency ? first : second;
    }
}
