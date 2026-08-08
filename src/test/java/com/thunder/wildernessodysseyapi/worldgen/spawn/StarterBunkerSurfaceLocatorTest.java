package com.thunder.wildernessodysseyapi.worldgen.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the landscape footprint distinguishes the visible bunker shell from generated island materials. */
class StarterBunkerSurfaceLocatorTest {

    @Test
    void recognizesStructuralBlocksButIgnoresLandscaping() {
        assertTrue(StarterBunkerSurfaceLocator.isSurfaceStructureBlock(Blocks.STONE_BRICKS.defaultBlockState()));
        assertTrue(StarterBunkerSurfaceLocator.isSurfaceStructureBlock(Blocks.IRON_BARS.defaultBlockState()));
        assertFalse(StarterBunkerSurfaceLocator.isSurfaceStructureBlock(Blocks.GRASS_BLOCK.defaultBlockState()));
        assertFalse(StarterBunkerSurfaceLocator.isSurfaceStructureBlock(Blocks.DIRT_PATH.defaultBlockState()));
        assertFalse(StarterBunkerSurfaceLocator.isSurfaceStructureBlock(Blocks.WATER.defaultBlockState()));
    }

    @Test
    void fallbackStaysCompactAroundTheSurfaceMarker() {
        AABB bounds = StarterBunkerSurfaceLocator.fallbackBounds(new BlockPos(100, 65, -40));

        assertEquals(86.0D, bounds.minX);
        assertEquals(115.0D, bounds.maxX);
        assertEquals(-54.0D, bounds.minZ);
        assertEquals(-25.0D, bounds.maxZ);
    }
}
