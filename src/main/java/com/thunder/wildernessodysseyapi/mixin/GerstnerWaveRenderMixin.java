package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerVertexConsumer;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterRenderingConfig;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * GerstnerWaveRenderMixin
 * <p>
 * Wraps the consumer passed to {@code LiquidBlockRenderer#tesselate} so water
 * geometry receives the mod's Gerstner displacement and analytic normals.
 * <p>
 * A narrow mixin is necessary because NeoForge has no event for transforming
 * vertices while vanilla builds a liquid chunk mesh. Wave time is advanced by
 * the client tick handler; chunk compilation may run on worker threads and must
 * never mutate global animation state.
 */
@Mixin(LiquidBlockRenderer.class)
public class GerstnerWaveRenderMixin {

    @ModifyVariable(
        method = "tesselate",
        at = @At("HEAD"),
        argsOnly = true,
        index = 3,
        require = 0
    )
    private VertexConsumer wrapWaterVertexConsumer(VertexConsumer originalConsumer,
                                                   BlockAndTintGetter level,
                                                   BlockPos pos,
                                                   VertexConsumer consumer,
                                                   BlockState blockState,
                                                   FluidState fluidState) {
        if (!fluidState.is(Fluids.WATER) && !fluidState.is(Fluids.FLOWING_WATER)) {
            return originalConsumer;
        }
        if (!WaterRenderingConfig.ENABLE_GERSTNER_WAVES.get()) {
            return originalConsumer;
        }

        WaterBodyClassifier.WaterType waterType = classifyWater(level, pos);
        return new GerstnerVertexConsumer(
                originalConsumer,
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                waterType
        );
    }

    private static WaterBodyClassifier.WaterType classifyWater(BlockAndTintGetter level, BlockPos pos) {
        if (level instanceof LevelReader levelReader) {
            return WaterBodyClassifier.classify(levelReader, pos);
        }

        if (level instanceof RenderChunkRegion renderChunkRegion) {
            Level clientLevel = ((RenderChunkRegionAccessor) renderChunkRegion).wildernessodysseyapi$getLevel();
            return WaterBodyClassifier.classify(clientLevel, pos);
        }

        return WaterBodyClassifier.WaterType.POND;
    }
}
