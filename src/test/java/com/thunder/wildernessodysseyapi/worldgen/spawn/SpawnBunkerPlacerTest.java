package com.thunder.wildernessodysseyapi.worldgen.spawn;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Verifies the generated starter island uses a living topsoil and a distinct beach foundation. */
class SpawnBunkerPlacerTest {

    @Test
    void boundsGenerationToTheSelectedStarterIslandAndBuildHeight() {
        var bounds = SpawnBunkerPlacer.starterTerrainBounds(new BlockPos(-200, 10, 300), new Vec3i(157, 76, 182), -64, 320);
        assertTrue(bounds.isInside(new BlockPos(-291, -64, 222)));
        assertTrue(bounds.isInside(new BlockPos(47, 319, 560)));
        assertFalse(bounds.isInside(new BlockPos(48, 100, 560)));
        assertFalse(bounds.isInside(new BlockPos(-291, 320, 222)));
    }

    @Test
    void usesGrassAcrossTheRaisedBunkerPlatform() {
        assertTrue(SpawnBunkerPlacer.selectIslandBlock(20.0D, 60, 100, 64, 64, 63)
                .is(Blocks.GRASS_BLOCK));
    }

    @Test
    void usesDirtBelowTheGrassPlatform() {
        assertTrue(SpawnBunkerPlacer.selectIslandBlock(20.0D, 60, 100, 64, 62, 63)
                .is(Blocks.DIRT));
    }

    @Test
    void usesSandAndSandstoneOnTheSlopedShore() {
        assertTrue(SpawnBunkerPlacer.selectIslandBlock(75.0D, 60, 100, 64, 64, 63)
                .is(Blocks.SAND));
        assertTrue(SpawnBunkerPlacer.selectIslandBlock(75.0D, 60, 100, 64, 62, 63)
                .is(Blocks.SANDSTONE));
    }
}
