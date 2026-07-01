package com.thunder.wildernessodysseyapi.watersystem.water.fluid;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    private static final int MAX_CELLS_PER_TICK = 192;
    private static final int MIN_FLOW_UNITS = WaterVolumeChunk.UNITS_PER_BLOCK / 64;
    private static final int MIN_LATERAL_DIFFERENCE_UNITS = WaterVolumeChunk.UNITS_PER_BLOCK / 16;
    private static final int MAX_VERTICAL_TRANSFER_UNITS = WaterVolumeChunk.UNITS_PER_BLOCK * 3 / 4;
    private static final int MAX_LATERAL_TRANSFER_UNITS = WaterVolumeChunk.UNITS_PER_BLOCK / 4;
    private static final int MOBILE_POUR_MIN_UNITS = WaterVolumeChunk.UNITS_PER_BLOCK / 4;
    private static final int MOBILE_POUR_MAX_UNITS = WaterVolumeChunk.UNITS_PER_BLOCK / 2;
    private static final float FALL_SPEED = -4.8f;
    private static final float SIDE_FLOW_SPEED = 0.85f;
    private static final float SOURCE_VELOCITY_DAMPING = 0.62f;
    private static final float REST_SPEED = 0.03f;

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

        int remaining = current.volumeUnits();
        boolean moved = false;

        // Gravity gets first claim on unsettled volume. A falling sheet can
        // either fill canonical capacity below or hand high-energy water to SPH
        // so waterfalls/pours are rendered as mobile water before settling.
        BlockPos below = pos.below();
        if (canOccupy(level, below)) {
            WaterVolumeChunk.WaterCell target = CanonicalWater.getOrImport(level, below);
            int capacity = WaterVolumeChunk.UNITS_PER_BLOCK - target.volumeUnits();
            int transfer = Math.min(remaining, Math.min(Math.max(0, capacity), MAX_VERTICAL_TRANSFER_UNITS));
            if (transfer > 0) {
                int mobileTransfer = maybeCreateMobilePour(level, pos, current, target, transfer);
                if (mobileTransfer > 0) {
                    remaining -= mobileTransfer;
                    moved = true;
                } else {
                    int accepted = addTargetVolume(
                            level,
                            below,
                            transfer,
                            current.velocityX() * 0.45f,
                            Math.min(FALL_SPEED, current.velocityY() - 1.2f),
                            current.velocityZ() * 0.45f
                    );
                    remaining -= accepted;
                    moved |= accepted > 0;
                }
            }
        }

        if (remaining > MIN_FLOW_UNITS) {
            int lateralMoved = flowSideways(level, pos, current, remaining);
            remaining -= lateralMoved;
            moved |= lateralMoved > 0;
        }

        if (moved) {
            commitSource(level, pos, current, remaining);
        } else if (speedSquared(current) > REST_SPEED * REST_SPEED) {
            // A disturbed cell that cannot currently move should calm down
            // instead of carrying stale velocity forever.
            CanonicalWater.set(level, pos, new WaterVolumeChunk.WaterCell(
                    current.volumeUnits(),
                    current.velocityX() * SOURCE_VELOCITY_DAMPING,
                    current.velocityY() * SOURCE_VELOCITY_DAMPING,
                    current.velocityZ() * SOURCE_VELOCITY_DAMPING,
                    WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED,
                    current.temperatureMilliKelvin()
            ), true);
        }
    }

    private static int flowSideways(
            ServerLevel level,
            BlockPos sourcePos,
            WaterVolumeChunk.WaterCell source,
            int sourceVolume
    ) {
        List<LateralCandidate> candidates = new ArrayList<>(4);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbourPos = sourcePos.relative(direction);
            if (!canOccupy(level, neighbourPos)) {
                continue;
            }
            WaterVolumeChunk.WaterCell neighbour = CanonicalWater.getOrImport(level, neighbourPos);
            int capacity = WaterVolumeChunk.UNITS_PER_BLOCK - neighbour.volumeUnits();
            int difference = sourceVolume - neighbour.volumeUnits();
            if (capacity > 0 && difference > MIN_LATERAL_DIFFERENCE_UNITS) {
                candidates.add(new LateralCandidate(direction, neighbourPos, capacity, difference));
            }
        }

        if (candidates.isEmpty()) {
            return 0;
        }
        candidates.sort(Comparator.comparingInt(LateralCandidate::difference).reversed());

        int remaining = sourceVolume;
        int moved = 0;
        for (LateralCandidate candidate : candidates) {
            if (remaining <= MIN_FLOW_UNITS) {
                break;
            }
            float gradient = Math.min(1.0f,
                    candidate.difference() / (float) WaterVolumeChunk.UNITS_PER_BLOCK);
            int requested = Math.min(
                    Math.min(candidate.capacity(), remaining),
                    Math.max(MIN_FLOW_UNITS, Math.min(MAX_LATERAL_TRANSFER_UNITS,
                            candidate.difference() / (candidates.size() + 1)))
            );
            if (requested <= 0) {
                continue;
            }

            float velocityX = source.velocityX() * 0.35f
                    + candidate.direction().getStepX() * SIDE_FLOW_SPEED * gradient;
            float velocityZ = source.velocityZ() * 0.35f
                    + candidate.direction().getStepZ() * SIDE_FLOW_SPEED * gradient;
            int accepted = addTargetVolume(
                    level,
                    candidate.pos(),
                    requested,
                    velocityX,
                    source.velocityY() * 0.20f,
                    velocityZ
            );
            remaining -= accepted;
            moved += accepted;
        }
        return moved;
    }

    private static int maybeCreateMobilePour(
            ServerLevel level,
            BlockPos sourcePos,
            WaterVolumeChunk.WaterCell source,
            WaterVolumeChunk.WaterCell target,
            int transfer
    ) {
        if (target.volumeUnits() > 0 || transfer < MOBILE_POUR_MIN_UNITS) {
            return 0;
        }
        int mobileVolume = Math.min(transfer, MOBILE_POUR_MAX_UNITS);
        boolean created = SPHSimulationManager.get().createCanonicalFlowSimulation(
                sourcePos.getX() + 0.5f,
                sourcePos.getY() + 0.35f,
                sourcePos.getZ() + 0.5f,
                level,
                mobileVolume,
                source.velocityX() * 0.35f,
                Math.min(FALL_SPEED, source.velocityY() - 1.0f),
                source.velocityZ() * 0.35f
        );
        return created ? mobileVolume : 0;
    }

    private static int addTargetVolume(
            ServerLevel level,
            BlockPos targetPos,
            int transfer,
            float velocityX,
            float velocityY,
            float velocityZ
    ) {
        // Commit the destination first. Its accepted amount is authoritative,
        // so a changed or non-replaceable target can never make volume vanish.
        return CanonicalWater.addVolume(
                level,
                targetPos,
                transfer,
                velocityX,
                velocityY,
                velocityZ
        );
    }

    private static void commitSource(
            ServerLevel level,
            BlockPos sourcePos,
            WaterVolumeChunk.WaterCell source,
            int remaining
    ) {
        CanonicalWater.set(level, sourcePos, remaining <= 0
                ? WaterVolumeChunk.WaterCell.EMPTY
                : new WaterVolumeChunk.WaterCell(
                        remaining,
                        source.velocityX() * SOURCE_VELOCITY_DAMPING,
                        source.velocityY() * SOURCE_VELOCITY_DAMPING,
                        source.velocityZ() * SOURCE_VELOCITY_DAMPING,
                        WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED,
                        source.temperatureMilliKelvin()
                ), true);
    }

    private static float speedSquared(WaterVolumeChunk.WaterCell cell) {
        return cell.velocityX() * cell.velocityX()
                + cell.velocityY() * cell.velocityY()
                + cell.velocityZ() * cell.velocityZ();
    }

    private static boolean canOccupy(ServerLevel level, BlockPos pos) {
        return CanonicalWater.canAcceptVolume(level, pos);
    }

    private record LateralCandidate(Direction direction, BlockPos pos, int capacity, int difference) {
    }
}
