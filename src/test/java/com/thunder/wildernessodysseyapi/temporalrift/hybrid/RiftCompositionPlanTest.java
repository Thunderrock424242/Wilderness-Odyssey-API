package com.thunder.wildernessodysseyapi.temporalrift.hybrid;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RiftCompositionPlanTest {
    private static final List<RiftCompositionPlan.Variant> VARIANTS = List.of(
            new RiftCompositionPlan.Variant("small", 3, 3, 3),
            new RiftCompositionPlan.Variant("platform", 5, 2, 4));

    @Test
    void sameSeedHasSameCompositionAndDifferentSeedsVaryIt() {
        var plan = RiftCompositionPlan.plan(42, 24, 8, 1, VARIANTS);
        assertFalse(plan.isEmpty());
        assertEquals(plan, RiftCompositionPlan.plan(42, 24, 8, 1, VARIANTS));
        assertNotEquals(plan, RiftCompositionPlan.plan(43, 24, 8, 1, VARIANTS));
    }

    @Test
    void footprintsStayInsideConfiguredDiameterAndClearTheRing() {
        for (int seed = 0; seed < 80; seed++) {
            var plan = RiftCompositionPlan.plan(seed, 24, 12, 1, VARIANTS);
            assertTrue(plan.size() <= 12);
            for (int i = 0; i < plan.size(); i++) {
                var box = plan.get(i).box();
                assertTrue(box.minX() >= -12 && box.maxX() <= 12);
                assertTrue(box.minZ() >= -12 && box.maxZ() <= 12);
                assertFalse(box.overlaps(new RiftCompositionPlan.Box(-3, -1, -3, 3, 6, 3), 1));
                for (int j = 0; j < i; j++) {
                    assertFalse(box.overlaps(plan.get(j).box(), 1));
                }
            }
        }
    }

    @Test
    void invalidOrOversizedVariantsCannotCreateUnboundedPlacements() {
        assertTrue(RiftCompositionPlan.plan(1, 20, 8, 1, List.of()).isEmpty());
        assertTrue(RiftCompositionPlan.plan(1, 20, 8, 1,
                List.of(new RiftCompositionPlan.Variant("giant", 200, 200, 200))).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new RiftCompositionPlan.Variant("bad", 0, 2, 2));
    }
}
