package com.thunder.wildernessodysseyapi.ecosystem.behavior;

import com.thunder.wildernessodysseyapi.ecosystem.api.ShelterPreference;
import com.thunder.wildernessodysseyapi.ecosystem.api.StormReaction;
import com.thunder.wildernessodysseyapi.ecosystem.api.StormSensitivity;
import com.thunder.wildernessodysseyapi.weather.api.WeatherThreat;
import com.thunder.wildernessodysseyapi.weather.api.WeatherThreatForecast;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemStage;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StormReactionPolicyTest {

    @Test
    void lightRainNeverCausesDramaticBehavior() {
        assertEquals(StormReaction.NORMAL, StormReactionPolicy.decide(
                forecast(WeatherThreat.LIGHT_RAIN, 0.90, 300.0, 600L),
                StormSensitivity.BIRD
        ));
    }

    @Test
    void strongThunderstormFiveMinutesAwayRaisesHerdAlertness() {
        assertEquals(StormReaction.ALERT, StormReactionPolicy.decide(
                forecast(WeatherThreat.THUNDERSTORM, 0.68, 900.0, 6_000L),
                StormSensitivity.HERD
        ));
    }

    @Test
    void severeStormTwoMinutesAwayTriggersShelter() {
        assertEquals(StormReaction.SEEK_SHELTER, StormReactionPolicy.decide(
                forecast(WeatherThreat.SEVERE_STORM, 0.82, 500.0, 2_400L),
                StormSensitivity.HERD
        ));
    }

    @Test
    void ordinaryRainVariesBySpeciesWithoutTriggeringShelter() {
        WeatherThreatForecast rain = forecast(WeatherThreat.RAIN, 0.45, 450.0, 1_800L);

        assertEquals(StormReaction.NORMAL,
                StormReactionPolicy.decide(rain, StormSensitivity.GENERIC));
        assertEquals(StormReaction.ALERT,
                StormReactionPolicy.decide(rain, StormSensitivity.BIRD));
    }

    @Test
    void aquaticSpeciesAlertsButDoesNotSearchForLandShelter() {
        assertEquals(StormReaction.ALERT, StormReactionPolicy.decide(
                forecast(WeatherThreat.EXTREME_WEATHER, 0.90, 500.0, 1_200L),
                StormSensitivity.AQUATIC
        ));
    }

    @Test
    void distanceAndMinimumIntensityAreBothRespected() {
        StormSensitivity cautious = new StormSensitivity(
                600, 0.75, ShelterPreference.SOLID_OVERHEAD, 0.9);

        assertEquals(StormReaction.NORMAL, StormReactionPolicy.decide(
                forecast(WeatherThreat.SEVERE_STORM, 0.90, 700.0, 1_200L), cautious));
        assertEquals(StormReaction.NORMAL, StormReactionPolicy.decide(
                forecast(WeatherThreat.SEVERE_STORM, 0.70, 500.0, 1_200L), cautious));
    }

    private static WeatherThreatForecast forecast(
            WeatherThreat threat,
            double intensity,
            double distance,
            long etaTicks
    ) {
        return new WeatherThreatForecast(
                threat,
                intensity,
                distance,
                etaTicks,
                0.85,
                1L,
                WeatherSystemType.STORM,
                WeatherSystemStage.MATURE
        );
    }
}
