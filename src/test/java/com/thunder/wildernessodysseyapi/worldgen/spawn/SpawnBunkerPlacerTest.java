package com.thunder.wildernessodysseyapi.worldgen.spawn;

import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the generated starter island uses a living topsoil and a distinct beach foundation. */
class SpawnBunkerPlacerTest {

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
