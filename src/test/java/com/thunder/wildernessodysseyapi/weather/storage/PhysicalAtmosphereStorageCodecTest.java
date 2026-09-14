package com.thunder.wildernessodysseyapi.weather.storage;

import com.thunder.wildernessodysseyapi.weather.api.*;
import com.thunder.wildernessodysseyapi.weather.simulation.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalAtmosphereStorageCodecTest {
    @Test
    void restartingRetainsRawTerrainHumidityWithoutStackingSeasonalAdjustments() {
        var original = view(PrecipitationType.RAIN, 0);
        double terrainHumidity = .95;
        for (int restart = 0; restart < 10; restart++) {
            double combined = Math.min(1, terrainHumidity + .12);
            var environment = new AtmosphereEnvironment(15, combined, 64, 0, .5, 0, 0, 0, 0, 1,
                    0, 0, 0, 0, 0, 0, 0, true, .25, combined - terrainHumidity);
            var view = new AtmosphereView(original.key(), original.sample(), 1, 100, 80,
                    original.physicalState(), environment);
            var restored = PhysicalAtmosphereStorageCodec.environment(PhysicalAtmosphereStorageCodec.encode(view));
            terrainHumidity = restored.terrainHumidity();
            assertEquals(.95, terrainHumidity, 1.0E-12);
        }
    }
    @Test
    void exactInventoryAndWarmNoseSurviveRepeatedSaveLoadForEveryPhase() {
        for (PrecipitationType phase : PrecipitationType.values()) {
            AtmosphereView view = view(phase, 0);
            AtmosphereGrid grid = new AtmosphereGrid(256);
            grid.restore(view);
            for (int i = 0; i < 10; i++) {
                var decoded = AtmosphereStorageCodec.decode(AtmosphereStorageCodec.encode(grid, 64), 256, 64);
                assertFalse(decoded.recovered());
                grid = decoded.grid();
            }
            var restored = grid.view(view.key());
            assertEquals(view.physicalState(), restored.physicalState());
            assertEquals(view.environment(), restored.environment());
            assertEquals(phase, restored.sample().precipitationType());
        }
    }

    @Test
    void oneMalformedPhysicalRowDoesNotDiscardTheOtherCell() {
        var grid = new AtmosphereGrid(256);
        grid.restore(view(PrecipitationType.SLEET, 0));
        grid.restore(view(PrecipitationType.FREEZING_RAIN, 1));
        CompoundTag saved = AtmosphereStorageCodec.encode(grid, 64);
        saved.getList("physical", Tag.TAG_COMPOUND).getCompound(0).putLongArray("layers", new long[0]);
        var decoded = AtmosphereStorageCodec.decode(saved, 256, 64);
        assertEquals(1, decoded.restoredCells());
        assertEquals(1, decoded.skippedCells());
        assertEquals(view(PrecipitationType.FREEZING_RAIN, 1).physicalState(),
                decoded.grid().view(new AtmosphereCellKey(1, 0)).physicalState());
    }

    @Test
    void genuineVersionThreeWordsMigrateOnceWithoutRequiringPhysicalData() {
        // Independent legacy layout: no physical rows, two-bit precipitation at bit 48.
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("dataVersion", 3); legacy.putInt("cellSize", 256);
        legacy.putLongArray("cellKeys", new long[]{0});
        legacy.putLongArray("weatherA", new long[]{32768L | (3000L << 16) | (32768L << 28) | (512L << 44) | (512L << 54)});
        legacy.putLongArray("weatherB", new long[]{2000L | (1000L << 12) | (2000L << 24) | (1500L << 36) | (2L << 48)});
        legacy.putLongArray("weatherC", new long[]{2048L | (2048L << 12) | (2048L << 24) | (2048L << 36)});
        legacy.putLongArray("weatherD", new long[]{1000L | (1000L << 12) | (2000L << 24)});
        legacy.putLongArray("revisions", new long[]{5});
        legacy.putLongArray("lastSimulatedTicks", new long[]{100});
        legacy.putLongArray("lastActiveTicks", new long[]{80});
        var migrated = AtmosphereStorageCodec.decode(legacy, 256, 64);
        assertEquals(1, migrated.restoredCells());
        assertTrue(migrated.recovered());
        var before = migrated.grid().view(new AtmosphereCellKey(0, 0));
        assertEquals(PrecipitationType.SNOW, before.sample().precipitationType());
        var reloaded = AtmosphereStorageCodec.decode(AtmosphereStorageCodec.encode(migrated.grid(), 64), 256, 64);
        assertFalse(reloaded.recovered());
        assertEquals(before.physicalState(), reloaded.grid().view(before.key()).physicalState());
    }

    private static AtmosphereView view(PrecipitationType phase, int x) {
        var column = new AtmosphericColumn(new AtmosphericLayer(0, 1012.123, -2, .9, 3, 1),
                new AtmosphericLayer(1500, 850, 6, 1, 10, -2), new AtmosphericLayer(3000, 700, -3, .8, 15, 4),
                new AtmosphericLayer(5500, 500, -24, .7, 20, 10));
        var state = new AtmosphericPhysicalState(-2, 1012.123, 37.1234567890123, 1.234567890123,
                .678912345678, column, 1.1234, 1123.456, .78, -3.123, phase == PrecipitationType.NONE ? 0 : 4.5678,
                new SurfaceWeatherState(.123, .234, .345, .456));
        return new AtmosphereView(new AtmosphereCellKey(x, 0), state.toWeatherSample(state.surface(), phase),
                10, 100, 80, state, AtmosphereEnvironment.TEMPERATE);
    }
}
