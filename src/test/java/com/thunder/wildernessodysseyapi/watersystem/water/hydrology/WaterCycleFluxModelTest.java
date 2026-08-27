package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterBody;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.SurfaceWeatherState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies conservative water-cycle direction and body weighting. */
class WaterCycleFluxModelTest {

    @Test
    void rainCreditsFiniteWaterButNeverRaisesLargeOceans() {
        WeatherSample rain = sample(
                16.0, 0.90, new WindVector(0.4, 0.1),
                0.8, PrecipitationType.RAIN, SurfaceWeatherState.DRY
        );

        double pond = WaterCycleFluxModel.fluxUnits(
                rain, WaterBody.Kind.LARGE_POND, 48.0, 18.0);
        double ocean = WaterCycleFluxModel.fluxUnits(
                rain, WaterBody.Kind.LARGE_OCEAN, 48.0, 18.0);
        double coast = WaterCycleFluxModel.fluxUnits(
                rain, WaterBody.Kind.LARGE_COAST, 48.0, 18.0);
        double lake = WaterCycleFluxModel.fluxUnits(
                rain, WaterBody.Kind.LARGE_LAKE, 48.0, 18.0);

        assertTrue(pond > 0.0);
        assertEquals(0.0, ocean);
        assertEquals(0.0, coast);
        assertEquals(pond, lake, 1.0e-9);
    }

    @Test
    void hotDryWindDebitsPondsAndRiversMoreConservatively() {
        WeatherSample drought = sample(
                38.0, 0.12, new WindVector(0.9, 0.4),
                0.0, PrecipitationType.NONE, SurfaceWeatherState.DRY
        );

        double pond = WaterCycleFluxModel.fluxUnits(
                drought, WaterBody.Kind.LARGE_POND, 48.0, 18.0);
        double river = WaterCycleFluxModel.fluxUnits(
                drought, WaterBody.Kind.LARGE_RIVER, 48.0, 18.0);

        assertTrue(pond < 0.0);
        assertTrue(river < 0.0);
        assertTrue(Math.abs(river) < Math.abs(pond));
    }

    @Test
    void frozenSnowWaitsForThawBeforeBecomingLiquidInput() {
        SurfaceWeatherState snowpack = new SurfaceWeatherState(0.2, 0.0, 1.0, 1.0);
        WeatherSample frozenSnow = sample(
                -8.0, 0.95, WindVector.ZERO,
                1.0, PrecipitationType.SNOW, snowpack
        );
        WeatherSample thaw = sample(
                8.0, 1.0, WindVector.ZERO,
                0.0, PrecipitationType.NONE,
                new SurfaceWeatherState(0.4, 0.0, 1.0, 0.0)
        );

        assertEquals(0.0, WaterCycleFluxModel.fluxUnits(
                frozenSnow, WaterBody.Kind.LARGE_POND, 48.0, 18.0), 1.0e-9);
        assertTrue(WaterCycleFluxModel.fluxUnits(
                thaw, WaterBody.Kind.LARGE_POND, 48.0, 18.0) > 0.0);
    }

    private static WeatherSample sample(
            double temperature,
            double humidity,
            WindVector wind,
            double precipitation,
            PrecipitationType type,
            SurfaceWeatherState surface
    ) {
        return new WeatherSample(
                temperature,
                humidity,
                1.0,
                wind,
                precipitation,
                0.4,
                precipitation,
                precipitation,
                type,
                0.0,
                precipitation,
                wind,
                surface
        );
    }
}
