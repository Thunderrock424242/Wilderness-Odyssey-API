package com.thunder.wildernessodysseyapi.WorldGen.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity for the cryo tube.
 */
public class CryoTubeBlockEntity extends BlockEntity {
    public CryoTubeBlockEntity(BlockPos pos, BlockState state) {
        super(CryoTubeBlock.CRYO_TUBE_ENTITY.get(), pos, state);
    }
}
