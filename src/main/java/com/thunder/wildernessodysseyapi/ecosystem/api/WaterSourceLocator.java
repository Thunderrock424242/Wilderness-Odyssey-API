package com.thunder.wildernessodysseyapi.ecosystem.api;

import net.minecraft.world.entity.PathfinderMob;

import java.util.Optional;

/** Read-only boundary for bounded shoreline discovery. */
@FunctionalInterface
public interface WaterSourceLocator {

    /** Finds a valid water cell and a safe approach position without loading chunks. */
    Optional<EnvironmentalContext.WaterTarget> find(PathfinderMob animal, SpeciesBehaviorProfile profile, int radius);
}
