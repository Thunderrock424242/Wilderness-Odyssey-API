package com.thunder.wildernessodysseyapi.weather.client;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeatherSnapshotTest {

    private static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");

    @Test
    void interpolatesBetweenAtmosphericCellCenters() {
        WeatherSnapshot snapshot = snapshot(Map.of(
                WeatherSnapshot.packCell(0, 0), cell(0, 0, sample(10.0, 0.0, PrecipitationType.NONE)),
                WeatherSnapshot.packCell(1, 0), cell(1, 0, sample(20.0, 1.0, PrecipitationType.RAIN))
        ));

        WeatherSample midpoint = snapshot.sample(256.0, 128.0);

        assertEquals(15.0, midpoint.temperature(), 1.0E-9);
        assertEquals(0.5, midpoint.precipitationIntensity(), 1.0E-9);
        assertEquals(PrecipitationType.RAIN, midpoint.precipitationType());
        assertEquals(
                midpoint.precipitationIntensity(),
                snapshot.precipitationIntensity(256.0, 128.0),
                1.0E-9
        );
        assertEquals(midpoint.precipitationType(), snapshot.precipitationType(256.0, 128.0));
    }

    @Test
    void negativeCoordinatesUseTheNegativeCellCenter() {
        WeatherSnapshot snapshot = snapshot(Map.of(
                WeatherSnapshot.packCell(-1, 0), cell(-1, 0, sample(-10.0, 0.8, PrecipitationType.SNOW)),
                WeatherSnapshot.packCell(0, 0), cell(0, 0, sample(20.0, 0.0, PrecipitationType.NONE))
        ));

        WeatherSample centered = snapshot.sample(-128.0, 128.0);

        assertEquals(-10.0, centered.temperature(), 1.0E-9);
        assertEquals(0.8, centered.precipitationIntensity(), 1.0E-9);
        assertEquals(PrecipitationType.SNOW, centered.precipitationType());
    }

    @Test
    void missingNetworkEdgeNeighborsReuseTheNearestCell() {
        WeatherSnapshot snapshot = snapshot(Map.of(
                WeatherSnapshot.packCell(0, 0), cell(0, 0, sample(12.0, 0.75, PrecipitationType.RAIN))
        ));

        WeatherSample nearRegionEdge = snapshot.sample(250.0, 250.0);

        assertEquals(12.0, nearRegionEdge.temperature(), 1.0E-9);
        assertEquals(0.75, nearRegionEdge.precipitationIntensity(), 1.0E-9);
    }

    @Test
    void snapshotBlendInterpolatesContinuousFieldsAndPrecipitationType() {
        WeatherSnapshot from = snapshot(Map.of(
                WeatherSnapshot.packCell(0, 0), cell(0, 0, sample(4.0, 0.2, PrecipitationType.RAIN))
        ));
        WeatherSnapshot to = snapshot(Map.of(
                WeatherSnapshot.packCell(0, 0), cell(0, 0, sample(-4.0, 0.8, PrecipitationType.SNOW))
        ));

        WeatherSample blended = WeatherSnapshot.blend(from, to, 0.5).sample(128.0, 128.0);

        assertEquals(0.0, blended.temperature(), 1.0E-9);
        assertEquals(0.5, blended.precipitationIntensity(), 1.0E-9);
        assertEquals(PrecipitationType.SNOW, blended.precipitationType());
    }

    @Test
    void scalarRenderPathPreservesAuthoritativeTypeWhenTemperatureWasDebugEdited() {
        WeatherSnapshot snapshot = snapshot(Map.of(
                WeatherSnapshot.packCell(0, 0), cell(0, 0, sample(-10.0, 0.8, PrecipitationType.RAIN))
        ));

        assertEquals(PrecipitationType.RAIN, snapshot.precipitationType(128.0, 128.0));
    }

    private static WeatherSnapshot snapshot(Map<Long, WeatherSnapshot.SnapshotCell> cells) {
        return new WeatherSnapshot(OVERWORLD, 1, 1L, 256, new HashMap<>(cells));
    }

    private static WeatherSnapshot.SnapshotCell cell(int x, int z, WeatherSample sample) {
        return new WeatherSnapshot.SnapshotCell(x, z, 1L, sample);
    }

    private static WeatherSample sample(
            double temperature,
            double precipitationIntensity,
            PrecipitationType precipitationType
    ) {
        return new WeatherSample(
                temperature,
                0.8,
                1.0,
                WindVector.ZERO,
                0.7,
                0.4,
                0.5,
                precipitationIntensity,
                precipitationType
        );
    }
}
