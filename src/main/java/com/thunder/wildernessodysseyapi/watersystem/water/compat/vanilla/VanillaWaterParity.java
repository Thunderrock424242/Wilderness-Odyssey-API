package com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla;

import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Narrow translations for vanilla code that compares exact water identities.
 *
 * <p>This class intentionally does not spoof registry identity or make every
 * tagged fluid equal vanilla water. Each caller opts into one exact comparison,
 * while normal block/fluid behavior and water-tag fallbacks remain unchanged.</p>
 */
public final class VanillaWaterParity {

    private VanillaWaterParity() {
    }

    /** Extends an exact {@link Blocks#WATER} request to standalone Wilderness water. */
    public static boolean matchesRequestedWaterBlock(BlockState state, Block requestedBlock) {
        if (requestedBlock != Blocks.WATER) {
            return state.is(requestedBlock);
        }
        return state.is(Blocks.WATER) || isStandaloneWildernessWater(state);
    }

    /** Extends an exact vanilla-water fluid request to either Wilderness fluid state. */
    public static boolean matchesRequestedWaterFluid(FluidState state, Fluid requestedFluid) {
        if (requestedFluid != Fluids.WATER) {
            return state.is(requestedFluid);
        }
        return state.is(Fluids.WATER) || isWildernessWaterFluid(state);
    }

    /** Returns whether a full standalone source can host a vanilla bubble column. */
    public static boolean isFullBubbleColumnWater(BlockState state) {
        FluidState fluidState = state.getFluidState();
        return (state.is(Blocks.WATER) || isStandaloneWildernessWater(state))
                && fluidState.isSource()
                && fluidState.getAmount() >= 8;
    }

    /** Applies the opt-in fishing flag to the two remaining exact visual checks. */
    public static boolean matchesFishingEffectWater(BlockState state, Block requestedBlock) {
        if (state.is(requestedBlock)) {
            return true;
        }
        return requestedBlock == Blocks.WATER
                && WaterSimulationConfig.fishingCompatEnabled()
                && isStandaloneWildernessWater(state);
    }

    /** Returns whether the state is the standalone namespaced water block. */
    public static boolean isStandaloneWildernessWater(BlockState state) {
        return state.is(WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get());
    }

    /** Returns whether a state contains either namespaced Wilderness fluid. */
    public static boolean isWildernessWaterFluid(FluidState state) {
        Fluid fluid = state.getType();
        return fluid.isSame(WildernessFluidRegistry.WILDERNESS_WATER.get())
                || fluid.isSame(WildernessFluidRegistry.FLOWING_WILDERNESS_WATER.get());
    }
}
