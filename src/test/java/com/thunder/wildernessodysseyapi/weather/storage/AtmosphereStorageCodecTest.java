package com.thunder.wildernessodysseyapi.weather.storage;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereView;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import com.thunder.wildernessodysseyapi.weather.simulation.AtmosphereGrid;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies compact atmospheric continuity and corrupt-entry isolation. */
class AtmosphereStorageCodecTest {

    @Test
    void versionOneRoundTripPreservesMeaningfulCellState() {
        AtmosphereGrid original = new AtmosphereGrid(256);
        AtmosphereCellKey key = new AtmosphereCellKey(-2, 3);
        WeatherSample sample = new WeatherSample(
                -4.25,
                0.83,
                0.94,
                new WindVector(0.37, -0.42),
                0.78,
                0.61,
                0.72,
                0.65,
                PrecipitationType.SNOW
        );
        original.restore(new AtmosphereView(key, sample, 7L, 1_200L, 1_240L));

        CompoundTag encoded = AtmosphereStorageCodec.encode(original, 64);
        AtmosphereStorageCodec.DecodeResult decoded = AtmosphereStorageCodec.decode(encoded, 128, 64);

        assertEquals(AtmosphereStorageCodec.DATA_VERSION, decoded.dataVersion());
        assertEquals(1, decoded.restoredCells());
        assertEquals(0, decoded.skippedCells());
        assertFalse(decoded.recovered());
        assertEquals(256, decoded.grid().cellSize());
        AtmosphereView restored = decoded.grid().view(key);
        assertNotNull(restored);
        assertEquals(7L, restored.revision());
        assertEquals(1_200L, restored.lastSimulatedTick());
        assertEquals(1_240L, restored.lastActiveTick());
        assertEquals(sample.temperature(), restored.sample().temperature(), 0.003);
        assertEquals(sample.humidity(), restored.sample().humidity(), 0.001);
        assertEquals(sample.pressure(), restored.sample().pressure(), 0.001);
        assertEquals(sample.wind().x(), restored.sample().wind().x(), 0.003);
        assertEquals(sample.wind().z(), restored.sample().wind().z(), 0.003);
        assertEquals(sample.cloudWater(), restored.sample().cloudWater(), 0.001);
        assertEquals(sample.instability(), restored.sample().instability(), 0.001);
        assertEquals(sample.stormEnergy(), restored.sample().stormEnergy(), 0.001);
        assertEquals(sample.precipitationIntensity(), restored.sample().precipitationIntensity(), 0.001);
        assertEquals(PrecipitationType.SNOW, restored.sample().precipitationType());
    }

    @Test
    void malformedParallelArraysRecoverWithoutThrowing() {
        CompoundTag malformed = encodedSingleCell();
        malformed.putLongArray("revisions", new long[0]);

        AtmosphereStorageCodec.DecodeResult decoded = assertDoesNotThrow(
                () -> AtmosphereStorageCodec.decode(malformed, 256, 64)
        );

        assertTrue(decoded.grid().isEmpty());
        assertTrue(decoded.skippedCells() > 0);
        assertTrue(decoded.recovered());
    }

    @Test
    void invalidEntryDoesNotDiscardOtherCells() {
        AtmosphereGrid grid = new AtmosphereGrid(256);
        grid.restore(new AtmosphereView(
                new AtmosphereCellKey(0, 0),
                WeatherSample.CLEAR,
                1L,
                10L,
                10L
        ));
        grid.restore(new AtmosphereView(
                new AtmosphereCellKey(1, 0),
                rainySample(),
                2L,
                20L,
                20L
        ));
        CompoundTag encoded = AtmosphereStorageCodec.encode(grid, 64);
        long[] revisions = encoded.getLongArray("revisions");
        revisions[0] = -1L;
        encoded.putLongArray("revisions", revisions);

        AtmosphereStorageCodec.DecodeResult decoded = AtmosphereStorageCodec.decode(encoded, 256, 64);

        assertEquals(1, decoded.restoredCells());
        assertEquals(1, decoded.skippedCells());
        assertTrue(decoded.recovered());
    }

    @Test
    void missingAndOldVersionsRecoverToSafeEmptyState() {
        AtmosphereStorageCodec.DecodeResult missing = assertDoesNotThrow(
                () -> AtmosphereStorageCodec.decode(new CompoundTag(), 512, 64)
        );
        assertTrue(missing.grid().isEmpty());
        assertEquals(512, missing.grid().cellSize());
        assertTrue(missing.recovered());

        CompoundTag old = encodedSingleCell();
        old.putInt("dataVersion", 0);
        AtmosphereStorageCodec.DecodeResult legacy = assertDoesNotThrow(
                () -> AtmosphereStorageCodec.decode(old, 256, 64)
        );
        assertTrue(legacy.grid().isEmpty());
        assertTrue(legacy.recovered());
    }

    private static CompoundTag encodedSingleCell() {
        AtmosphereGrid grid = new AtmosphereGrid(256);
        grid.restore(new AtmosphereView(
                new AtmosphereCellKey(0, 0),
                rainySample(),
                1L,
                10L,
                10L
        ));
        return AtmosphereStorageCodec.encode(grid, 64);
    }

    private static WeatherSample rainySample() {
        return new WeatherSample(
                18.0,
                0.86,
                0.96,
                new WindVector(0.2, 0.1),
                0.81,
                0.64,
                0.71,
                0.55,
                PrecipitationType.RAIN
        );
    }
}
