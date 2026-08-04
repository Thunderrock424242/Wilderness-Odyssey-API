package com.thunder.wildernessodysseyapi.ecosystem.behavior;

import com.thunder.wildernessodysseyapi.ecosystem.api.EnvironmentalContext;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies localized rain, thunder, and wind independently trigger shelter policy. */
class WeatherShelterPolicyTest {

    private static final SpeciesBehaviorProfile.Shelter SHELTER = new SpeciesBehaviorProfile.Shelter(
            true, 20, 0.5, 0.35, 0.7, 80, 240, 1.0);

    @Test
    void strongLocalizedRainOrWindTriggersShelterWithoutGlobalWeatherFlags() {
        WeatherSample rain = new WeatherSample(
                15.0, 0.9, 1.0, WindVector.ZERO, 0.8, 0.2, 0.1, 0.6, PrecipitationType.RAIN);
        WeatherSample wind = new WeatherSample(
                15.0, 0.4, 1.0, new WindVector(0.8, 0.0), 0.1, 0.1, 0.0, 0.0, PrecipitationType.NONE);

        assertTrue(DefaultEcosystemBehaviorController.hazardousWeather(context(rain), SHELTER));
        assertTrue(DefaultEcosystemBehaviorController.hazardousWeather(context(wind), SHELTER));
    }

    @Test
    void mildClearSampleDoesNotTriggerShelter() {
        assertFalse(DefaultEcosystemBehaviorController.hazardousWeather(
                context(WeatherSample.CLEAR), SHELTER));
    }

    private static EnvironmentalContext context(WeatherSample weather) {
        return new EnvironmentalContext(
                null,
                null,
                null,
                0L,
                0L,
                ResourceLocation.withDefaultNamespace("plains"),
                weather,
                true,
                0.0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }
}
