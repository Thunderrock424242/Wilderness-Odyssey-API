package com.thunder.wildernessodysseyapi.ecosystem.api;

import net.minecraft.world.entity.PathfinderMob;

import java.util.Optional;

/** Read-only boundary for cached, bounded cover discovery. */
@FunctionalInterface
public interface ShelterLocator {

    /** Finds a standable covered position without modifying blocks or loading chunks. */
    Optional<EnvironmentalContext.ShelterTarget> find(PathfinderMob animal, SpeciesBehaviorProfile profile, int radius);

    /**
     * Finds cover with a soft species preference.
     *
     * <p>The compatibility default preserves third-party locators. Wilderness's
     * built-in locator overrides this method and still falls back to any safe
     * cover when the preferred category is unavailable.</p>
     */
    default Optional<EnvironmentalContext.ShelterTarget> find(
            PathfinderMob animal,
            SpeciesBehaviorProfile profile,
            int radius,
            ShelterPreference preference
    ) {
        return preference == ShelterPreference.NONE
                ? Optional.empty()
                : find(animal, profile, radius);
    }
}
