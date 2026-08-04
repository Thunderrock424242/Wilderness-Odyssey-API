package com.thunder.wildernessodysseyapi.ecosystem.api;

import net.minecraft.world.entity.PathfinderMob;

import java.util.Optional;

/** Read-only boundary for cached, bounded cover discovery. */
@FunctionalInterface
public interface ShelterLocator {

    /** Finds a standable covered position without modifying blocks or loading chunks. */
    Optional<EnvironmentalContext.ShelterTarget> find(PathfinderMob animal, SpeciesBehaviorProfile profile, int radius);
}
