package com.thunder.wildernessodysseyapi.weather.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the shared six-bit precipitation precision and physical bucket. */
class PrecipitationIntensityTest {

    @Test
    void finiteOutOfRangeValuesClampBeforeFloatConversion() {
        assertEquals(PrecipitationIntensity.QUANTIZED_MAX,
                PrecipitationIntensity.quantize(Double.MAX_VALUE));
        assertEquals(0, PrecipitationIntensity.quantize(-Double.MAX_VALUE));
        assertEquals(0, PrecipitationIntensity.quantize(Double.NaN));
        assertEquals(0, PrecipitationIntensity.quantize(Double.POSITIVE_INFINITY));
    }

    @Test
    void functionalClassificationUsesRoundedWireBuckets() {
        assertFalse(PrecipitationIntensity.isFunctional(0.020));
        assertTrue(PrecipitationIntensity.isFunctional(0.025));
        assertEquals(
                PrecipitationIntensity.FIRST_FUNCTIONAL_DEQUANTIZED_VALUE,
                PrecipitationIntensity.dequantize(PrecipitationIntensity.MINIMUM_FUNCTIONAL_CODE),
                1.0E-7
        );
    }
}
