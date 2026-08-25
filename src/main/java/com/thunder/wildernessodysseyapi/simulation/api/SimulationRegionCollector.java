package com.thunder.wildernessodysseyapi.simulation.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Bounded server-thread sink for regions already known to a simulation owner.
 *
 * <p>Participants may publish positions from bounded owner state such as a
 * persistent group ledger. They must not scan chunks, entities, or the world to
 * discover work. Accepted positions are coalesced by the Simulation Engine's
 * existing regional queue and never force a chunk load.</p>
 */
@FunctionalInterface
public interface SimulationRegionCollector {

    /**
     * Requests optional orchestration for one known position.
     *
     * @return {@code false} only when the bounded regional queue rejects work
     */
    boolean request(ServerLevel level, BlockPos position);
}
