package com.thunder.wildernessodysseyapi.ecosystem.api;

import net.minecraft.resources.ResourceLocation;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
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
        Predator predator,
        Environment environment
) {

    /**
     * Retains the original construction shape for compatibility modules built
     * against the first ecosystem API.
     */
    public SpeciesBehaviorProfile(
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
        this(
                id, entities, entityTags, needs, drinking, shelter, herd, prey, predator,
                Environment.compatibilityDefaults(needs, drinking, shelter, herd, prey)
        );
    }

    public SpeciesBehaviorProfile {
        id = Objects.requireNonNull(id, "id");
        entities = Set.copyOf(Objects.requireNonNullElse(entities, Set.of()));
        entityTags = Set.copyOf(Objects.requireNonNullElse(entityTags, Set.of()));
        needs = Objects.requireNonNull(needs, "needs");
        drinking = Objects.requireNonNull(drinking, "drinking");
        shelter = Objects.requireNonNull(shelter, "shelter");
        herd = Objects.requireNonNull(herd, "herd");
        prey = Objects.requireNonNull(prey, "prey");
        predator = Objects.requireNonNull(predator, "predator");
        if (environment == null) {
            environment = Environment.compatibilityDefaults(needs, drinking, shelter, herd, prey);
        }
    }

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

    /**
     * Daily schedule, climate preference, and broad states supported by a species.
     *
     * <p>Weather, thirst, shelter, and grouping keep their dedicated settings;
     * this section controls how those capabilities fit into a low-frequency
     * environmental routine.</p>
     */
    public record Environment(
            ActivityTime activeTime,
            double preferredMinimumTemperatureCelsius,
            double preferredMaximumTemperatureCelsius,
            double hotDryDrinkThresholdReduction,
            double forageHungerThreshold,
            double restThreshold,
            double minimumFoodForForage,
            int localTravelRadius,
            int migrationRadius,
            int scheduleJitterTicks,
            int restDurationTicks,
            int sleepDurationTicks,
            Set<EcosystemBehaviorState> supportedStates
    ) {
        public Environment {
            activeTime = Objects.requireNonNullElse(activeTime, ActivityTime.FLEXIBLE);
            preferredMinimumTemperatureCelsius = finite(
                    preferredMinimumTemperatureCelsius, -10.0);
            preferredMaximumTemperatureCelsius = finite(
                    preferredMaximumTemperatureCelsius, 35.0);
            if (preferredMinimumTemperatureCelsius > preferredMaximumTemperatureCelsius) {
                double swap = preferredMinimumTemperatureCelsius;
                preferredMinimumTemperatureCelsius = preferredMaximumTemperatureCelsius;
                preferredMaximumTemperatureCelsius = swap;
            }
            hotDryDrinkThresholdReduction = unit(hotDryDrinkThresholdReduction);
            forageHungerThreshold = unit(forageHungerThreshold);
            restThreshold = unit(restThreshold);
            minimumFoodForForage = unit(minimumFoodForForage);
            localTravelRadius = Math.max(4, Math.min(32, localTravelRadius));
            migrationRadius = Math.max(localTravelRadius, Math.min(64, migrationRadius));
            scheduleJitterTicks = Math.max(0, Math.min(4_000, scheduleJitterTicks));
            restDurationTicks = Math.max(20, Math.min(2_400, restDurationTicks));
            sleepDurationTicks = Math.max(40, Math.min(4_800, sleepDurationTicks));
            EnumSet<EcosystemBehaviorState> states = supportedStates == null
                    || supportedStates.isEmpty()
                    ? EnumSet.of(EcosystemBehaviorState.IDLE)
                    : EnumSet.copyOf(supportedStates);
            states.add(EcosystemBehaviorState.IDLE);
            supportedStates = Set.copyOf(states);
        }

        /** Returns whether this species opted into the supplied broad state. */
        public boolean supports(EcosystemBehaviorState state) {
            return supportedStates.contains(state);
        }

        private static Environment compatibilityDefaults(
                Needs needs,
                Drinking drinking,
                Shelter shelter,
                Herd herd,
                Prey prey
        ) {
            EnumSet<EcosystemBehaviorState> states = EnumSet.of(
                    EcosystemBehaviorState.IDLE,
                    EcosystemBehaviorState.FORAGE,
                    EcosystemBehaviorState.TRAVEL,
                    EcosystemBehaviorState.REST,
                    EcosystemBehaviorState.SLEEP
            );
            if (drinking.enabled()) {
                states.add(EcosystemBehaviorState.DRINK);
            }
            if (shelter.enabled()) {
                states.add(EcosystemBehaviorState.SEEK_SHELTER);
            }
            if (prey.enabled()) {
                states.add(EcosystemBehaviorState.FLEE);
            }
            if (herd.enabled()) {
                states.add(EcosystemBehaviorState.MIGRATE);
            }
            return new Environment(
                    needs.nocturnal() ? ActivityTime.NOCTURNAL : ActivityTime.DIURNAL,
                    -5.0,
                    needs.hotTemperatureCelsius(),
                    0.12,
                    0.40,
                    0.62,
                    0.16,
                    12,
                    28,
                    900,
                    120,
                    240,
                    states
            );
        }

        private static double unit(double value) {
            return Math.max(0.0, Math.min(1.0, finite(value, 0.0)));
        }

        private static double finite(double value, double fallback) {
            return Double.isFinite(value) ? value : fallback;
        }
    }
}
