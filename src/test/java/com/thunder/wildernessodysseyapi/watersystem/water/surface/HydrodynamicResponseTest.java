package com.thunder.wildernessodysseyapi.watersystem.water.surface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HydrodynamicResponseTest {
    @Test void shallowWaterBreaksBeforeDeepWater() {
        assertTrue(HydrodynamicResponse.breaking(1, 1, 0.1f) > HydrodynamicResponse.breaking(1, 20, 0.1f));
    }

    @Test void stormAndImpactIncreaseErosionPressure() {
        float calm = HydrodynamicResponse.erosionPressure(1, 0.2f, 0, 0);
        assertTrue(HydrodynamicResponse.erosionPressure(1, 0.2f, 0, 1) > calm);
        assertTrue(HydrodynamicResponse.erosionPressure(1, 0.2f, 6, 0) > calm);
    }

    @Test void stalledUpdatesDoNotApplyUnboundedCatchUpExposure() {
        assertEquals(HydrodynamicResponse.accumulate(0, 1, 2, 600),
                HydrodynamicResponse.accumulate(0, 1, 100000, 600));
        assertEquals(600, HydrodynamicResponse.accumulate(599, 1, 2, 600));
        assertEquals(0, HydrodynamicResponse.accumulate(0, 0, 1, 600));
    }

    @Test void malformedForcingCannotPoisonPressure() {
        assertTrue(Float.isFinite(HydrodynamicResponse.erosionPressure(Float.NaN, Float.POSITIVE_INFINITY, -9, Float.NaN)));
        assertEquals(0, HydrodynamicResponse.accumulate(Float.NaN, Float.NaN, Float.POSITIVE_INFINITY, 600));
    }
}
