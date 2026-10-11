package com.thunder.wildernessodysseyapi.temporalrift.hybrid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiftFieldMathTest {
    @Test
    void personalImpactRemainsVisibleAfterLeavingTheFieldAndThenExpires() {
        double outside = RiftFieldMath.intensity(40, 30);
        assertEquals(0, outside);
        assertTrue(RiftFieldMath.presentationIntensity(outside, 1) > 0);
        assertEquals(0, RiftFieldMath.presentationIntensity(outside, 0));
        assertEquals(0.8, RiftFieldMath.presentationIntensity(0.8, 1), 1.0E-9);
    }

    @Test
    void distanceFalloffIsSmoothBoundedAndZeroOutsideField() {
        assertEquals(1.0, RiftFieldMath.intensity(0, 30), 1.0E-9);
        assertEquals(0.5, RiftFieldMath.intensity(15, 30), 1.0E-9);
        assertEquals(0, RiftFieldMath.intensity(30, 30));
        assertEquals(0, RiftFieldMath.intensity(80, 30));
        assertEquals(0, RiftFieldMath.intensity(Double.NaN, 30));
        assertEquals(0, RiftFieldMath.intensity(4, 0));
        double previous = 1;
        for (int distance = 0; distance < 40; distance++) {
            double intensity = RiftFieldMath.intensity(distance, 30);
            assertTrue(intensity >= 0 && intensity <= previous);
            previous = intensity;
        }
    }

    @Test
    void fastEntryDamageIsCappedAndCannotConsumeLastHealthPoint() {
        assertEquals(4, RiftFieldMath.impactDamage(0, 20, 4, 2, 8));
        assertEquals(8, RiftFieldMath.impactDamage(100, 20, 4, 2, 8));
        assertEquals(2, RiftFieldMath.impactDamage(100, 3, 4, 2, 8));
        assertEquals(0, RiftFieldMath.impactDamage(100, 0.5, 4, 2, 8));
        assertEquals(4, RiftFieldMath.impactDamage(Double.NaN, 20, 4, 2, 8));
    }

    @Test
    void dormantAndStabilizedStatesDoNotApplyDangerousForces() {
        assertEquals(0, RiftState.DORMANT.forceMultiplier());
        assertEquals(0, RiftState.STABILIZED.forceMultiplier());
        assertFalse(RiftState.DORMANT.rejectsEntry());
        assertFalse(RiftState.STABILIZED.rejectsEntry());
        assertTrue(RiftState.ACTIVE.rejectsEntry());
        assertTrue(RiftState.CRITICAL.forceMultiplier() > RiftState.ACTIVE.forceMultiplier());
        assertEquals(RiftState.ACTIVE, RiftState.fromName("unknown"));
    }
}
