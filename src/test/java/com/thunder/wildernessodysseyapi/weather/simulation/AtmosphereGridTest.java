package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationIntensity;
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

    @Test
    void packedSnapshotRemainsFrozenWhenAuthorityChanges() {
        AtmosphereGrid grid = new AtmosphereGrid(100);
        AtmosphereCellKey key = new AtmosphereCellKey(0, 0);
        grid.getOrCreate(key, WeatherSample.CLEAR, 0L);

        var previous = grid.snapshotByPackedKey();
        grid.force(key, rainySample(), 20L);

        assertEquals(WeatherSample.CLEAR, previous.get(key.packed()).sample());
        assertEquals(rainySample(), grid.view(key).sample());
    }

    @Test
    void primitivePrecipitationPathUsesCanonicalFunctionalBuckets() {
        AtmosphereGrid grid = new AtmosphereGrid(16);
        grid.getOrCreate(new AtmosphereCellKey(0, 0), sample(15.0, 0.025, PrecipitationType.RAIN), 0L);
        grid.getOrCreate(new AtmosphereCellKey(1, 0), sample(15.0, 0.020, PrecipitationType.RAIN), 0L);
        grid.getOrCreate(new AtmosphereCellKey(2, 0), sample(-5.0, 0.50, PrecipitationType.SNOW), 0L);

        assertEquals(0.025, grid.precipitationIntensity(8.0, 8.0), 1.0E-12);
        assertEquals(PrecipitationType.RAIN, grid.functionalPrecipitationType(8.0, 8.0));
        assertEquals(PrecipitationType.NONE, grid.functionalPrecipitationType(24.0, 8.0));
        assertEquals(PrecipitationType.SNOW, grid.functionalPrecipitationType(40.0, 8.0));
    }

    @Test
    void functionalInterpolationQuantizesCellEndpointsBeforeBlending() {
        AtmosphereGrid grid = new AtmosphereGrid(16);
        grid.getOrCreate(new AtmosphereCellKey(0, 0), WeatherSample.CLEAR, 0L);
        grid.getOrCreate(new AtmosphereCellKey(1, 0), sample(15.0, 0.047, PrecipitationType.RAIN), 0L);

        double rawMidpoint = grid.sample(16.0, 8.0).precipitationIntensity();
        double wireMidpoint = (
                PrecipitationIntensity.dequantize(PrecipitationIntensity.quantize(0.0))
                        + PrecipitationIntensity.dequantize(PrecipitationIntensity.quantize(0.047))
        ) * 0.5;

        assertFalse(PrecipitationIntensity.isFunctional(rawMidpoint));
        assertTrue(PrecipitationIntensity.isFunctional(wireMidpoint));
        assertEquals(PrecipitationType.RAIN, grid.functionalPrecipitationType(16.0, 8.0));
    }

    @Test
    void functionalInterpolationDropsEndpointTypeThatWireRoundsClear() {
        AtmosphereGrid grid = new AtmosphereGrid(16);
        grid.getOrCreate(new AtmosphereCellKey(0, 0), sample(-5.0, 0.001, PrecipitationType.SNOW), 0L);
        grid.getOrCreate(new AtmosphereCellKey(1, 0), sample(5.0, 0.10, PrecipitationType.RAIN), 0L);

        assertEquals(0, PrecipitationIntensity.quantize(0.001));
        assertEquals(PrecipitationType.RAIN, grid.functionalPrecipitationType(16.0, 8.0));
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

    private static WeatherSample sample(
            double temperature,
            double precipitation,
            PrecipitationType type
    ) {
        return new WeatherSample(
                temperature,
                0.9,
                0.95,
                WindVector.ZERO,
                0.9,
                0.6,
                0.7,
                precipitation,
                type
        );
    }
}
