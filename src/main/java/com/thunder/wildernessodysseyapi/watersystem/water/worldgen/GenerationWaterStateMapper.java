package com.thunder.wildernessodysseyapi.watersystem.water.worldgen;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Maps final standalone water states at generation-only chunk write boundaries.
 *
 * <p>The adapter deliberately does not own terrain, aquifer, carver, or feature
 * decisions. It receives their final block state, replaces exact
 * {@code minecraft:water} with the equivalent Wilderness source/flow state,
 * then records the successful old-to-new write in compact chunk metadata.</p>
 */
public final class GenerationWaterStateMapper {

    private GenerationWaterStateMapper() {
    }

    /**
     * Converts exact standalone vanilla water while preserving amount/falling.
     * Waterlogged hosts, lava, other fluids, and existing custom water are unchanged.
     */
    public static BlockState map(BlockState state) {
        if (state == null || !state.is(Blocks.WATER)) {
            return state;
        }
        FluidState fluid = state.getFluidState();
        if (fluid.isSource()) {
            return WildernessFluidRegistry.WILDERNESS_WATER.get()
                    .defaultFluidState()
                    .createLegacyBlock();
        }
        boolean falling = fluid.hasProperty(FlowingFluid.FALLING)
                && fluid.getValue(FlowingFluid.FALLING);
        return WildernessFluidRegistry.WILDERNESS_WATER.get()
                .getFlowing(Math.max(1, Math.min(8, fluid.getAmount())), falling)
                .createLegacyBlock();
    }

    /** Maps an exact vanilla water fluid used by generation features and scheduled spring ticks. */
    public static FluidState mapFluid(FluidState fluid) {
        if (fluid == null || (!fluid.getType().isSame(Fluids.WATER)
                && !fluid.getType().isSame(Fluids.FLOWING_WATER))) {
            return fluid;
        }
        if (fluid.isSource()) {
            return WildernessFluidRegistry.WILDERNESS_WATER.get().defaultFluidState();
        }
        boolean falling = fluid.hasProperty(FlowingFluid.FALLING)
                && fluid.getValue(FlowingFluid.FALLING);
        return WildernessFluidRegistry.WILDERNESS_WATER.get().getFlowing(
                Math.max(1, Math.min(8, fluid.getAmount())),
                falling
        );
    }

    /**
     * Updates generated metadata after a mapped state has actually been stored.
     * This method never writes blocks and therefore cannot recurse into a mixin.
     */
    public static void recordStoredState(ChunkAccess chunk, BlockPos pos, BlockState storedState) {
        if (!isWildernessWater(storedState)) {
            chunk.getExistingData(ModAttachments.GENERATED_WATER)
                    .ifPresent(generated -> {
                        generated.recordCell(pos, null);
                        generated.recordSurfaceCover(pos, storedState != null && !storedState.isAir());
                    });
            return;
        }
        GeneratedWaterChunk generated = chunk.getData(ModAttachments.GENERATED_WATER);
        FluidState fluid = storedState.getFluidState();
        boolean falling = fluid.hasProperty(FlowingFluid.FALLING)
                && fluid.getValue(FlowingFluid.FALLING);
        Classification classification = classify(chunk, pos);
        generated.recordCell(pos, GeneratedWaterChunk.Cell.of(
                Math.max(1, Math.min(8, fluid.getAmount())),
                falling,
                classification.bodyType(),
                classification.waterTint()
        ));
    }

    /** Returns whether the stored block is the namespaced source or flowing Wilderness fluid. */
    public static boolean isWildernessWater(BlockState state) {
        if (state == null) {
            return false;
        }
        FluidState fluid = state.getFluidState();
        return state.is(WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get())
                || fluid.getType().isSame(WildernessFluidRegistry.WILDERNESS_WATER.get())
                || fluid.getType().isSame(WildernessFluidRegistry.FLOWING_WILDERNESS_WATER.get());
    }

    private static Classification classify(ChunkAccess chunk, BlockPos pos) {
        try {
            var biome = chunk.getNoiseBiome(
                    QuartPos.fromBlock(pos.getX()),
                    QuartPos.fromBlock(pos.getY()),
                    QuartPos.fromBlock(pos.getZ())
            );
            if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)
                    || biome.is(BiomeTags.IS_BEACH)) {
                return new Classification(GeneratedWaterChunk.BodyType.OCEAN,
                        biome.value().getWaterColor());
            }
            if (biome.is(BiomeTags.IS_RIVER)) {
                return new Classification(GeneratedWaterChunk.BodyType.RIVER,
                        biome.value().getWaterColor());
            }
            return new Classification(
                    pos.getY() < chunk.getMinBuildHeight() + 48
                            ? GeneratedWaterChunk.BodyType.AQUIFER
                            : GeneratedWaterChunk.BodyType.LAKE,
                    biome.value().getWaterColor()
            );
        } catch (IllegalStateException biomesNotReady) {
            // Flat/custom generators may write a configured layer before a
            // standalone ProtoChunk test has installed biomes. Classification
            // is optical metadata only and must never block exact state mapping.
        }
        // Buried generated water is treated as an aquifer optical profile; the
        // generator's original density and fluid-level decisions remain intact.
        if (pos.getY() < chunk.getMinBuildHeight() + 48) {
            return new Classification(GeneratedWaterChunk.BodyType.AQUIFER,
                    GeneratedWaterChunk.Cell.DEFAULT_WATER_TINT);
        }
        return new Classification(GeneratedWaterChunk.BodyType.LAKE,
                GeneratedWaterChunk.Cell.DEFAULT_WATER_TINT);
    }

    private record Classification(GeneratedWaterChunk.BodyType bodyType, int waterTint) {
    }
}
