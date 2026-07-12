package com.thunder.wildernessodysseyapi.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.GenerationWaterStateMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Maps the single direct section write used by noise terrain and aquifers.
 *
 * <p>{@code NoiseBasedChunkGenerator#doFill} bypasses
 * {@code ProtoChunk#setBlockState}; wrapping its final section write preserves
 * every vanilla density/aquifer calculation while changing only the state that
 * is stored.</p>
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorWaterMixin {

    /** Executes the original section write exactly once, then records its final state. */
    @WrapOperation(
            method = "doFill",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
                    ordinal = 0
            ),
            require = 1
    )
    private BlockState wildernessOdyssey$mapNoiseWater(
            LevelChunkSection section,
            int localX,
            int localY,
            int localZ,
            BlockState state,
            boolean useLocks,
            Operation<BlockState> original,
            @Local(argsOnly = true, ordinal = 0) ChunkAccess chunk,
            @Local(index = 25) int absoluteY
    ) {
        BlockState mapped = GenerationWaterStateMapper.map(state);
        BlockState previous = original.call(section, localX, localY, localZ, mapped, useLocks);
        BlockPos worldPos = new BlockPos(
                chunk.getPos().getMinBlockX() + localX,
                absoluteY,
                chunk.getPos().getMinBlockZ() + localZ
        );
        GenerationWaterStateMapper.recordStoredState(chunk, worldPos, mapped);
        return previous;
    }
}
