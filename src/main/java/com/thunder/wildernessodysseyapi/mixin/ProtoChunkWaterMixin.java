package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.GenerationWaterStateMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Maps feature, carver, flat-world, and WorldGenRegion water writes before a
 * ProtoChunk stores them. Runtime LevelChunk writes are intentionally outside
 * this mixin.
 */
@Mixin(ProtoChunk.class)
public abstract class ProtoChunkWaterMixin {

    /** Replaces only the BlockState argument at the generation write boundary. */
    @ModifyVariable(method = "setBlockState", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockState wildernessOdyssey$mapGeneratedWater(BlockState state) {
        return GenerationWaterStateMapper.map(state);
    }

    /** Records the final stored state, including later dry overwrites, without another write. */
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void wildernessOdyssey$recordGeneratedWater(
            BlockPos pos,
            BlockState state,
            boolean moved,
            CallbackInfoReturnable<BlockState> callback
    ) {
        ProtoChunk chunk = (ProtoChunk) (Object) this;
        GenerationWaterStateMapper.recordStoredState(chunk, pos, chunk.getBlockState(pos));
    }
}
