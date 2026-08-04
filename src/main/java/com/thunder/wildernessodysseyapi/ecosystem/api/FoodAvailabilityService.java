package com.thunder.wildernessodysseyapi.ecosystem.api;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;

import java.util.List;
import java.util.Optional;

/** Boundary for inexpensive cached forage estimates and bounded prey selection. */
public interface FoodAvailabilityService {

    /** Returns a normalized cached estimate of nearby tagged forage blocks. */
    double availability(PathfinderMob animal, int radius);

    /** Returns eligible prey and the sampled local prey population. */
    PredatorFoodSample prey(PathfinderMob predator, SpeciesBehaviorProfile profile, int radius);

    /** Bounded prey population result used by the predator safeguard policy. */
    record PredatorFoodSample(List<LivingEntity> population, Optional<LivingEntity> target) {
    }
}
