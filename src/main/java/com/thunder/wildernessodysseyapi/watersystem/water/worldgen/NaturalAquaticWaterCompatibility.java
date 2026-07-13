package com.thunder.wildernessodysseyapi.watersystem.water.worldgen;

import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Narrow compatibility boundary for vanilla natural aquatic generation.
 *
 * <p>Several vanilla flora features and aquatic spawn predicates compare a
 * block directly with {@link Blocks#WATER} instead of using
 * {@link FluidTags#WATER}. Those exact checks run after world generation has
 * already stored the namespaced Wilderness fluid, so otherwise-valid kelp,
 * seagrass, coral, sea pickles, and surface water animals are rejected.</p>
 *
 * <p>This helper deliberately recognizes only standalone vanilla or Wilderness
 * water blocks. It does not make arbitrary waterlogged hosts pass a standalone
 * water predicate.</p>
 */
public final class NaturalAquaticWaterCompatibility {

    private NaturalAquaticWaterCompatibility() {
    }

    /**
     * Applies vanilla block identity semantics except for an exact water request.
     *
     * @param state block being tested by the vanilla feature or spawn rule
     * @param requestedBlock block requested by vanilla's original predicate
     * @return the original identity result, extended only for standalone Wilderness water
     */
    public static boolean matchesRequestedBlock(BlockState state, Block requestedBlock) {
        if (requestedBlock != Blocks.WATER) {
            return state.is(requestedBlock);
        }
        return isStandaloneNaturalWater(state);
    }

    /** Returns whether a state is an exact standalone vanilla or Wilderness water block. */
    public static boolean isStandaloneNaturalWater(BlockState state) {
        return state.is(Blocks.WATER)
                || state.is(WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get());
    }

    /**
     * Returns whether a generated flora state still hosts the water cell it replaced.
     *
     * <p>This list is intentionally limited to states emitted by the vanilla
     * kelp, seagrass, sea-pickle, and coral features. Fences, slabs, and other
     * general waterlogged blocks remain outside this compatibility phase.</p>
     */
    public static boolean isNaturalAquaticFloraHost(BlockState state) {
        if (!state.getFluidState().is(FluidTags.WATER)) {
            return false;
        }
        return state.is(Blocks.KELP)
                || state.is(Blocks.KELP_PLANT)
                || state.is(Blocks.SEAGRASS)
                || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.SEA_PICKLE)
                || state.is(BlockTags.CORALS)
                || state.is(BlockTags.WALL_CORALS);
    }
}
