package com.thunder.wildernessodysseyapi.watersystem.water.fluid;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Advances disturbed cells in the canonical finite-volume water state.
 *
 * <p>World-generation water is lazily imported as a stable reservoir and does
 * not consume tick budget until gameplay disturbs it. Player water and derived
 * flow use a bounded active queue, conserve fixed-point volume, prefer gravity,
 * and project results back to vanilla blocks for compatibility.</p>
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class WildernessFluidRegistry {

    private static final int MAX_CELLS_PER_TICK = 128;
    private static final float FALL_SPEED = -4.0f;
    private static final float SIDE_FLOW_SPEED = 0.7f;

    private WildernessFluidRegistry() {
    }

    /** Reserved for future custom fluid registrations; runtime hooks use events. */
    public static void register(IEventBus modEventBus) {
    }

    /** Processes a bounded number of disturbed canonical cells after each level tick. */
    @SubscribeEvent
    public static void onServerLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        for (int processed = 0; processed < MAX_CELLS_PER_TICK; processed++) {
            BlockPos pos = CanonicalWater.pollActive(level);
            if (pos == null) {
                break;
            }
            tickCell(level, pos);
        }
    }

    private static void tickCell(ServerLevel level, BlockPos pos) {
        WaterVolumeChunk.WaterCell current = CanonicalWater.getOrImport(level, pos);
        if (current.volumeUnits() <= 0 || current.imported()) {
            return;
        }

        BlockPos below = pos.below();
        if (canOccupy(level, below)) {
            WaterVolumeChunk.WaterCell target = CanonicalWater.getOrImport(level, below);
            int capacity = WaterVolumeChunk.UNITS_PER_BLOCK - target.volumeUnits();
            int transfer = Math.min(current.volumeUnits(), Math.max(0, capacity));
            if (transfer > 0) {
                transfer(level, pos, current, below, target, transfer, 0.0f, FALL_SPEED, 0.0f);
                return;
            }
        }

        Direction bestDirection = null;
        WaterVolumeChunk.WaterCell bestTarget = null;
        int lowestVolume = current.volumeUnits();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbourPos = pos.relative(direction);
            if (!canOccupy(level, neighbourPos)) {
                continue;
            }
            WaterVolumeChunk.WaterCell neighbour = CanonicalWater.getOrImport(level, neighbourPos);
            if (neighbour.volumeUnits() < lowestVolume) {
                lowestVolume = neighbour.volumeUnits();
                bestDirection = direction;
                bestTarget = neighbour;
            }
        }

        if (bestDirection == null || bestTarget == null) {
            return;
        }
        int transfer = Math.min(
                current.volumeUnits(),
                Math.min(
                        WaterVolumeChunk.UNITS_PER_BLOCK - bestTarget.volumeUnits(),
                        Math.max(0, (current.volumeUnits() - bestTarget.volumeUnits()) / 2)
                )
        );
        if (transfer <= 0) {
            return;
        }

        float velocityX = bestDirection.getStepX() * SIDE_FLOW_SPEED;
        float velocityZ = bestDirection.getStepZ() * SIDE_FLOW_SPEED;
        transfer(level, pos, current, pos.relative(bestDirection), bestTarget, transfer,
                velocityX, 0.0f, velocityZ);
    }

    private static void transfer(
            ServerLevel level,
            BlockPos sourcePos,
            WaterVolumeChunk.WaterCell source,
            BlockPos targetPos,
            WaterVolumeChunk.WaterCell target,
            int transfer,
            float velocityX,
            float velocityY,
            float velocityZ
    ) {
        int remaining = source.volumeUnits() - transfer;
        CanonicalWater.set(level, sourcePos, remaining <= 0
                ? WaterVolumeChunk.WaterCell.EMPTY
                : new WaterVolumeChunk.WaterCell(
                        remaining,
                        source.velocityX() * 0.5f,
                        source.velocityY() * 0.5f,
                        source.velocityZ() * 0.5f,
                        WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED,
                        source.temperatureMilliKelvin()
                ), true);
        CanonicalWater.addVolume(level, targetPos, transfer, velocityX, velocityY, velocityZ);
    }

    private static boolean canOccupy(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(Blocks.WATER) || !state.blocksMotion();
    }
}
