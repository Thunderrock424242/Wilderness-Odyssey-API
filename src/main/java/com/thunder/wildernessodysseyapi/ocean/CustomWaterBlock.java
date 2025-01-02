package com.thunder.wildernessodysseyapi.ocean;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class CustomWaterBlock extends Block {
    public CustomWaterBlock() {
        super(BlockBehaviour.Properties.of(Material.WATER).noOcclusion());
    }

    private Iterable<BlockPos> getWaveAffectedPositions(Level world) {
        return world.players().stream()
                .flatMap(player -> BlockPos.betweenClosedStream(
                        player.blockPosition().offset(-64, -16, -64),
                        player.blockPosition().offset(64, 16, 64))
                )
                .filter(pos -> world.getBlockState(pos).getBlock() instanceof CustomWaterBlock)
                .toList();
    }

}