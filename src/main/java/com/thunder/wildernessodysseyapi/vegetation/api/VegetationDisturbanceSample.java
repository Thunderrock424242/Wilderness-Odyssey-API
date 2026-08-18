package com.thunder.wildernessodysseyapi.vegetation.api;

/** Strongest active regional plant disturbance at one position. */
public record VegetationDisturbanceSample(
        PlantDisturbanceType type,
        double intensity,
        boolean blockDamageAllowed,
        long expiresAt
) {

    /** Shared result for a region with no active external plant pressure. */
    public static final VegetationDisturbanceSample NONE = new VegetationDisturbanceSample(
            PlantDisturbanceType.WIND, 0.0, false, 0L
    );

    public VegetationDisturbanceSample {
        type = type == null ? PlantDisturbanceType.WIND : type;
        double finite = Double.isFinite(intensity) ? intensity : 0.0;
        intensity = Math.max(0.0, Math.min(1.0, finite));
        expiresAt = Math.max(0L, expiresAt);
    }

    /** Returns whether any meaningful regional pressure remains. */
    public boolean active() {
        return intensity > 0.001;
    }
}
