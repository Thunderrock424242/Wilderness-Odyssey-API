package com.thunder.wildernessodysseyapi.weather.simulation;

/** Single conservative owner of vapor/cloud phase changes and precipitation sedimentation. */
public final class AtmosphericMoistureBudget {
    private AtmosphericMoistureBudget() { }

    /**
     * Condensation and cloud evaporation transfer the identical kg/m2 between reservoirs.
     * Freezing changes phase only. Sedimentation is bounded by available condensate.
     */
    public static Result advance(double vapor, double liquid, double ice, double temperature,
            double cloudTemperature, double lift, double instability, double seconds, SimulationSettings settings) {
        double capacity = AtmosphericThermodynamics.saturationColumnWater(temperature);
        // Subgrid humidity variability permits fractional cloud below cell-mean saturation.
        double threshold = capacity * settings.cloudFormationThreshold();
        double condensation = Math.max(0.0, vapor - threshold)
                * AtmosphericUnits.response(seconds, 12.0 / (1.0 + Math.max(0.0, lift) * 0.2));
        vapor -= condensation;
        liquid += condensation;
        double evaporation = Math.min(liquid + ice, Math.max(0.0, threshold - vapor))
                * AtmosphericUnits.response(seconds, 90.0);
        double cloud = liquid + ice;
        if (cloud > 0.0) {
            double remaining = (cloud - evaporation) / cloud;
            liquid *= remaining;
            ice *= remaining;
        }
        vapor += evaporation;
        double iceTarget = (liquid + ice) * AtmosphericUnits.unit(-cloudTemperature / 20.0);
        double frozen = (iceTarget - ice) * AtmosphericUnits.response(seconds, 30.0);
        ice += frozen;
        liquid -= frozen;

        cloud = liquid + ice;
        double excessCloud = Math.max(0.0, cloud
                - settings.precipitationThreshold() * AtmosphericUnits.CLOUD_SCALE_KG_PER_SQUARE_METRE);
        double fallTime = 120.0 / (1.0 + AtmosphericUnits.unit(instability / 3000.0) * 2.0);
        double precipitation = Math.min(excessCloud * AtmosphericUnits.response(seconds, fallTime),
                settings.maximumPrecipitationIntensity() * AtmosphericUnits.RAIN_SCALE_MM_PER_HOUR * seconds / 3600.0);
        if (cloud > 0.0) {
            double retained = (cloud - precipitation) / cloud;
            liquid *= retained;
            ice *= retained;
        }
        return new Result(vapor, liquid, ice, condensation, evaporation, precipitation,
                (condensation - evaporation) * 2.5 + frozen * 0.334);
    }

    /** Mass amounts in kg/m2; latent temperature change assumes an effective 1000 kg/m2 air slab. */
    public record Result(double vapor, double liquid, double ice, double condensation,
                         double cloudEvaporation, double precipitation, double latentTemperatureChange) { }
}
