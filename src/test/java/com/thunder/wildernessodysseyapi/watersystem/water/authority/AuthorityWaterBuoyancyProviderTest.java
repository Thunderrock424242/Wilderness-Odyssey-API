package com.thunder.wildernessodysseyapi.watersystem.water.authority;

import com.thunder.wildernessodysseyapi.watersystem.water.api.BuoyancySample;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterSample;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the allocation-free vertical intersection math used by entity state. */
class AuthorityWaterBuoyancyProviderTest {

    @Test
    void clampsSubmergedFractionToEntityBounds() {
        assertEquals(0.0, AuthorityWaterBuoyancyProvider.submergedFraction(10.0, 12.0, 9.5));
        assertEquals(0.25, AuthorityWaterBuoyancyProvider.submergedFraction(10.0, 12.0, 10.5));
        assertEquals(1.0, AuthorityWaterBuoyancyProvider.submergedFraction(10.0, 12.0, 13.0));
    }

    @Test
    void handlesDegenerateOrUnknownSurfacesAsDry() {
        assertEquals(0.0, AuthorityWaterBuoyancyProvider.submergedFraction(10.0, 10.0, 10.0));
        assertEquals(0.0, AuthorityWaterBuoyancyProvider.submergedFraction(10.0, 12.0, Double.NaN));
    }

    @Test
    void footprintAggregationWeightsForceByDisplacedVolume() {
        BuoyancySampleAccumulator accumulator = new BuoyancySampleAccumulator().reset();
        accumulator.add(sample(true, 11.0, 2.0, 0.0, 0.0), 10.0, 12.0);
        accumulator.add(sample(true, 12.0, 4.0, 0.0, 0.0), 10.0, 12.0);
        accumulator.add(sample(false, Double.NaN, 0.0, 0.0, 0.0), 10.0, 12.0);

        BuoyancySample result = accumulator.finish();

        assertAll(
                () -> assertTrue(result.touchingWater()),
                () -> assertFalse(result.submerged()),
                () -> assertEquals(0.5, result.submergedFraction(), 1.0e-9),
                () -> assertEquals(10.0 / 3.0, result.current().x, 1.0e-9),
                () -> assertEquals(1.0, result.surfaceNormal().length(), 1.0e-9)
        );
    }

    @Test
    void fullySubmergedRequiresEveryFootprintPoint() {
        BuoyancySampleAccumulator accumulator = new BuoyancySampleAccumulator().reset();
        for (int point = 0; point < 5; point++) {
            accumulator.add(sample(true, 13.0, 0.0, 0.0, 0.0), 10.0, 12.0);
        }

        assertTrue(accumulator.finish().submerged());
    }

    @Test
    void orientedHullAxesFollowCardinalAndDiagonalYaw() {
        assertEquals(0.0, AuthorityWaterBuoyancyProvider.forwardX(0.0f), 1.0e-9);
        assertEquals(1.0, AuthorityWaterBuoyancyProvider.forwardZ(0.0f), 1.0e-9);
        assertEquals(-1.0, AuthorityWaterBuoyancyProvider.forwardX(90.0f), 1.0e-9);
        assertEquals(0.0, AuthorityWaterBuoyancyProvider.forwardZ(90.0f), 1.0e-9);
        assertEquals(-Math.sqrt(0.5), AuthorityWaterBuoyancyProvider.forwardX(45.0f), 1.0e-9);
        assertEquals(Math.sqrt(0.5), AuthorityWaterBuoyancyProvider.forwardZ(45.0f), 1.0e-9);
        assertEquals(0.0,
                AuthorityWaterBuoyancyProvider.forwardX(45.0f)
                        * AuthorityWaterBuoyancyProvider.rightX(45.0f)
                        + AuthorityWaterBuoyancyProvider.forwardZ(45.0f)
                        * AuthorityWaterBuoyancyProvider.rightZ(45.0f),
                1.0e-9);
    }

    private static WaterSample sample(
            boolean water,
            double surfaceHeight,
            double currentX,
            double currentY,
            double currentZ
    ) {
        return new WaterSample().set(
                water,
                surfaceHeight,
                1.0,
                currentX,
                currentY,
                currentZ,
                0.0,
                1.0,
                0.0
        );
    }
}
