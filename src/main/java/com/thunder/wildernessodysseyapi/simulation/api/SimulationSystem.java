package com.thunder.wildernessodysseyapi.simulation.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Optional participant in Wilderness Odyssey's regional orchestration layer.
 *
 * <p>Implementations retain authority over their own state and are invoked on
 * the logical server thread. Mandatory gameplay clocks and safety work must
 * remain on their owning subsystem's normal lifecycle path because this
 * contract may be deferred when the server has no spare time.</p>
 */
public interface SimulationSystem {

    /** Returns the stable ID used for registration, metrics, and diagnostics. */
    ResourceLocation id();

    /** Returns whether current configuration permits optional orchestration work. */
    default boolean isEnabled() {
        return true;
    }

    /** Performs a cheap relevance check without scanning the world or loading chunks. */
    default boolean shouldUpdate(SimulationContext context) {
        return true;
    }

    /**
     * Performs one bounded server-thread update for the supplied region.
     *
     * <p>Expensive pure computation should submit
     * {@link SimulationContext#immutableSnapshot()} through the existing Data
     * Engine rather than retaining this live context on a worker thread.</p>
     */
    void update(SimulationContext context) throws Exception;

    /** Notifies the participant that a new server lifecycle owns runtime state. */
    default void onServerStarted(MinecraftServer server) {
    }

    /** Notifies the participant that reload-backed settings may have changed. */
    default void onConfigurationReload() {
    }

    /** Releases transient state associated with one unloading dimension. */
    default void onLevelUnload(ResourceLocation dimension) {
    }

    /** Releases all remaining transient state before the server lifecycle ends. */
    default void onServerStopping() {
    }
}
