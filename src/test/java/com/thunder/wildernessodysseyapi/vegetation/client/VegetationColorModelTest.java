package com.thunder.wildernessodysseyapi.vegetation.client;

import com.thunder.wildernessodysseyapi.vegetation.api.VegetationClimateState;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationSeasonState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class VegetationColorModelTest {

    @Test
    void healthyRegionKeepsBiomeOwnedGrassColor() {
        int biomeColor = 0x4FAE55;

        assertEquals(biomeColor, VegetationColorModel.applyDrought(
                biomeColor,
                VegetationClimateState.DEFAULT
        ));
    }

    @Test
    void prolongedDroughtAddsDryTintWithoutReplacingTheBaseColorSource() {
        int biomeColor = 0x4FAE55;
        VegetationClimateState drought = new VegetationClimateState(
                0.08,
                0.0,
                1.0,
                0.0,
                VegetationSeasonState.DRY,
                0L,
                0L,
                0,
                0.0
        );

        int dryColor = VegetationColorModel.applyDrought(biomeColor, drought);

        assertNotEquals(biomeColor, dryColor);
        assertNotEquals(0xB7A05A, dryColor);
    }
}
