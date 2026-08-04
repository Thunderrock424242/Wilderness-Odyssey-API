package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.DrainageDirection;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.WaterFeature;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import com.thunder.wildernessodysseyapi.weather.api.SurfaceWeatherState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies long-storm buildup, drought decay, routing, and flood thresholds. */
class WatershedSimulationModelTest {

    private static final WeatherSample HEAVY_RAIN = new WeatherSample(
            18.0,
            0.98,
            0.92,
            new WindVector(0.7, 0.25),
            1.0,
            0.9,
            1.0,
            1.0,
            PrecipitationType.RAIN
    );

    @Test
    void sustainedRainBuildsSaturationDischargeAndFloodRiskGradually() {
        WatershedChunkState state = riverState();
        float firstDischarge = state.conditions().riverDischarge();

        for (int pass = 0; pass < 160; pass++) {
            apply(state, HEAVY_RAIN, true, true, 0.72f);
        }

        WatershedConditions wet = state.conditions();
        assertTrue(wet.soilSaturation() > 0.7f);
        assertTrue(wet.recentRainfall() > 0.5f);
        assertTrue(wet.riverDischarge() > firstDischarge + 0.35f);
        assertTrue(wet.waterLevelOffset() > 0.0f);
        assertTrue(wet.floodRisk() >= wet.floodThreshold());
        assertTrue(wet.flooding());
        assertTrue(wet.sediment() > 0.2f);
        assertTrue(wet.debris() > 0.1f);
    }

    @Test
    void clearWeatherDecaysRainfallAndEndsFloodingBeforeDroughtLowersSurface() {
        WatershedChunkState state = riverState();
        for (int pass = 0; pass < 160; pass++) {
            apply(state, HEAVY_RAIN, true, true, 0.72f);
        }
        float wetRainfall = state.conditions().recentRainfall();

        for (int pass = 0; pass < 520; pass++) {
            apply(state, WeatherSample.CLEAR, true, true, 0.72f);
        }

        WatershedConditions dry = state.conditions();
        assertTrue(dry.recentRainfall() < wetRainfall * 0.2f);
        assertTrue(dry.soilSaturation() < 0.2f);
        assertFalse(dry.flooding());
        assertTrue(dry.waterLevelOffset() < 0.0f);
        assertTrue(dry.clarity() > 0.8f);
    }

    @Test
    void downstreamRunoffTransfersOnlyWhenLoadedDestinationIsAvailable() {
        WatershedConditions backedUp = conditions(0.55f, 0.72f, 0.84f, 0.35f, false);
        WatershedSimulationModel.Result blocked = WatershedSimulationModel.advance(input(
                backedUp, WeatherSample.CLEAR, true, false, 0.8f));
        WatershedSimulationModel.Result routed = WatershedSimulationModel.advance(input(
                backedUp, WeatherSample.CLEAR, true, true, 0.8f));

        assertTrue(blocked.downstreamTransfer() == 0.0f);
        assertTrue(routed.downstreamTransfer() > 0.0f);
        assertTrue(routed.storedRunoff() < blocked.storedRunoff());
    }

    @Test
    void disabledWeatherFallsBackToDecayEvenWhenStormSampleIsWet() {
        WatershedConditions previous = conditions(0.75f, 0.80f, 0.55f, 0.60f, true);
        WatershedSimulationModel.Result disabled = WatershedSimulationModel.advance(
                new WatershedSimulationModel.Input(
                        previous,
                        HEAVY_RAIN,
                        0.0f,
                        0.08f,
                        0.04f,
                        0.45f,
                        0.72f,
                        false,
                        true,
                        true,
                        true
                )
        );

        assertTrue(disabled.recentRainfall() < previous.recentRainfall());
        assertTrue(disabled.soilSaturation() < previous.soilSaturation());
    }

    @Test
    void warmStoredSnowBecomesDelayedRunoffWithoutLiquidRain() {
        WeatherSample thaw = new WeatherSample(
                8.0, 0.55, 1.0, WindVector.ZERO,
                0.0, 0.1, 0.0, 0.0, PrecipitationType.NONE,
                0.0, 0.0, WindVector.ZERO,
                new SurfaceWeatherState(0.2, 0.0, 0.9, 0.0)
        );
        WatershedSimulationModel.Result result = WatershedSimulationModel.advance(
                new WatershedSimulationModel.Input(
                        riverState().conditions(), thaw, 0.0f, 0.08f, 0.035f,
                        0.45f, 0.72f, true, true, true, true, 0.20f
                )
        );

        assertTrue(result.recentSnowmelt() > 0.0f);
        assertTrue(result.storedRunoff() > 0.0f);
        assertTrue(result.recentRainfall() == 0.0f);
    }

    private static void apply(
            WatershedChunkState state,
            WeatherSample weather,
            boolean weatherEnabled,
            boolean downstreamAvailable,
            float threshold
    ) {
        WatershedSimulationModel.Result result = WatershedSimulationModel.advance(
                input(state.conditions(), weather, weatherEnabled, downstreamAvailable, threshold)
        );
        state.apply(result, threshold, state.lastUpdatedTick() + 40L);
    }

    private static WatershedSimulationModel.Input input(
            WatershedConditions conditions,
            WeatherSample weather,
            boolean weatherEnabled,
            boolean downstreamAvailable,
            float threshold
    ) {
        return new WatershedSimulationModel.Input(
                conditions,
                weather,
                0.0f,
                0.08f,
                0.035f,
                0.45f,
                threshold,
                weatherEnabled,
                downstreamAvailable,
                true,
                true
        );
    }

    private static WatershedChunkState riverState() {
        return WatershedChunkState.create(
                91L,
                67,
                DrainageDirection.SOUTH_EAST,
                WaterFeature.RIVER,
                0.82f,
                1234L,
                0.72f,
                0L
        );
    }

    private static WatershedConditions conditions(
            float saturation,
            float rainfall,
            float runoff,
            float discharge,
            boolean flooding
    ) {
        return new WatershedConditions(
                91L,
                67,
                DrainageDirection.SOUTH_EAST,
                0.82f,
                saturation,
                rainfall,
                runoff,
                discharge,
                0.1f,
                flooding ? 0.8f : 0.4f,
                0.72f,
                flooding,
                0,
                0.35f,
                0.7f,
                0.2f,
                0.2f,
                0.25f,
                WaterFeature.RIVER
        );
    }
}
