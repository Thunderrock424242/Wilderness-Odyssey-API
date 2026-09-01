package com.thunder.wildernessodysseyapi.environment.glacial.worldgen;

import com.thunder.wildernessodysseyapi.environment.glacial.GlacialBiomeManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlacialTerrainProfileTest {

    @Test
    void iceSheetStaysBroadAndRollingWhileHighlandsKeepDramaticRelief() {
        int lowSheet = GlacialTerrainProfile.iceRise(
                GlacialBiomeManager.Family.POLAR_ICE_SHEET, -1.0, 0.0, 0.0);
        int highSheet = GlacialTerrainProfile.iceRise(
                GlacialBiomeManager.Family.POLAR_ICE_SHEET, 1.0, 1.0, 1.0);
        int highlandMass = GlacialTerrainProfile.iceRise(
                GlacialBiomeManager.Family.GLACIAL_HIGHLANDS, 0.0, 1.0, 1.0);

        assertEquals(4, lowSheet);
        assertEquals(10, highSheet);
        assertTrue(highlandMass >= 24);
    }

    @Test
    void basinProfileCreatesADeepCenterAndTallUShapedWalls() {
        int center = GlacialTerrainProfile.iceRise(
                GlacialBiomeManager.Family.GLACIAL_BASIN, -0.25, 0.4, 0.0);
        int wall = GlacialTerrainProfile.iceRise(
                GlacialBiomeManager.Family.GLACIAL_BASIN, 0.5, 0.4, 1.0);

        assertEquals(1, center);
        assertTrue(wall - center >= 13);
    }

    @Test
    void windDriftsRemainLayeredInsteadOfBecomingUniformSnowBlocks() {
        assertEquals(2, GlacialTerrainProfile.snowLayers(
                GlacialBiomeManager.Family.POLAR_ICE_SHEET, -1.0, -1.0));
        assertEquals(7, GlacialTerrainProfile.snowLayers(
                GlacialBiomeManager.Family.POLAR_ICE_SHEET, 1.0, 1.0));
        assertEquals(4, GlacialTerrainProfile.snowLayers(
                GlacialBiomeManager.Family.ICEBERG_COAST, 1.0, 1.0));
    }
}
