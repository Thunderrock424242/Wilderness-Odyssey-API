package com.thunder.wildernessodysseyapi.WorldGen.blocks;

import com.thunder.wildernessodysseyapi.WorldGen.spawn.CryoSpawnData;
import com.thunder.wildernessodysseyapi.WorldGen.spawn.WorldSpawnHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity for the cryo tube.
 */
public class CryoTubeBlockEntity extends BlockEntity {
    public CryoTubeBlockEntity(BlockPos pos, BlockState state) {
        super(CryoTubeBlock.CRYO_TUBE_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel serverLevel) {
            CryoSpawnData data = CryoSpawnData.get(serverLevel);
            if (data.add(this.worldPosition)) {
                WorldSpawnHandler.refreshWorldSpawn(serverLevel);
            }
        }
    }
}
