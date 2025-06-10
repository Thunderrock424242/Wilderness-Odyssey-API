package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thunder.wildernessodysseyapi.ocean.events.WaterSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderDispatcher.class)
public abstract class BlockRenderDispatcherMixin {
    @Inject(
            method = "renderLiquid",
            at = @At("TAIL")
    )
    private void onRenderLiquid(
            BlockPos pos,
            BlockAndTintGetter view,
            VertexConsumer consumer,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo ci
    ) {
        // 1) foamAlpha from your WaterSystem
        float foamAlpha = (float) Math.max(0.0, Math.min(1.0, WaterSystem.getWaveHeightAt(pos.getX(), pos.getZ())));

        // 2) packed light
        int rawLight = Minecraft.getInstance()
                .level
                .getLightEngine()
                .getRawBrightness(pos, 0);
        int lightU = rawLight & 0xFFFF;
        int lightV = (rawLight >>> 16) & 0xFFFF;

        // 3) pack color
        int packedColor = FastColor.ARGB32.color((int)(255 * foamAlpha), 255, 255, 255);

        // 4) emit your foam quad
        consumer.addVertex(/* matrix, */ -1F, 0F, -1F)
                .setColor(packedColor)
                .setUv(0F, 0F)
                .setUv2(lightU, lightV);
        consumer.addVertex(/* matrix, */  1F, 0F, -1F)
                .setColor(packedColor)
                .setUv(1F, 0F)
                .setUv2(lightU, lightV);
        consumer.addVertex(/* matrix, */  1F, 0F,  1F)
                .setColor(packedColor)
                .setUv(1F, 1F)
                .setUv2(lightU, lightV);
        consumer.addVertex(/* matrix, */ -1F, 0F,  1F)
                .setColor(packedColor)
                .setUv(0F, 1F)
                .setUv2(lightU, lightV);
    }
}
