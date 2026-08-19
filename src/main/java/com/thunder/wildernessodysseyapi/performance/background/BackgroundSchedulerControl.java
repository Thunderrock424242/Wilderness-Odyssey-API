package com.thunder.wildernessodysseyapi.performance.background;

/**
 * Integration boundary used by the Tick Engine to govern background capacity.
 */
public interface BackgroundSchedulerControl {

    /** Applies limits that remain in effect until they are replaced. */
    void setExternalControl(BackgroundBudgetControl control);

    /** Returns a cheap zero-to-one view of the scheduler's current queue pressure. */
    double queuePressure();

    /** Returns the number of tasks currently waiting. */
    int queuedTasks();
}
