package com.thunder.wildernessodysseyapi.worldgen.spawn;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies jungle density and traversal-space decisions without requiring a loaded Minecraft world. */
class StarterIslandJungleDecoratorTest {
    private static final AABB BUNKER = new AABB(100.0D, 60.0D, 100.0D, 121.0D, 80.0D, 121.0D);

    @Test
    void keepsTreeCanopiesAwayFromTheBunkerShell() {
        assertTrue(StarterIslandJungleDecorator.isProtectedPosition(95, 110, BUNKER, 8, true));
        assertFalse(StarterIslandJungleDecorator.isProtectedPosition(90, 110, BUNKER, 8, true));
    }

    @Test
    void preservesTheNegativeZEntranceApproach() {
        assertTrue(StarterIslandJungleDecorator.isProtectedPosition(110, 80, BUNKER, 3, true));
        assertFalse(StarterIslandJungleDecorator.isProtectedPosition(90, 80, BUNKER, 3, true));
    }

    @Test
    void densityCanDisableTreesAndCapsLargeIslands() {
        assertEquals(0, StarterIslandJungleDecorator.targetTreeCount(80, 0.0D));
        assertEquals(96, StarterIslandJungleDecorator.targetTreeCount(400, 1.0D));
    }
}
