package com.thunder.wildernessodysseyapi.watersystem.water.authority;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
