package com.thunder.wildernessodysseyapi.ocean;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

public class CustomWaterBlock extends LiquidBlock {
    public CustomWaterBlock() {
        super(Fluids.WATER, Properties.of(Material.WATER).noCollission().strength(100.0F).noDrops());
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, java.util.Random random) {
        super.animateTick(state, world, pos, (RandomSource) random);

        // Add foam particles near shores or wave motion effects
        if (random.nextDouble() < 0.1) {
            world.addParticle(ParticleTypes.SPLASH,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    0.0, 0.1, 0.0);
        }
    }
}
