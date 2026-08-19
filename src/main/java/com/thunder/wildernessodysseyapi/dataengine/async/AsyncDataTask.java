package com.thunder.wildernessodysseyapi.dataengine.async;

/**
 * Split-phase calculation submitted through the Data Engine worker bridge.
 *
 * <p>{@link #compute()} is WORKER SAFE and must use only immutable/copied input.
 * It must never access live Minecraft worlds, chunks, entities, players, or
 * registries with thread-affinity. Validation and application run later on the
 * logical server thread.</p>
 */
public interface AsyncDataTask<R> {
    /** WORKER SAFE. Performs pure calculation against immutable task input. */
    R compute() throws Exception;

    /** SERVER THREAD ONLY. Rechecks whether the computed result is still current. */
    boolean isStillValid(R result);

    /** SERVER THREAD ONLY. Applies a previously validated result. */
    void apply(R result) throws Exception;

    /** SERVER THREAD ONLY. Optional notification when validation rejects stale work. */
    default void onDiscarded(R result) {
    }
}
