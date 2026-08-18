package com.thunder.wildernessodysseyapi.environment.api;

/**
 * Normalized cross-system conclusions derived from authoritative environment state.
 *
 * <p>These values are advice for consumers, not a second weather, water, plant,
 * or wildlife simulation. Each component is bounded to {@code [0, 1]}.</p>
 */
public record EnvironmentInfluence(
        double waterAvailability,
        double habitatProductivity,
        double shelterPressure,
        double migrationPressure,
        double wildlifeActivity,
        double aquaticActivity,
        double vegetationStress,
        double overallHazard
) {

    /** Neutral temperate conditions used when no regional owner is available. */
    public static final EnvironmentInfluence NEUTRAL = new EnvironmentInfluence(
            0.55, 0.65, 0.0, 0.0, 1.0, 0.5, 0.0, 0.0
    );
    /** Fully inactive state used by deliberately empty dimensions. */
    public static final EnvironmentInfluence INERT = new EnvironmentInfluence(
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
    );

    /** Clamps integration-provided values before they reach gameplay or networking. */
    public EnvironmentInfluence {
        waterAvailability = unit(waterAvailability);
        habitatProductivity = unit(habitatProductivity);
        shelterPressure = unit(shelterPressure);
        migrationPressure = unit(migrationPressure);
        wildlifeActivity = unit(wildlifeActivity);
        aquaticActivity = unit(aquaticActivity);
        vegetationStress = unit(vegetationStress);
        overallHazard = unit(overallHazard);
    }

    private static double unit(double value) {
        double finite = Double.isFinite(value) ? value : 0.0;
        return Math.max(0.0, Math.min(1.0, finite));
    }
}
