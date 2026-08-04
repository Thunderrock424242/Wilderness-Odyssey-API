package com.thunder.wildernessodysseyapi.ecosystem.api;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/**
 * Immutable data-pack definition for one group of animals.
 *
 * <p>Selectors decide which entity types receive the profile. Nested settings
 * keep individual behavior families independently configurable and allow pack
 * authors to extend the system without Java entity-type switches.</p>
 */
public record SpeciesBehaviorProfile(
        ResourceLocation id,
        Set<ResourceLocation> entities,
        Set<ResourceLocation> entityTags,
        Needs needs,
        Drinking drinking,
        Shelter shelter,
        Herd herd,
        Prey prey,
        Predator predator
) {

    /** Low-cost rates applied during staggered need updates. */
    public record Needs(
            double thirstPerMinute,
            double hungerPerMinute,
            double restPerMinute,
            double hotTemperatureCelsius,
            double heatThirstMultiplier,
            double activityThirstMultiplier,
            boolean nocturnal
    ) {
    }

    /** Shoreline-seeking and drinking behavior controls. */
    public record Drinking(
            boolean enabled,
            double thirstThreshold,
            int searchRadius,
            int durationTicks,
            double moveSpeed,
            double thirstRestored,
            boolean canSwim,
            double maximumSafeDepth
    ) {
    }

    /** Localized-weather thresholds and shelter release timing. */
    public record Shelter(
            boolean enabled,
            int searchRadius,
            double precipitationThreshold,
            double thunderThreshold,
            double windThreshold,
            int minimumReleaseDelayTicks,
            int maximumReleaseDelayTicks,
            double moveSpeed
    ) {
    }

    /** Same-species spacing and regrouping controls. */
    public record Herd(
            boolean enabled,
            int searchRadius,
            double preferredDistance,
            double motivationThreshold,
            double moveSpeed
    ) {
    }

    /** Threat detection, memory, flight, and herd-warning controls. */
    public record Prey(
            boolean enabled,
            int threatRadius,
            int threatMemoryTicks,
            int propagationRadius,
            double fleeSpeed,
            List<ResourceLocation> threatTags
    ) {
    }

    /** Bounded hunger-gated hunting controls for ecosystem predators. */
    public record Predator(
            boolean enabled,
            int huntRadius,
            double hungerThreshold,
            int huntCooldownTicks,
            int minimumNearbyPrey,
            int attackIntervalTicks,
            double moveSpeed,
            boolean wildOnly,
            List<ResourceLocation> preyTags
    ) {
    }
}
