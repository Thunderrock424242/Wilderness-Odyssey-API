package com.thunder.wildernessodysseyapi.weather.integration;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies conversion from vanilla command flags to localized weather. */
class VanillaWeatherCommandAdapterTest {

    @Test
    void resolvesVanillaWeatherFlags() {
        assertEquals(
                VanillaWeatherCommandAdapter.State.CLEAR,
                VanillaWeatherCommandAdapter.fromParameters(false, false)
        );
        assertEquals(
                VanillaWeatherCommandAdapter.State.RAIN,
                VanillaWeatherCommandAdapter.fromParameters(true, false)
        );
        assertEquals(
                VanillaWeatherCommandAdapter.State.THUNDER,
                VanillaWeatherCommandAdapter.fromParameters(true, true)
        );
    }

    @Test
    void clearRemovesPrecipitationAndStormEnergy() {
        WeatherSample clear = VanillaWeatherCommandAdapter.apply(
                sample(18.0),
                VanillaWeatherCommandAdapter.State.CLEAR
        );

        assertFalse(clear.hasPrecipitation());
        assertEquals(0.0, clear.cloudWater());
        assertEquals(0.0, clear.stormEnergy());
    }

    @Test
    void rainUsesAtmosphericTemperatureForRainOrSnow() {
        WeatherSample warm = VanillaWeatherCommandAdapter.apply(
                sample(18.0, 0.95),
                VanillaWeatherCommandAdapter.State.RAIN
        );
        WeatherSample cold = VanillaWeatherCommandAdapter.apply(
                sample(-4.0),
                VanillaWeatherCommandAdapter.State.RAIN
        );

        assertEquals(PrecipitationType.RAIN, warm.precipitationType());
        assertEquals(PrecipitationType.SNOW, cold.precipitationType());
        assertEquals(18.0, warm.temperature());
        assertEquals(-4.0, cold.temperature());
        assertFalse(warm.lightningEligible());
        assertEquals(0.48, warm.stormEnergy());
    }

    @Test
    void coldOrdinaryBiomeCommandRainDoesNotBecomeSnowOutsideWinter() {
        WeatherSample cold = VanillaWeatherCommandAdapter.apply(
                sample(-4.0),
                VanillaWeatherCommandAdapter.State.RAIN,
                false
        );

        assertEquals(PrecipitationType.RAIN, cold.precipitationType());
    }

    @Test
    void thunderProducesLightningEligibleLocalizedWeather() {
        WeatherSample thunder = VanillaWeatherCommandAdapter.apply(
                sample(18.0),
                VanillaWeatherCommandAdapter.State.THUNDER
        );

        assertTrue(thunder.isRaining());
        assertTrue(thunder.lightningEligible());
        assertEquals(1.0, thunder.precipitationIntensity());
    }

    private static WeatherSample sample(double temperature) {
        return sample(temperature, 0.1);
    }

    private static WeatherSample sample(double temperature, double stormEnergy) {
        return new WeatherSample(
                temperature,
                0.4,
                1.1,
                new WindVector(0.1, -0.1),
                0.1,
                0.2,
                stormEnergy,
                0.0,
                PrecipitationType.NONE
        );
    }
}
