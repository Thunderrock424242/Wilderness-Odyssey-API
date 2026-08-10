package com.thunder.wildernessodysseyapi.weather.severe;

import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemType;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemStage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SevereWeatherWindPolicyTest {

    @Test
    void establishedTornadoesAndCyclonesApplyPhysicalWind() {
        assertTrue(SevereWeatherWindPolicy.canApplyEntityWind(
                WeatherSystemType.TORNADO,
                WeatherSystemStage.MATURE
        ));
        assertTrue(SevereWeatherWindPolicy.canApplyEntityWind(
                WeatherSystemType.CYCLONE,
                WeatherSystemStage.MATURE
        ));
        assertTrue(SevereWeatherWindPolicy.canApplyEntityWind(
                WeatherSystemType.TORNADO,
                WeatherSystemStage.WEAKENING
        ));
    }

    @Test
    void ordinaryAndFormingStormsDoNotApplyPhysicalWind() {
        assertFalse(SevereWeatherWindPolicy.canApplyEntityWind(
                WeatherSystemType.STORM,
                WeatherSystemStage.MATURE
        ));
        assertFalse(SevereWeatherWindPolicy.canApplyEntityWind(
                WeatherSystemType.COLD_FRONT,
                WeatherSystemStage.MATURE
        ));
        assertFalse(SevereWeatherWindPolicy.canApplyEntityWind(
                WeatherSystemType.TORNADO,
                WeatherSystemStage.FORMING
        ));
        assertFalse(SevereWeatherWindPolicy.canApplyEntityWind(null, WeatherSystemStage.MATURE));
        assertFalse(SevereWeatherWindPolicy.canApplyEntityWind(WeatherSystemType.TORNADO, null));
    }
}
