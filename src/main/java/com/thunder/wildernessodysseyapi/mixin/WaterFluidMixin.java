package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thunder.wildernessodysseyapi.ocean.rendering.WaveRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The type Water fluid mixin.
 */
@Mixin(WaterFluid.class)
public abstract class WaterFluidMixin {

    /**
     * Inject into the `animateTick` method to add foam and wave rendering logic.
     */
    @Inject(method = "animateTick", at = @At("HEAD"))
    private void addFoamAndWaveEffects(Level level, BlockPos pos, FluidState state, net.minecraft.util.RandomSource random, CallbackInfo ci) {
        // Ensure this logic only runs on the client
        if (!level.isClientSide) {
            return;
        }

        // Render foam and waves
        PoseStack poseStack = new PoseStack();
        float partialTicks = level.getGameTime() % 20 / 20.0F; // Simulate partial ticks
        WaveRenderer.renderFoamAndWaves(poseStack, partialTicks, 0xF000F0);
    }
}

// this corresponds to the ocean package.