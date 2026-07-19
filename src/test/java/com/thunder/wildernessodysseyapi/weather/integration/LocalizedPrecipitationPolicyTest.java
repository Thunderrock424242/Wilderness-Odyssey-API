package com.thunder.wildernessodysseyapi.weather.integration;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationIntensity;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the shared boundary between visual traces and physical precipitation. */
class LocalizedPrecipitationPolicyTest {

    @Test
    void rejectsClearAndSubThresholdTraces() {
        assertFalse(LocalizedPrecipitationPolicy.hasPrecipitation(WeatherSample.CLEAR));
        assertFalse(LocalizedPrecipitationPolicy.hasPrecipitation(sample(
                PrecipitationType.RAIN,
                0.020D
        )));
    }

    @Test
    void acceptsRainAndSnowInFirstFunctionalWireBucket() {
        WeatherSample rain = sample(
                PrecipitationType.RAIN,
                PrecipitationIntensity.FIRST_FUNCTIONAL_DEQUANTIZED_VALUE
        );
        WeatherSample snow = sample(
                PrecipitationType.SNOW,
                PrecipitationIntensity.FIRST_FUNCTIONAL_DEQUANTIZED_VALUE
        );

        assertTrue(LocalizedPrecipitationPolicy.isRain(rain));
        assertFalse(LocalizedPrecipitationPolicy.isSnow(rain));
        assertTrue(LocalizedPrecipitationPolicy.isSnow(snow));
        assertFalse(LocalizedPrecipitationPolicy.isRain(snow));
        assertTrue(LocalizedPrecipitationPolicy.isRain(sample(PrecipitationType.RAIN, 0.025D)));
    }

    private static WeatherSample sample(PrecipitationType type, double intensity) {
        return new WeatherSample(
                type == PrecipitationType.SNOW ? -4.0D : 12.0D,
                0.9D,
                0.95D,
                WindVector.ZERO,
                0.8D,
                0.5D,
                0.5D,
                intensity,
                type
        );
    }
}
