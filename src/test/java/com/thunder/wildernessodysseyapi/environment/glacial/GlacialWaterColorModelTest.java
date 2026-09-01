package com.thunder.wildernessodysseyapi.environment.glacial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlacialWaterColorModelTest {

    @Test
    void meltFractionInterpolatesBetweenWinterAndFamilySummerColor() {
        int winter = GlacialWaterColorModel.surfaceTint(
                GlacialBiomeManager.Family.ICEBERG_COAST, 0x336699, 0.0);
        int summer = GlacialWaterColorModel.surfaceTint(
                GlacialBiomeManager.Family.ICEBERG_COAST, 0x336699, 1.0);
        int middle = GlacialWaterColorModel.surfaceTint(
                GlacialBiomeManager.Family.ICEBERG_COAST, 0x336699, 0.5);

        assertEquals(0x1D6FA8, winter);
        assertNotEquals(winter, summer);
        assertTrue(channel(middle, 8) > channel(winter, 8));
        assertTrue(channel(middle, 0) > channel(winter, 0));
    }

    @Test
    void inlandAndCoastalFamiliesKeepDistinctSummerTints() {
        int coast = GlacialWaterColorModel.surfaceTint(
                GlacialBiomeManager.Family.ICEBERG_COAST, 0x3F76E4, 1.0);
        int sheet = GlacialWaterColorModel.surfaceTint(
                GlacialBiomeManager.Family.POLAR_ICE_SHEET, 0x3F76E4, 1.0);

        assertNotEquals(coast, sheet);
    }

    @Test
    void summerMeltwaterKeepsTheRequestedVividCyanContrast() {
        int valley = GlacialWaterColorModel.surfaceTint(
                GlacialBiomeManager.Family.MELTWATER_VALLEY, 0x3ABFD8, 1.0);

        assertTrue(channel(valley, 8) >= 200);
        assertTrue(channel(valley, 0) >= 220);
        assertTrue(channel(valley, 0) > channel(valley, 16) + 120);
    }

    private static int channel(int color, int shift) {
        return color >>> shift & 0xFF;
    }
}
