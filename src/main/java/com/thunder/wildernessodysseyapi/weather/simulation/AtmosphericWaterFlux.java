package com.thunder.wildernessodysseyapi.weather.simulation;

/**
 * Integrated cell-area water transfers for a completed step, all in mm liquid equivalent.
 * Internal phase transfers cancel; boundary vapor and face transport are signed.
 * Hydrology may consume precipitation only after the associated cell revision commits.
 */
public record AtmosphericWaterFlux(double dtSeconds, double evaporationMm, double condensationMm,
        double cloudEvaporationMm, double rainfallMm, double snowfallMm, double sleetMm,
        double freezingRainMm, double hailMm, double boundaryVaporMm, double advectedWaterMm) {
    public static final AtmosphericWaterFlux NONE = new AtmosphericWaterFlux(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public AtmosphericWaterFlux {
        if (!Double.isFinite(dtSeconds) || dtSeconds < 0 || !Double.isFinite(evaporationMm) || evaporationMm < 0
                || !Double.isFinite(condensationMm) || condensationMm < 0
                || !Double.isFinite(cloudEvaporationMm) || cloudEvaporationMm < 0
                || !Double.isFinite(rainfallMm) || rainfallMm < 0 || !Double.isFinite(snowfallMm) || snowfallMm < 0
                || !Double.isFinite(sleetMm) || sleetMm < 0 || !Double.isFinite(freezingRainMm) || freezingRainMm < 0
                || !Double.isFinite(hailMm) || hailMm < 0 || !Double.isFinite(boundaryVaporMm)
                || !Double.isFinite(advectedWaterMm)) {
            throw new IllegalArgumentException("Atmospheric water flux must be finite with nonnegative transfers");
        }
    }

    public double precipitationMm() {
        return rainfallMm + snowfallMm + sleetMm + freezingRainMm + hailMm;
    }

    public AtmosphericWaterFlux plus(AtmosphericWaterFlux other) {
        return new AtmosphericWaterFlux(dtSeconds + other.dtSeconds, evaporationMm + other.evaporationMm,
                condensationMm + other.condensationMm, cloudEvaporationMm + other.cloudEvaporationMm,
                rainfallMm + other.rainfallMm, snowfallMm + other.snowfallMm, sleetMm + other.sleetMm,
                freezingRainMm + other.freezingRainMm, hailMm + other.hailMm,
                boundaryVaporMm + other.boundaryVaporMm, advectedWaterMm + other.advectedWaterMm);
    }

    /** Signed expected change of stored vapor plus cloud mass. */
    public double atmosphericChangeMm() {
        return evaporationMm + boundaryVaporMm + advectedWaterMm - precipitationMm();
    }
}
