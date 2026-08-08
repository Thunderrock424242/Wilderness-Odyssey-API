package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.CloudType;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies every operator preset remains visible and derives to its requested genus. */
class CloudDebugPresetTest {

    @Test
    void everyPresetRoundTripsThroughTheProductionClassifier() {
        WeatherSample baseline = baseline(-5.0);

        for (CloudType type : CloudType.values()) {
            WeatherSample preset = CloudDebugPreset.apply(type, baseline);

            assertEquals(type, preset.cloudType(), "preset " + type);
            assertEquals(baseline.temperature(), preset.temperature());
            assertEquals(baseline.pressure(), preset.pressure());
            assertEquals(baseline.wind(), preset.wind());
            assertEquals(baseline.surface(), preset.surface());
            if (type == CloudType.CLEAR) {
                assertEquals(0.0, preset.cloudWater());
            } else {
                assertTrue(preset.cloudWater() >= 0.25, "visible mass for " + type);
            }
        }
    }

    @Test
    void cloudShapePresetsUseRainAndLeaveExplicitSnowToTheSnowCommand() {
        WeatherSample cold = CloudDebugPreset.apply(CloudType.NIMBOSTRATUS, baseline(-5.0));
        WeatherSample warm = CloudDebugPreset.apply(CloudType.CUMULONIMBUS, baseline(18.0));

        assertEquals(PrecipitationType.RAIN, cold.precipitationType());
        assertEquals(PrecipitationType.RAIN, warm.precipitationType());
        assertTrue(cold.precipitationIntensity() > 0.0);
        assertTrue(warm.precipitationIntensity() > cold.precipitationIntensity());
    }

    private static WeatherSample baseline(double temperature) {
        return new WeatherSample(
                temperature,
                0.55,
                1.12,
                new WindVector(0.90, -0.35),
                0.12,
                0.18,
                0.10,
                0.0,
                PrecipitationType.NONE,
                0.0,
                0.12,
                WindVector.ZERO,
                WeatherSample.CLEAR.surface()
        );
    }
}
