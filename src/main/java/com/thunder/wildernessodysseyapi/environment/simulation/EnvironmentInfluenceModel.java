package com.thunder.wildernessodysseyapi.environment.simulation;

import com.thunder.wildernessodysseyapi.environment.api.EnvironmentInfluence;
import com.thunder.wildernessodysseyapi.meteor.api.MeteorSiteSnapshot;
import com.thunder.wildernessodysseyapi.riftfall.RiftfallStage;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationClimateState;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationDisturbanceSample;
import com.thunder.wildernessodysseyapi.weather.api.SeasonalClimateState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WeatherThreatForecast;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;

/** Pure cross-system model used by server snapshots and focused tests. */
public final class EnvironmentInfluenceModel {

    private EnvironmentInfluenceModel() {
    }

    /** Derives bounded consumer-facing conclusions without mutating any owner. */
    public static EnvironmentInfluence evaluate(
            WeatherSample weather,
            WeatherThreatForecast forecast,
            SeasonalClimateState season,
            WatershedConditions watershed,
            TideSystem.TideSample tide,
            VegetationClimateState vegetation,
            VegetationDisturbanceSample vegetationDisturbance,
            MeteorSiteSnapshot meteor,
            RiftfallStage riftfallStage
    ) {
        WeatherSample air = weather == null ? WeatherSample.CLEAR : weather;
        WeatherThreatForecast outlook = forecast == null ? WeatherThreatForecast.NONE : forecast;
        SeasonalClimateState calendar = season == null ? SeasonalClimateState.NONE : season;
        WatershedConditions water = watershed == null ? WatershedConditions.NONE : watershed;
        VegetationClimateState plants = vegetation == null ? VegetationClimateState.DEFAULT : vegetation;
        VegetationDisturbanceSample plantEvent = vegetationDisturbance == null
                ? VegetationDisturbanceSample.NONE : vegetationDisturbance;
        MeteorSiteSnapshot site = meteor == null ? MeteorSiteSnapshot.NONE : meteor;
        RiftfallStage rift = riftfallStage == null ? RiftfallStage.CLEAR : riftfallStage;

        double waterAvailability = Math.max(
                water.hasSurfaceWater() ? 0.82 : 0.0,
                Math.max(water.soilSaturation(), water.normalizedWaterTable())
        );
        double seasonalDormancy = unit(calendar.snowSeasonFactor() * 0.72
                + calendar.fireSeasonFactor() * 0.35);
        double habitatProductivity = unit(
                plants.moisture() * 0.44
                        + (1.0 - plants.droughtLevel()) * 0.34
                        + (1.0 - seasonalDormancy) * 0.16
                        + waterAvailability * 0.18
                        - plantEvent.intensity() * 0.24
                        - site.radiation() * 0.42
        );

        double weatherHazard = Math.max(
                Math.max(air.stormEnergy(), air.thunderIntensity()),
                Math.max(unit(air.wind().magnitude() / 1.15), air.precipitationIntensity() * 0.72)
        );
        double floodHazard = water.flooding() ? 1.0 : water.floodRisk();
        double riftHazard = switch (rift) {
            case CLEAR -> 0.0;
            case WARNING -> 0.35;
            case ACTIVE -> 0.78;
            case METEOR_SURGE -> 1.0;
            case ENDING -> 0.42;
        };
        double overallHazard = Math.max(
                Math.max(weatherHazard, floodHazard),
                Math.max(Math.max(site.radiation(), riftHazard), plantEvent.intensity())
        );
        double shelterPressure = Math.max(
                Math.max(weatherHazard, floodHazard * 0.75),
                riftHazard
        );
        double migrationPressure = Math.max(
                Math.max(plants.droughtLevel(), floodHazard),
                Math.max(Math.max(site.radiation(), riftHazard), seasonalDormancy * 0.72)
        );

        double forecastActivity = outlook.ambientWildlifeActivityScale();
        double wildlifeActivity = unit(
                forecastActivity
                        * (1.0 - overallHazard * 0.58)
                        * (0.72 + habitatProductivity * 0.28)
        );
        double tideActivity = 0.5;
        if (water.waterFeature() == WatershedConditions.WaterFeature.COASTAL && tide != null) {
            double rate = unit(Math.abs(tide.rate()) / 0.01);
            tideActivity = unit(0.38 + rate * 0.42 + tide.springFactor() * 0.20);
        }
        double vegetationStress = Math.max(
                Math.max(plants.droughtLevel(), plantEvent.intensity()),
                Math.max(site.radiation(), Math.max(riftHazard, floodHazard * 0.68))
        );
        return new EnvironmentInfluence(
                waterAvailability,
                habitatProductivity,
                shelterPressure,
                migrationPressure,
                wildlifeActivity,
                tideActivity,
                vegetationStress,
                overallHazard
        );
    }

    private static double unit(double value) {
        double finite = Double.isFinite(value) ? value : 0.0;
        return Math.max(0.0, Math.min(1.0, finite));
    }
}
