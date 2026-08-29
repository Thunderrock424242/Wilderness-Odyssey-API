package com.thunder.wildernessodysseyapi.rendering;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.SurfaceWeatherState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentStateTest {

    @Test
    void composesWeatherAndWindWithoutOwningSimulation() {
        WeatherSample weather = new WeatherSample(
                -4.0,
                0.88,
                0.96,
                new WindVector(0.8, 0.2),
                0.82,
                0.76,
                0.70,
                0.65,
                PrecipitationType.SNOW,
                0.25,
                0.75,
                new WindVector(0.9, 0.3),
                new SurfaceWeatherState(0.72, 0.30, 0.80, 0.55)
        );
        WindSample wind = new WindSample(
                new Vec3(3.0, 0.5, 4.0),
                12.0F,
                3.0F,
                6.0F,
                0.5F,
                0.25F,
                1L,
                new AtmosphereCellKey(2, 3)
        );

        EnvironmentState state = EnvironmentState.from(weather, wind);

        assertEquals(0.0F, state.rainIntensity());
        assertEquals(0.65F, state.snowIntensity());
        assertEquals(15.0F, state.windSpeed());
        assertEquals(0.72F, state.wetness());
        assertEquals(0.55F, state.frozenFraction());
        assertTrue(state.waterTurbulence() > 0.65F);
    }
}
