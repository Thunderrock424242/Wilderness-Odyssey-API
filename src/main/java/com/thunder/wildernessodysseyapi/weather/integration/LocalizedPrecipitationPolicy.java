package com.thunder.wildernessodysseyapi.weather.integration;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationIntensity;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;

/**
 * Defines the intensity boundary where atmospheric precipitation becomes a
 * physical rain or snow interaction.
 *
 * <p>The policy is world-independent so rendering and server adapters can use
 * one threshold without consulting Minecraft state during unit tests.</p>
 */
public final class LocalizedPrecipitationPolicy {

    private LocalizedPrecipitationPolicy() {
    }

    /** Returns whether the sample can wet terrain or entities. */
    public static boolean hasPrecipitation(WeatherSample sample) {
        return sample != null
                && sample.precipitationType() != PrecipitationType.NONE
                && PrecipitationIntensity.isFunctional(sample.precipitationIntensity());
    }

    /** Returns whether the sample supplies functional rain. */
    public static boolean isRain(WeatherSample sample) {
        return hasPrecipitation(sample)
                && (sample.precipitationType() == PrecipitationType.RAIN
                || sample.precipitationType() == PrecipitationType.HAIL);
    }

    /** Returns whether the sample supplies functional snow. */
    public static boolean isSnow(WeatherSample sample) {
        return hasPrecipitation(sample) && sample.precipitationType() == PrecipitationType.SNOW;
    }
}
