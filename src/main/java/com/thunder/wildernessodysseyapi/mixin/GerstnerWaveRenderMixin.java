package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.render.OceanSurfaceRenderer;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterRenderingConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterCompatibility;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerVertexConsumer;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GerstnerWaveRenderMixin
 * <p>
 * Wraps the consumer passed to {@code LiquidBlockRenderer#tesselate} when the
 * static compatibility mesh is allowed to receive fallback Gerstner motion.
 * <p>
 * A narrow mixin is necessary because NeoForge has no event for transforming
 * vertices while vanilla builds a liquid chunk mesh. Wave time is advanced by
 * the client tick handler; chunk compilation may run on worker threads and must
 * never mutate global animation state. When the dynamic ocean surface is
 * enabled, baked vanilla water stays stable and acts as the compatibility mask
 * beneath the per-frame replacement pass. Safe replacement cells can hide the
 * baked vanilla top so the player does not see two water surfaces blended over
 * each other.
 */
@Mixin(LiquidBlockRenderer.class)
public class GerstnerWaveRenderMixin {

    // Liquid chunk compilation runs on worker threads. A ThreadLocal carries
    // top-face ownership to the redirect without sharing mutable positions.
    private static final ThreadLocal<Boolean> HIDE_VANILLA_TOP =
            ThreadLocal.withInitial(() -> false);

    @Inject(method = "tesselate", at = @At("HEAD"))
    private void wildernessOdysseyApi$captureTopOwnership(
            BlockAndTintGetter level,
            BlockPos pos,
            VertexConsumer buffer,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo callbackInfo
    ) {
        HIDE_VANILLA_TOP.set(
                WaterCompatibility.isTaggedWater(fluidState)
                        && WaterRenderingConfig.ENABLE_GERSTNER_WAVES.get()
                        && WaterRenderingConfig.ENABLE_DYNAMIC_OCEAN_SURFACE.get()
                        && WaterRenderingConfig.suppressVanillaWaterTopFaces()
                        && OceanSurfaceRenderer.ownsBakedTop(pos)
        );
    }

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
        if (!WaterCompatibility.isTaggedWater(fluidState)) {
            return originalConsumer;
        }
        if (!WaterRenderingConfig.ENABLE_GERSTNER_WAVES.get()) {
            return originalConsumer;
        }

        LevelReader waterLevel = resolveLevelReader(level);
        WaterBodyClassifier.WaterType waterType = waterLevel == null
                ? WaterBodyClassifier.WaterType.POND
                : WaterBodyClassifier.classify(waterLevel, pos);
        boolean dynamicSurfaceEnabled = WaterRenderingConfig.ENABLE_DYNAMIC_OCEAN_SURFACE.get();
        boolean suppressSurfaceDisplacement = dynamicSurfaceEnabled
                || !isExposedWaterTop(level, pos, fluidState);
        return new GerstnerVertexConsumer(
                originalConsumer,
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                waterType,
                suppressSurfaceDisplacement
        );
    }

    /**
     * Hides the vanilla top only when replacement ownership contains the same
     * block. Vanilla side faces and all out-of-range water remain intact for
     * compatibility and visual fallback.
     */
    @Redirect(
            method = "tesselate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;"
                            + "isNeighborStateHidingOverlay("
                            + "Lnet/minecraft/world/level/material/FluidState;"
                            + "Lnet/minecraft/world/level/block/state/BlockState;"
                            + "Lnet/minecraft/core/Direction;)Z",
                    ordinal = 0
            )
    )
    private boolean wildernessOdysseyApi$hideReplacedVanillaTop(
            FluidState selfState,
            BlockState aboveState,
            Direction neighborFace
    ) {
        if (HIDE_VANILLA_TOP.get()) {
            return true;
        }
        return aboveState.shouldHideAdjacentFluidFace(neighborFace, selfState);
    }

    @Inject(method = "tesselate", at = @At("TAIL"))
    private void wildernessOdysseyApi$clearTopOwnership(
            BlockAndTintGetter level,
            BlockPos pos,
            VertexConsumer buffer,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo callbackInfo
    ) {
        HIDE_VANILLA_TOP.set(false);
    }

    private static LevelReader resolveLevelReader(BlockAndTintGetter level) {
        if (level instanceof LevelReader levelReader) {
            return levelReader;
        }

        if (level instanceof RenderChunkRegion renderChunkRegion) {
            Level clientLevel = ((RenderChunkRegionAccessor) renderChunkRegion).wildernessodysseyapi$getLevel();
            return clientLevel;
        }

        return null;
    }

    private static boolean isExposedWaterTop(
            BlockAndTintGetter level,
            BlockPos pos,
            FluidState fluidState
    ) {
        BlockPos abovePos = pos.above();
        BlockState aboveState = level.getBlockState(abovePos);
        FluidState aboveFluid = aboveState.getFluidState();
        if (WaterCompatibility.isTaggedWater(aboveFluid)) {
            return false;
        }
        if (aboveState.shouldHideAdjacentFluidFace(Direction.DOWN, fluidState)) {
            return false;
        }
        return aboveState.getCollisionShape(level, abovePos).isEmpty();
    }
}
