package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the explicit legacy-water command cannot expand into an unbounded scan. */
class ExistingWorldWaterConverterTest {

    @Test
    void requestedRadiusIsAlwaysBounded() {
        assertEquals(1, ExistingWorldWaterConverter.boundedRadius(-100));
        assertEquals(8, ExistingWorldWaterConverter.boundedRadius(8));
        assertEquals(ExistingWorldWaterConverter.MAX_RADIUS,
                ExistingWorldWaterConverter.boundedRadius(Integer.MAX_VALUE));
    }

    @Test
    void transitionReportsRequireCompleteCoverageAndNoVanillaRemainder() {
        assertFalse(new ExistingWorldWaterConverter.LoadedCoverage(8, 288, 1).complete());
        assertTrue(new ExistingWorldWaterConverter.LoadedCoverage(8, 289, 0).complete());
        assertFalse(
                new ExistingWorldWaterConverter.ConversionVerification(8, 4_913, 0, 1).successful());
        assertTrue(
                new ExistingWorldWaterConverter.ConversionVerification(8, 4_913, 0, 0).successful());

        assertTrue(new ExistingWorldWaterConverter.ActivationPreflight(
                8, 10, 0, 0, 0, 0).successful());
        assertFalse(new ExistingWorldWaterConverter.ActivationPreflight(
                8, 10, 0, 0, 1, 0).successful());
        assertFalse(new ExistingWorldWaterConverter.ActivationPreflight(
                8, 10, 0, 0, 0, 1).successful());
        assertTrue(new ExistingWorldWaterConverter.StagingVerification(8, 10, 0).successful());
        assertFalse(new ExistingWorldWaterConverter.StagingVerification(8, 10, 1).successful());
    }
}
