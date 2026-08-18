package com.thunder.wildernessodysseyapi.vegetation.api;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Computes one bounded registered-plant reaction.
 *
 * <p>Implementations run on the server thread after a chunk-level climate
 * sample. Returning the current state performs no block update. Implementations
 * should never force chunks to load or scan outward from the supplied plant.</p>
 */
@FunctionalInterface
public interface ReactivePlantBehavior {

    /** Returns the desired visual block state after consulting regional context. */
    BlockState update(ReactivePlantUpdateContext context);
}
