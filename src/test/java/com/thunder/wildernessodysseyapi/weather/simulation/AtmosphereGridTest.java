package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that the authoritative grid exposes smooth, position-local weather. */
class AtmosphereGridTest {

    @Test
    void samplesLocalizedPrecipitationAtCellCentersAndInterpolatesBetweenThem() {
        AtmosphereGrid grid = new AtmosphereGrid(100);
        grid.getOrCreate(new AtmosphereCellKey(0, 0), rainySample(), 0L);
        grid.getOrCreate(new AtmosphereCellKey(1, 0), WeatherSample.CLEAR, 0L);

        WeatherSample rainyCenter = grid.sample(50.0, 50.0);
        WeatherSample midpoint = grid.sample(100.0, 50.0);
        WeatherSample clearCenter = grid.sample(150.0, 50.0);

        assertTrue(rainyCenter.isRaining());
        assertEquals(1.0, rainyCenter.precipitationIntensity(), 1.0E-9);
        assertTrue(midpoint.isRaining());
        assertEquals(0.5, midpoint.precipitationIntensity(), 1.0E-9);
        assertFalse(clearCenter.hasPrecipitation());
    }

    @Test
    void unchangedSimulationTouchDoesNotAdvanceNetworkRevision() {
        AtmosphereGrid grid = new AtmosphereGrid(100);
        AtmosphereCellKey key = new AtmosphereCellKey(0, 0);
        grid.getOrCreate(key, WeatherSample.CLEAR, 0L);

        assertFalse(grid.applyIfRevision(key, 0L, WeatherSample.CLEAR, 60L));
        assertEquals(0L, grid.view(key).revision());
        assertEquals(60L, grid.view(key).lastSimulatedTick());
    }

    private static WeatherSample rainySample() {
        return new WeatherSample(
                18.0,
                0.9,
                0.95,
                WindVector.ZERO,
                0.9,
                0.6,
                0.7,
                1.0,
                PrecipitationType.RAIN
        );
    }
}
