package com.thunder.wildernessodysseyapi.ecosystem.api;

import com.thunder.wildernessodysseyapi.ecosystem.state.AnimalNeedsState;
import net.minecraft.world.entity.PathfinderMob;

import java.util.Optional;

/** Boundary for visible threats, remembered danger, and herd warning propagation. */
public interface ThreatAwarenessService {

    /** Returns the nearest current or remembered threat inside the configured radius. */
    Optional<EnvironmentalContext.Threat> assess(
            PathfinderMob animal,
            SpeciesBehaviorProfile profile,
            AnimalNeedsState needs,
            int radius
    );

    /** Shares an active flight trigger with nearby same-species herd members. */
    void warnHerd(
            PathfinderMob animal,
            SpeciesBehaviorProfile profile,
            EnvironmentalContext.Threat threat,
            int radius
    );
}
