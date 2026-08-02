package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphericFrontType;
import com.thunder.wildernessodysseyapi.weather.api.CloudType;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherPhenomenon;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;

/**
 * Derives continuous hazard likelihoods from the authoritative atmosphere.
 *
 * <p>The profile is descriptive and side-effect free. Server schedulers decide
 * whether optional severe effects are enabled; clients use the same thresholds
 * for diagnostics and visual presentation.</p>
 */
public final class WeatherHazardModel {

    private WeatherHazardModel() {
    }

    /** Evaluates hazards without typed water context, as used by clients. */
    public static HazardProfile evaluate(WeatherSample sample) {
        return evaluate(sample, AtmosphereEnvironment.TEMPERATE, AtmosphericFrontType.NONE, 0.0);
    }

    /** Evaluates hazards with captured surface-water and front context. */
    public static HazardProfile evaluate(
            WeatherSample sample,
            AtmosphereEnvironment environment,
            AtmosphericFrontType frontType,
            double frontStrength
    ) {
        WeatherSample weather = sample == null ? WeatherSample.CLEAR : sample;
        AtmosphereEnvironment inputs = environment == null ? AtmosphereEnvironment.TEMPERATE : environment;
        AtmosphericFrontType front = frontType == null ? AtmosphericFrontType.NONE : frontType;
        double wind = unit(weather.wind().magnitude());
        double shear = unit(weather.windShear() / 1.25);
        double storm = weather.stormEnergy();
        double lift = unit(Math.max(0.0, weather.verticalMotion()));
        double lowPressure = unit((1.02 - weather.pressure()) / 0.20);
        double frontSupport = unit(frontStrength)
                * (front == AtmosphericFrontType.COLD || front == AtmosphericFrontType.OCCLUDED ? 1.0 : 0.65);

        double denseFog = unit((weather.humidity() - 0.82) / 0.18)
                * unit(1.0 - wind * 0.75)
                * unit(0.45 + weather.cloudWater() * 0.55);
        // Lake-effect attribution requires typed inland-water context. A
        // client-only sample may identify a blizzard, but must not label all
        // windy snow as lake effect merely because water context is absent.
        double lakeEffect = inputs.lakeEffectPotential(
                weather.temperature(), weather.wind().magnitude()
        );
        double oceanStorm = Math.max(
                inputs.oceanStormPotential(weather.temperature(), weather.humidity()) * storm,
                unit((weather.temperature() - 18.0) / 16.0) * lowPressure * storm * wind
        );
        double drought = unit((0.42 - weather.humidity()) / 0.42)
                * unit((weather.temperature() - 20.0) / 22.0)
                * unit((weather.pressure() - 1.0) / 0.18)
                * (1.0 - weather.precipitationIntensity());
        double heatWave = unit((weather.temperature() - 30.0) / 18.0)
                * unit((weather.pressure() - 0.98) / 0.20)
                * unit(1.15 - weather.humidity());
        double hail = weather.precipitationType() == PrecipitationType.HAIL
                ? weather.precipitationIntensity()
                : weather.precipitationIntensity() * storm * weather.instability() * lift;
        double blizzard = weather.precipitationType() == PrecipitationType.SNOW
                ? weather.precipitationIntensity() * unit(wind * 0.85 + shear * 0.35)
                * unit((3.0 - weather.temperature()) / 14.0)
                : 0.0;
        double tornado = weather.cloudType() == CloudType.CUMULONIMBUS
                ? unit(storm * 0.32
                + weather.instability() * 0.20
                + lift * 0.18
                + shear * 0.22
                + frontSupport * 0.18 - 0.58) / 0.42
                : 0.0;
        double cyclone = unit(oceanStorm * 0.38
                + lowPressure * 0.24
                + storm * 0.20
                + wind * 0.18 - 0.62) / 0.38;

        return new HazardProfile(
                denseFog,
                lakeEffect,
                oceanStorm,
                drought,
                heatWave,
                hail,
                blizzard,
                tornado,
                cyclone
        );
    }

    /** Immutable intensity bundle; all values are normalized. */
    public record HazardProfile(
            double denseFog,
            double lakeEffectSnow,
            double oceanStorm,
            double drought,
            double heatWave,
            double hail,
            double blizzard,
            double tornado,
            double cyclone
    ) {
        public HazardProfile {
            denseFog = unit(denseFog);
            lakeEffectSnow = unit(lakeEffectSnow);
            oceanStorm = unit(oceanStorm);
            drought = unit(drought);
            heatWave = unit(heatWave);
            hail = unit(hail);
            blizzard = unit(blizzard);
            tornado = unit(tornado);
            cyclone = unit(cyclone);
        }

        /** Returns the strongest meaningful phenomenon. */
        public WeatherPhenomenon dominant() {
            WeatherPhenomenon result = WeatherPhenomenon.NONE;
            double strongest = 0.18;
            double[] values = {
                    denseFog, lakeEffectSnow, oceanStorm, drought, heatWave,
                    hail, blizzard, tornado, cyclone
            };
            WeatherPhenomenon[] phenomena = {
                    WeatherPhenomenon.DENSE_FOG,
                    WeatherPhenomenon.LAKE_EFFECT_SNOW,
                    WeatherPhenomenon.OCEAN_STORM,
                    WeatherPhenomenon.DROUGHT,
                    WeatherPhenomenon.HEAT_WAVE,
                    WeatherPhenomenon.HAIL,
                    WeatherPhenomenon.BLIZZARD,
                    WeatherPhenomenon.TORNADO,
                    WeatherPhenomenon.CYCLONE
            };
            for (int index = 0; index < values.length; index++) {
                if (values[index] > strongest) {
                    strongest = values[index];
                    result = phenomena[index];
                }
            }
            return result;
        }

        /** Returns the intensity associated with the dominant phenomenon. */
        public double dominantIntensity() {
            return switch (dominant()) {
                case DENSE_FOG -> denseFog;
                case LAKE_EFFECT_SNOW -> lakeEffectSnow;
                case OCEAN_STORM -> oceanStorm;
                case DROUGHT -> drought;
                case HEAT_WAVE -> heatWave;
                case HAIL -> hail;
                case BLIZZARD -> blizzard;
                case TORNADO -> tornado;
                case CYCLONE -> cyclone;
                case NONE -> 0.0;
            };
        }
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }
}
