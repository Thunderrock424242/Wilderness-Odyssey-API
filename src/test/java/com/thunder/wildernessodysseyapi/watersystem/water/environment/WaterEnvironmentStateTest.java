package com.thunder.wildernessodysseyapi.watersystem.water.environment;

import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterEnvironmentStateTest {

    @Test
    void openLakeBuildsMoreWindEnergyThanShelteredPond() {
        OceanSeaState.Sample windy = new OceanSeaState.Sample(
                0.85f, 0.6f, 0.8f, 17.0f, 1.65f, 2.20f, 0.78f, 0.82f);
        WaterEnvironmentState lake = WaterEnvironmentState.derive(
                WaterBodyClassifier.WaterType.LAKE,
                windy,
                0.4f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                9.0f,
                1_024L * WaterVolumeChunk.UNITS_PER_BLOCK,
                false
        );
        WaterEnvironmentState pond = WaterEnvironmentState.derive(
                WaterBodyClassifier.WaterType.POND,
                windy,
                0.4f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                2.0f,
                12L * WaterVolumeChunk.UNITS_PER_BLOCK,
                true
        );

        assertTrue(lake.fetch() > pond.fetch());
        assertTrue(lake.waveSpectrum().chopScale() > pond.waveSpectrum().chopScale());
    }

    @Test
    void tideIsRestrictedToOceanAndCoast() {
        WaterEnvironmentState lake = WaterEnvironmentState.derive(
                WaterBodyClassifier.WaterType.LAKE,
                OceanSeaState.CALM,
                0.0f,
                0.8f,
                -0.2f,
                0.0f,
                0.0f,
                5.0f,
                100L * WaterVolumeChunk.UNITS_PER_BLOCK,
                false
        );
        WaterEnvironmentState coast = WaterEnvironmentState.derive(
                WaterBodyClassifier.WaterType.COAST,
                OceanSeaState.CALM,
                0.0f,
                0.8f,
                -0.2f,
                0.0f,
                0.0f,
                5.0f,
                100L * WaterVolumeChunk.UNITS_PER_BLOCK,
                true
        );

        assertEquals(0.0f, lake.tideHeight(), 1.0e-6f);
        assertEquals(0.8f, coast.tideHeight(), 1.0e-6f);
        assertEquals(-0.2f, coast.tideRate(), 1.0e-6f);
    }
}
