package com.thunder.wildernessodysseyapi.simulation.region;

/** Identifies why a region entered the optional orchestration queue. */
public enum SimulationTrigger {
    PLAYER_INTEREST(0),
    EXPLICIT_REQUEST(1),
    WORLD_DISTURBANCE(2);

    private final int urgency;

    SimulationTrigger(int urgency) {
        this.urgency = urgency;
    }

    /** Retains the more consequential cause when duplicate requests coalesce. */
    public static SimulationTrigger moreUrgent(SimulationTrigger first, SimulationTrigger second) {
        return first.urgency >= second.urgency ? first : second;
    }
}
