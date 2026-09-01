package com.thunder.wildernessodysseyapi.watersystem.ocean.coast;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CoastalSeasonModelTest {

    @Test
    void warmTropicalWaterIsBrighterAndClearerThanWinterWater() {
        CoastalSeasonModel.Sample summer = CoastalSeasonModel.sample(
                CoastalWaveProfile.ShoreType.TROPICAL, 31.0, 0.0, 0.0, 0.5);
        CoastalSeasonModel.Sample winter = CoastalSeasonModel.sample(
                CoastalWaveProfile.ShoreType.TROPICAL, -4.0, 0.8, 0.6, 0.5);

        assertTrue(summer.brightness() > winter.brightness());
        assertTrue(summer.tropicalClarity() > winter.tropicalClarity());
        assertTrue(winter.coldBlue() > summer.coldBlue());
    }

    @Test
    void coldGlacialSeasonRaisesBlueFoamAndMistWithoutChangingAuthority() {
        CoastalSeasonModel.Sample thaw = CoastalSeasonModel.sample(
                CoastalWaveProfile.ShoreType.GLACIAL, 3.0, 0.1, 0.2, 0.9);
        CoastalSeasonModel.Sample freeze = CoastalSeasonModel.sample(
                CoastalWaveProfile.ShoreType.GLACIAL, -22.0, 1.0, 1.0, 0.05);

        assertTrue(freeze.coldBlue() > thaw.coldBlue());
        assertTrue(freeze.foamMultiplier() >= thaw.foamMultiplier());
        assertTrue(freeze.mist() > thaw.mist());
    }
}
