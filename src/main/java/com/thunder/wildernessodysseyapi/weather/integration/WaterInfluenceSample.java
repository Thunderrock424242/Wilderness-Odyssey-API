package com.thunder.wildernessodysseyapi.weather.integration;

import net.minecraft.util.Mth;

/**
 * Immutable, normalized surface-water coverage captured for one atmosphere cell.
 *
 * <p>Every coverage value is in {@code [0, 1]} and is calculated only from
 * loaded chunks. {@code loadedProbeFraction} lets the simulation distinguish
 * unknown terrain from a genuinely dry sample without retaining world objects.</p>
 *
 * @param surfaceWaterCoverage all exposed water, regardless of ownership
 * @param oceanCoverage exposed water in ocean biomes
 * @param riverCoverage exposed water in river biomes
 * @param inlandWaterCoverage exposed water outside ocean and river biomes
 * @param vanillaTaggedCoverage exposed tagged water not owned by Wilderness water
 * @param loadedProbeFraction fraction of the fixed probe lattice that was loaded
 */
public record WaterInfluenceSample(
        float surfaceWaterCoverage,
        float oceanCoverage,
        float riverCoverage,
        float inlandWaterCoverage,
        float vanillaTaggedCoverage,
        float loadedProbeFraction
) {

    /** Shared result for a cell whose sampled chunks are all unavailable. */
    public static final WaterInfluenceSample UNKNOWN = new WaterInfluenceSample(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);

    public WaterInfluenceSample {
        surfaceWaterCoverage = clamp(surfaceWaterCoverage);
        oceanCoverage = clamp(oceanCoverage);
        riverCoverage = clamp(riverCoverage);
        inlandWaterCoverage = clamp(inlandWaterCoverage);
        vanillaTaggedCoverage = clamp(vanillaTaggedCoverage);
        loadedProbeFraction = clamp(loadedProbeFraction);
    }

    /**
     * Returns a gameplay-scale evaporation potential in {@code [0, 1]}.
     *
     * <p>Ocean exposure receives the strongest weight, while rivers and inland
     * water still feed humidity. The base surface term keeps modded tagged
     * water useful even when its biome has no standard water classification.</p>
     */
    public float moisturePotential() {
        float loadedMoisture = surfaceWaterCoverage * 0.55f
                + oceanCoverage * 0.35f
                + riverCoverage * 0.18f
                + inlandWaterCoverage * 0.10f;
        // A partially loaded lattice is incomplete evidence, not a fully wet
        // cell. Weighting by coverage confidence prevents one loaded water
        // column from supplying an entire 256-block cell's evaporation.
        return clamp(loadedMoisture * loadedProbeFraction);
    }

    private static float clamp(float value) {
        return Mth.clamp(Float.isFinite(value) ? value : 0.0f, 0.0f, 1.0f);
    }
}
