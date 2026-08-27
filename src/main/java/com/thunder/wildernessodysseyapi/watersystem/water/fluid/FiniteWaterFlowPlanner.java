package com.thunder.wildernessodysseyapi.watersystem.water.fluid;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;

import java.util.Arrays;

/**
 * Plans conservative transfers for one disturbed finite-water cell.
 *
 * <p>The planner is deliberately independent of Minecraft world state. Every
 * lateral request is derived from the same immutable volume snapshot, so
 * applying one destination cannot change the amount requested from another.
 * Runtime code still commits destinations first and deducts only the amount
 * each destination actually accepts.</p>
 */
final class FiniteWaterFlowPlanner {

    static final int BLOCKED_TARGET = -1;
    static final int MIN_FLOW_UNITS = WaterVolumeChunk.UNITS_PER_BLOCK / 64;
    static final int MIN_LATERAL_DIFFERENCE_UNITS = WaterVolumeChunk.UNITS_PER_BLOCK / 16;
    static final int MAX_LATERAL_TRANSFER_UNITS = WaterVolumeChunk.UNITS_PER_BLOCK / 4;

    private FiniteWaterFlowPlanner() {
    }

    /** Returns the exact amount gravity may move into the capacity below. */
    static int verticalTransfer(int sourceVolume, int targetVolume) {
        int boundedSource = clampVolume(sourceVolume);
        int capacity = WaterVolumeChunk.UNITS_PER_BLOCK - clampVolume(targetVolume);
        return Math.min(boundedSource, Math.max(0, capacity));
    }

    /**
     * Plans simultaneous lateral equalization from one source snapshot.
     *
     * <p>Blocked targets are represented by {@link #BLOCKED_TARGET}. Eligible
     * lower cells participate in a water-filling calculation: the source and
     * every target below the eventual common level are equalized together.
     * Transfer caps slow the motion without changing its symmetry. Integer
     * remainders remain in the source, avoiding a fixed directional bias.</p>
     */
    static LateralPlan planLateral(int sourceVolume, int[] targetVolumes) {
        int boundedSource = clampVolume(sourceVolume);
        int[] transfers = new int[targetVolumes.length];
        if (boundedSource <= MIN_FLOW_UNITS || targetVolumes.length == 0) {
            return new LateralPlan(boundedSource, transfers);
        }

        int[] eligibleVolumes = new int[targetVolumes.length];
        int eligibleCount = 0;
        for (int targetVolume : targetVolumes) {
            if (targetVolume == BLOCKED_TARGET) {
                continue;
            }
            int boundedTarget = clampVolume(targetVolume);
            if (boundedTarget < WaterVolumeChunk.UNITS_PER_BLOCK
                    && boundedSource - boundedTarget > MIN_LATERAL_DIFFERENCE_UNITS) {
                eligibleVolumes[eligibleCount++] = boundedTarget;
            }
        }
        if (eligibleCount == 0) {
            return new LateralPlan(boundedSource, transfers);
        }

        Arrays.sort(eligibleVolumes, 0, eligibleCount);
        long participatingVolume = boundedSource;
        int participantCount = 1;
        for (int index = 0; index < eligibleCount; index++) {
            int targetVolume = eligibleVolumes[index];
            int currentLevel = (int) (participatingVolume / participantCount);
            if (targetVolume >= currentLevel) {
                break;
            }
            participatingVolume += targetVolume;
            participantCount++;
        }
        int equalizedLevel = (int) (participatingVolume / participantCount);

        int plannedTotal = 0;
        for (int index = 0; index < targetVolumes.length; index++) {
            int targetVolume = targetVolumes[index];
            if (targetVolume == BLOCKED_TARGET) {
                continue;
            }
            int boundedTarget = clampVolume(targetVolume);
            if (boundedSource - boundedTarget <= MIN_LATERAL_DIFFERENCE_UNITS) {
                continue;
            }

            int desired = Math.max(0, equalizedLevel - boundedTarget);
            int capacity = WaterVolumeChunk.UNITS_PER_BLOCK - boundedTarget;
            int available = boundedSource - plannedTotal;
            int transfer = Math.min(
                    Math.min(desired, capacity),
                    Math.min(MAX_LATERAL_TRANSFER_UNITS, available)
            );
            transfers[index] = Math.max(0, transfer);
            plannedTotal += transfers[index];
        }
        return new LateralPlan(boundedSource - plannedTotal, transfers);
    }

    private static int clampVolume(int volume) {
        return Math.max(0, Math.min(WaterVolumeChunk.UNITS_PER_BLOCK, volume));
    }

    /** Requested transfer snapshot and the source remainder it implies. */
    record LateralPlan(int sourceRemainder, int[] transfers) {
    }
}
