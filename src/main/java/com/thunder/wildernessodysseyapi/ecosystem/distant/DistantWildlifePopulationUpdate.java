package com.thunder.wildernessodysseyapi.ecosystem.distant;

import com.thunder.wildernessodysseyapi.ecosystem.simulation.AbstractEcosystemModel;

import java.util.Objects;

/**
 * Immutable worker result for one persisted distant-wildlife group.
 *
 * <p>Expected fields provide the minimum optimistic-lock state needed by
 * {@link DistantWildlifeSavedData}. Group motion is intentionally absent so a
 * valid population result can be applied without overwriting newer movement.</p>
 */
public record DistantWildlifePopulationUpdate(
        long groupId,
        int expectedPopulation,
        double expectedRemainder,
        long expectedReferenceGameTime,
        int population,
        double remainder,
        long referenceGameTime,
        AbstractEcosystemModel.Environment environment
) {
    public DistantWildlifePopulationUpdate {
        if (groupId <= 0L) {
            throw new IllegalArgumentException("Population update group ID must be positive");
        }
        requirePopulation(expectedPopulation, "expected");
        requirePopulation(population, "result");
        requireRemainder(expectedRemainder, "expected");
        requireRemainder(remainder, "result");
        expectedReferenceGameTime = Math.max(0L, expectedReferenceGameTime);
        referenceGameTime = Math.max(expectedReferenceGameTime, referenceGameTime);
        environment = Objects.requireNonNull(environment, "Population environment is required");
    }

    /** Creates the minimal owner mutation between two states of the same group. */
    public static DistantWildlifePopulationUpdate between(
            DistantWildlifeGroup expected,
            DistantWildlifeGroup result
    ) {
        Objects.requireNonNull(expected, "Expected group is required");
        Objects.requireNonNull(result, "Result group is required");
        if (expected.id() != result.id()) {
            throw new IllegalArgumentException("Population update groups must have the same ID");
        }
        return new DistantWildlifePopulationUpdate(
                expected.id(),
                expected.populationEstimate(),
                expected.populationRemainder(),
                expected.populationReferenceGameTime(),
                result.populationEstimate(),
                result.populationRemainder(),
                result.populationReferenceGameTime(),
                new AbstractEcosystemModel.Environment(
                        result.foodAvailability(),
                        result.waterAvailability(),
                        result.foodPressure(),
                        result.disturbance(),
                        result.weatherImpact()
                )
        );
    }

    /** Returns whether the owner still has the population state used by the worker. */
    public boolean matches(DistantWildlifeGroup group) {
        return group != null
                && group.id() == groupId
                && group.populationEstimate() == expectedPopulation
                && Double.compare(group.populationRemainder(), expectedRemainder) == 0
                && group.populationReferenceGameTime() == expectedReferenceGameTime;
    }

    private static void requirePopulation(int value, String label) {
        if (value <= 0 || value > DistantWildlifeGroup.MAXIMUM_GROUP_POPULATION) {
            throw new IllegalArgumentException("Invalid " + label + " population: " + value);
        }
    }

    private static void requireRemainder(double value, String label) {
        if (!Double.isFinite(value) || value < -0.5 || value > 0.5) {
            throw new IllegalArgumentException("Invalid " + label + " population remainder: " + value);
        }
    }
}
