package com.thunder.wildernessodysseyapi.ecosystem.distant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies configured LOD bands, cross-fades, and conservative absorption. */
class DistantWildlifeTransitionPolicyTest {

    @Test
    void classifiesConfiguredBandsWithoutHardcodedWorldDistances() {
        assertEquals(DistantWildlifeTransitionPolicy.LodState.REAL, state(95.9));
        assertEquals(DistantWildlifeTransitionPolicy.LodState.TRANSITION, state(96.0));
        assertEquals(DistantWildlifeTransitionPolicy.LodState.DISTANT, state(128.0));
        assertEquals(DistantWildlifeTransitionPolicy.LodState.DISTANT, state(200.0));
        assertEquals(DistantWildlifeTransitionPolicy.LodState.DISTANT, state(400.0));
        assertEquals(DistantWildlifeTransitionPolicy.LodState.DISTANT_FADE, state(480.0));
        assertEquals(DistantWildlifeTransitionPolicy.LodState.HIDDEN, state(512.0));
    }

    @Test
    void representationCrossFadesAtBothBoundaries() {
        assertEquals(0.0F, alpha(96.0), 1.0E-6F);
        assertEquals(0.5F, alpha(112.0), 1.0E-6F);
        assertEquals(1.0F, alpha(128.0), 1.0E-6F);
        assertEquals(0.5F, alpha(496.0), 1.0E-6F);
        assertEquals(0.0F, alpha(512.0), 1.0E-6F);
    }

    @Test
    void abstractionRequiresDistanceVisibilityAndTimeSafeguards() {
        assertFalse(canAbstract(128.0, false, 400L));
        assertFalse(canAbstract(200.0, true, 400L));
        assertFalse(canAbstract(200.0, false, 399L));
        assertTrue(canAbstract(200.0, false, 400L));
        assertTrue(DistantWildlifeTransitionPolicy.shouldMaterialize(128.0, 96, 32));
        assertFalse(DistantWildlifeTransitionPolicy.shouldMaterialize(128.01, 96, 32));
    }

    private static DistantWildlifeTransitionPolicy.LodState state(double distance) {
        return DistantWildlifeTransitionPolicy.lodState(distance, 96, 512, 32);
    }

    private static float alpha(double distance) {
        return DistantWildlifeTransitionPolicy.renderAlpha(distance, 96, 512, 32);
    }

    private static boolean canAbstract(double distance, boolean observed, long ticks) {
        return DistantWildlifeTransitionPolicy.canAbstract(distance, observed, ticks, 96, 32, 400L);
    }
}
