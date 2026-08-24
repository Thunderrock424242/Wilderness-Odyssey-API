package com.thunder.wildernessodysseyapi.simulation.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable fact published after an authoritative world-system action succeeds.
 *
 * <p>Events are notifications, not commands: listeners must use the owning
 * weather, water, ecosystem, vegetation, meteor, or Riftfall API for any
 * permitted mutation.</p>
 */
public interface SimulationEvent {

    /** Returns the stable typed event ID. */
    ResourceLocation type();

    /** Returns the dimension containing the event. */
    ResourceLocation dimension();

    /** Returns the immutable event position. */
    BlockPos position();

    /** Returns the authoritative server game time at publication. */
    long gameTime();
}
