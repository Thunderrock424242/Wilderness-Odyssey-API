package com.thunder.wildernessodysseyapi.WorldGen.structure;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Defines which existing blocks get cleared to air when the starter bunker is placed.
 */
public final class BunkerTerrainClearer {
    private BunkerTerrainClearer() {
    }

    /**
     * Clears any non-air block (including fluids) inside the bunker footprint.
     */
    public static boolean shouldClear(BlockState state) {
        return !state.isAir() && !state.is(Blocks.STRUCTURE_VOID);
    }
}
