package com.thunder.wildernessodysseyapi.weather.client;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import com.thunder.wildernessodysseyapi.weather.client.cloud.CloudFieldSample;
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

    @Test
    void cloudFieldInterpolatesAllFourAtmosphericCells() {
        WeatherSnapshot snapshot = snapshot(Map.of(
                WeatherSnapshot.packCell(0, 0), cell(0, 0, cloudSample(0.0, 0.1, 0.2, 0.3, -0.8, 0.2)),
                WeatherSnapshot.packCell(1, 0), cell(1, 0, cloudSample(0.2, 0.3, 0.4, 0.5, -0.4, 0.4)),
                WeatherSnapshot.packCell(0, 1), cell(0, 1, cloudSample(0.6, 0.5, 0.6, 0.7, 0.4, 0.6)),
                WeatherSnapshot.packCell(1, 1), cell(1, 1, cloudSample(1.0, 0.7, 0.8, 0.9, 0.8, 0.8))
        ));

        CloudFieldSample field = snapshot.cloudField(256.0, 256.0);

        assertEquals(1.0, field.support(), 1.0E-12);
        assertEquals(0.45, field.cloudWater(), 1.0E-12);
        assertEquals(0.40, field.precipitationIntensity(), 1.0E-12);
        assertEquals(0.50, field.stormEnergy(), 1.0E-12);
        assertEquals(0.60, field.instability(), 1.0E-12);
        assertEquals(0.0, field.windX(), 1.0E-12);
        assertEquals(0.50, field.windZ(), 1.0E-12);
    }

    @Test
    void cloudFieldFadesSupportAtSynchronizedRegionEdge() {
        WeatherSnapshot snapshot = snapshot(Map.of(
                WeatherSnapshot.packCell(0, 0), cell(
                        0,
                        0,
                        cloudSample(0.80, 0.60, 0.50, 0.40, 0.30, -0.20)
                ))
        );

        CloudFieldSample centered = snapshot.cloudField(128.0, 128.0);
        CloudFieldSample nearEdge = snapshot.cloudField(192.0, 128.0);
        CloudFieldSample outside = snapshot.cloudField(384.0, 128.0);

        assertEquals(1.0, centered.support(), 1.0E-12);
        assertEquals(0.75, nearEdge.support(), 1.0E-12);
        assertEquals(0.80, nearEdge.cloudWater(), 1.0E-12);
        assertEquals(0.60, nearEdge.precipitationIntensity(), 1.0E-12);
        assertEquals(0.0, outside.support(), 1.0E-12);
        assertEquals(0.0, outside.cloudWater(), 1.0E-12);
    }

    @Test
    void visualPrecipitationFadesInsteadOfRepeatingAtRegionEdge() {
        WeatherSnapshot snapshot = snapshot(Map.of(
                WeatherSnapshot.packCell(0, 0), cell(
                        0,
                        0,
                        cloudSample(0.80, 0.60, 0.50, 0.40, 0.30, -0.20)
                ))
        );

        assertEquals(0.60, snapshot.supportedPrecipitationIntensity(128.0, 128.0), 1.0E-12);
        assertEquals(0.45, snapshot.supportedPrecipitationIntensity(192.0, 128.0), 1.0E-12);
        assertEquals(0.0, snapshot.supportedPrecipitationIntensity(384.0, 128.0), 1.0E-12);
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

    private static WeatherSample cloudSample(
            double cloudWater,
            double precipitationIntensity,
            double stormEnergy,
            double instability,
            double windX,
            double windZ
    ) {
        return new WeatherSample(
                12.0,
                0.8,
                1.0,
                new WindVector(windX, windZ),
                cloudWater,
                instability,
                stormEnergy,
                precipitationIntensity,
                PrecipitationType.RAIN
        );
    }
}
