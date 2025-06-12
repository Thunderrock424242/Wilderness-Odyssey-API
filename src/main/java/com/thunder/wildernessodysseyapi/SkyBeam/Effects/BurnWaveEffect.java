package com.thunder.wildernessodysseyapi.SkyBeam.Effects;

import com.thunder.wildernessodysseyapi.SkyBeam.Utilities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class BurnWaveEffect {
    /**
     * @param world    server world
     * @param center   epicenter
     * @param maxRadius how far the fire wave spreads
     * @param steps    how many rings
     * @param delay    ticks between rings
     */
    public static void schedule(ServerLevel world, BlockPos center,
                                int maxRadius, int steps, int delay) {
        for (int i = 1; i <= steps; i++) {
            int radius = (int)((double)maxRadius * i / steps);
            int tickDelay = i * delay;
            Utilities.runLater(tickDelay, () -> applyRing(world, center, radius));
        }
    }

    private static void applyRing(ServerLevel w, BlockPos c, int r) {
        for (BlockPos p : Utilities.squareRing(c, r)) {
            BlockState bs = w.getBlockState(p);
            if (bs.is(Blocks.OAK_LOG) || bs.is(Blocks.BIRCH_LOG)) {
                w.setBlock(p, Blocks.COAL_BLOCK.defaultBlockState(), 3);
            } else if (bs.isAir()) {
                w.setBlock(p, Blocks.FIRE.defaultBlockState(), 3);
            }
        }
    }
}
