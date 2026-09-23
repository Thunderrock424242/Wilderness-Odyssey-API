package com.thunder.wildernessodysseyapi.temporalrift.echo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EchoRealityModelTest {
    @Test
    void samplingPreservesChanceEndpointsAndBreaksAreASubset() {
        int placed = 0;
        int broken = 0;
        for (int i = 0; i < 10000; i++) {
            long hash = EchoRegionModel.mix(i);
            assertFalse(EchoRealityModel.sample(hash, 0, false));
            assertTrue(EchoRealityModel.sample(hash, 1, false));
            boolean place = EchoRealityModel.sample(hash, 0.35, false);
            boolean broke = EchoRealityModel.sample(hash, 0.35, true);
            assertFalse(broke && !place);
            if (place) placed++;
            if (broke) broken++;
        }
        assertTrue(placed > 3200 && placed < 3800);
        assertTrue(broken > 1500 && broken < 2000);
    }

    @Test
    void fragmentsKeepACoherentBoundedOffsetIncludingNegativeCoordinates() {
        long fragment = EchoRealityModel.fragmentHash(42, -8, 64, -8);
        assertEquals(fragment, EchoRealityModel.fragmentHash(42, -1, 71, -1));
        for (int x = -100; x < 100; x++) {
            long hash = EchoRealityModel.fragmentHash(42, x, 64, x);
            assertTrue(Math.abs(EchoRealityModel.lateralOffset(hash)) <= 2);
            assertTrue(EchoRealityModel.verticalOffset(hash) >= -3 && EchoRealityModel.verticalOffset(hash) <= 0);
        }
    }
}
