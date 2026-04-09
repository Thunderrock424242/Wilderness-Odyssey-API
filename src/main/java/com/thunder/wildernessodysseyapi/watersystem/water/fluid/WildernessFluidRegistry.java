package com.thunder.wildernessodysseyapi.watersystem.water.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.LevelTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * WildernessFluidRegistry
 *
 * Handles server-side finite fluid simulation.
 * Water levels are tracked via vanilla fluid states (0–8).
 * Each tick, unbalanced water blocks attempt to equalize with
 * lower neighbours, simulating realistic gravity-driven flow.
 *
 * Infinite water source behaviour is suppressed by preventing
 * two source blocks from merging into a third (handled in the Mixin).
 */
@EventBusSubscriber(modid = "wilderness", bus = EventBusSubscriber.Bus.GAME)
public class WildernessFluidRegistry {

    // How many fluid blocks are processed per tick (tune for performance)
    private static final int MAX_BLOCKS_PER_TICK = 64;

    public static void register(IEventBus modEventBus) {
        // Particle / fluid type registrations would go here if adding custom fluids.
        // For physics on vanilla water we rely on mixins + game events.
    }

    /**
     * Server tick: scan recently-updated water blocks and equalise levels.
     */
    @SubscribeEvent
    public static void onServerLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        List<BlockPos> waterBlocks = gatherSurfaceWaterBlocks(level, MAX_BLOCKS_PER_TICK);

        for (BlockPos pos : waterBlocks) {
            FluidState fluid = level.getFluidState(pos);
            if (!fluid.is(Fluids.WATER) && !fluid.is(Fluids.FLOWING_WATER)) continue;

            tryEqualize(level, pos, fluid.getAmount());
        }
    }

    /**
     * Collect a sample of loaded water blocks near active chunks.
     */
    private static List<BlockPos> gatherSurfaceWaterBlocks(ServerLevel level, int limit) {
        List<BlockPos> result = new ArrayList<>();
        level.getChunkSource().chunkMap.getChunks().forEach(chunk -> {
            if (result.size() >= limit) return;
            BlockPos center = chunk.getPos().getMiddleBlockPosition(64);
            for (int dx = -8; dx <= 8 && result.size() < limit; dx += 2) {
                for (int dz = -8; dz <= 8 && result.size() < limit; dz += 2) {
                    for (int dy = -4; dy <= 4; dy++) {
                        BlockPos check = center.offset(dx, dy, dz);
                        FluidState fs = level.getFluidState(check);
                        if (fs.is(Fluids.WATER) || fs.is(Fluids.FLOWING_WATER)) {
                            result.add(check);
                            break;
                        }
                    }
                }
            }
        });
        return result;
    }

    /**
     * Attempt to flow water downward or sideways to equalise levels.
     * Water always prefers to fall straight down first.
     */
    private static void tryEqualize(ServerLevel level, BlockPos pos, int currentLevel) {
        // 1. Try to fall directly downward
        BlockPos below = pos.below();
        FluidState belowFluid = level.getFluidState(below);
        BlockState belowBlock = level.getBlockState(below);

        if (belowBlock.isAir() || (belowBlock.is(Blocks.WATER) && belowFluid.getAmount() < 8)) {
            if (canFlowInto(level, below)) {
                flowInto(level, below, Math.min(8, currentLevel));
                reduceLevel(level, pos, currentLevel);
                return;
            }
        }

        // 2. Try to spread sideways to lower neighbours
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbour = pos.relative(dir);
            FluidState neighbourFluid = level.getFluidState(neighbour);
            int neighbourLevel = neighbourFluid.is(Fluids.WATER) || neighbourFluid.is(Fluids.FLOWING_WATER)
                    ? neighbourFluid.getAmount() : 0;

            if (neighbourLevel < currentLevel - 1 && canFlowInto(level, neighbour)) {
                int transfer = (currentLevel - neighbourLevel) / 2;
                if (transfer > 0) {
                    flowInto(level, neighbour, neighbourLevel + transfer);
                    reduceLevel(level, pos, currentLevel - transfer);
                    return;
                }
            }
        }
    }

    private static boolean canFlowInto(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(Blocks.WATER) || !state.blocksMotion();
    }

    private static void flowInto(ServerLevel level, BlockPos pos, int amount) {
        if (amount >= 8) {
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
        } else if (amount > 0) {
            level.setBlock(pos,
                Fluids.FLOWING_WATER.getFlowing(amount, false).createLegacyBlock(), 3);
        }
    }

    private static void reduceLevel(ServerLevel level, BlockPos pos, int currentLevel) {
        int newLevel = currentLevel - 1;
        if (newLevel <= 0) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        } else if (newLevel >= 8) {
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
        } else {
            level.setBlock(pos,
                Fluids.FLOWING_WATER.getFlowing(newLevel, false).createLegacyBlock(), 3);
        }
    }
}
