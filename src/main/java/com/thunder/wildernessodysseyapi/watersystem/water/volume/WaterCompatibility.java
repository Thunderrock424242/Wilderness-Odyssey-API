package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Read-only compatibility view of replacement water at a block position.
 *
 * <p>The replacement system now owns a namespaced water fluid and relies on
 * {@code #minecraft:water} for broad compatibility. This helper keeps that
 * rule in one place so gameplay hooks, render sampling, and diagnostics do not
 * drift back toward hardcoded {@code Blocks.WATER} checks.</p>
 */
public final class WaterCompatibility {

    private WaterCompatibility() {
    }

    /** Returns whether vanilla or replacement-owned water occupies this block. */
    public static boolean isWater(Level level, BlockPos pos) {
        return describe(level, pos).wet();
    }

    /** Returns whether this fluid state is considered water by the compatibility tag. */
    public static boolean isTaggedWater(FluidState state) {
        return state.is(FluidTags.WATER);
    }

    /** Returns whether this fluid is one of the bucket/canonical water fluids this mod owns directly. */
    public static boolean isCanonicalWaterFluid(Fluid fluid) {
        return fluid == Fluids.WATER
                || fluid == Fluids.FLOWING_WATER
                || fluid.isSame(WildernessFluidRegistry.WILDERNESS_WATER.get())
                || fluid.isSame(WildernessFluidRegistry.FLOWING_WILDERNESS_WATER.get());
    }

    /** Returns whether a block state is a plain water projection, not a waterlogged host. */
    public static boolean isPlainWaterProjection(BlockState state) {
        return state.is(Blocks.WATER) || state.is(WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get());
    }

    /** Returns whether the block's own fluid state participates in the water compatibility tag. */
    public static boolean hasTaggedWater(BlockGetter level, BlockPos pos) {
        return isTaggedWater(level.getFluidState(pos));
    }

    /** Builds a bounded diagnostic snapshot for one block. */
    public static Snapshot describe(Level level, BlockPos pos) {
        WaterVolumeChunk.WaterCell canonical = CanonicalWater.get(level, pos);
        boolean tracked = CanonicalWater.isTracked(level, pos);
        BlockState blockState = level.getBlockState(pos);
        boolean tagWater = isTaggedWater(blockState.getFluidState());
        boolean vanillaWaterBlock = blockState.is(Blocks.WATER);
        boolean wildernessWaterBlock = blockState.is(WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get());
        boolean plainProjectionBlock = isPlainWaterProjection(blockState);
        SPHSimulationManager.MobileWaterSample mobile = SPHSimulationManager.get().sampleAt(
                level,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
        );

        return new Snapshot(
                tracked,
                canonical.volumeUnits(),
                canonical.fillFraction(),
                canonical.velocityX(),
                canonical.velocityY(),
                canonical.velocityZ(),
                canonical.imported(),
                (canonical.flags() & WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED) != 0,
                tagWater,
                vanillaWaterBlock,
                wildernessWaterBlock,
                plainProjectionBlock,
                mobile.wet(),
                mobile.velocityX(),
                mobile.velocityY(),
                mobile.velocityZ()
        );
    }

    /** Immutable replacement/vanilla water state used by commands and diagnostics. */
    public record Snapshot(
            boolean canonicalTracked,
            int canonicalVolumeUnits,
            float fillFraction,
            float velocityX,
            float velocityY,
            float velocityZ,
            boolean imported,
            boolean compatibilityProjected,
            boolean tagWater,
            boolean vanillaWaterBlock,
            boolean wildernessWaterBlock,
            boolean plainProjectionBlock,
            boolean mobileWater,
            float mobileVelocityX,
            float mobileVelocityY,
            float mobileVelocityZ
    ) {
        /** Returns whether any vanilla or replacement water exists here. */
        public boolean wet() {
            return canonicalVolumeUnits > 0 || tagWater || mobileWater;
        }

        /** Returns a compact velocity magnitude for debug output. */
        public float canonicalSpeed() {
            return (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
        }

        /** Returns a compact mobile-water velocity magnitude for debug output. */
        public float mobileSpeed() {
            return (float) Math.sqrt(
                    mobileVelocityX * mobileVelocityX
                            + mobileVelocityY * mobileVelocityY
                            + mobileVelocityZ * mobileVelocityZ
            );
        }
    }
}
