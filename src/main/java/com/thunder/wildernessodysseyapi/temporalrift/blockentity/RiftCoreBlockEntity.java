package com.thunder.wildernessodysseyapi.temporalrift.blockentity;

import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RiftCoreBlockEntity extends BlockEntity {
    public RiftCoreBlockEntity(BlockPos pos, BlockState state) {
        super(TemporalRiftBlockEntities.RIFT_CORE.get(), pos, state);
    }
}
