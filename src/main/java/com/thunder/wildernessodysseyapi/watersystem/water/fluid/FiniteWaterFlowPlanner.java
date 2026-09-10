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
    static final double GRAVITY_METERS_PER_SECOND_SQUARED = 9.81;
    static final double DISCHARGE_COEFFICIENT = 0.62;
    static final double MINIMUM_HEAD_METERS = 1.0 / 64.0;

    private FiniteWaterFlowPlanner() {
    }

    /** Returns the exact amount gravity may move into the capacity below. */
    static int verticalTransfer(int sourceVolume, int targetVolume) {
        int boundedSource = clampVolume(sourceVolume);
        int capacity = WaterVolumeChunk.UNITS_PER_BLOCK - clampVolume(targetVolume);
        return Math.min(boundedSource, Math.max(0, capacity));
    }

    /**
     * Returns a time-scaled gravity transfer through a full block opening.
     *
     * <p>The legacy overload remains for compatibility tests and transactional callers. Runtime
     * flow uses this overload so queue budget changes delay evaluation without redefining one
     * evaluation as an arbitrary amount of physical time.</p>
     */
    static int verticalTransfer(int sourceVolume, int targetVolume, double dtSeconds) {
        int maximum = verticalTransfer(sourceVolume, targetVolume);
        if (maximum <= 0) {
            return 0;
        }
        double boundedDt = clampFinite(dtSeconds, 0.0, 1.0);
        if (boundedDt == 0.0) {
            return 0;
        }
        double sourceDepth = clampVolume(sourceVolume) / (double) WaterVolumeChunk.UNITS_PER_BLOCK;
        double speed = Math.sqrt(2.0 * GRAVITY_METERS_PER_SECOND_SQUARED
                * Math.max(MINIMUM_HEAD_METERS, sourceDepth));
        long requested = Math.round(DISCHARGE_COEFFICIENT
                * Math.max(MINIMUM_HEAD_METERS, sourceDepth)
                * speed
                * boundedDt
                * WaterVolumeChunk.UNITS_PER_BLOCK);
        return Math.min(maximum, (int) Math.max(0L, requested));
    }

    /**
     * Plans simultaneous lateral discharge from free-surface head and opening geometry.
     *
     * <p>Bed elevations and depths are expressed in block-metres. Candidate discharge follows
     * a bounded orifice approximation, is capped at the pairwise equalization point, and is then
     * scaled against the one immutable source snapshot. Integer remainder always stays with the
     * source, so applying accepted destination credits cannot create or lose a unit.</p>
     */
    static LateralPlan planHydraulic(
            double sourceBedElevation,
            int sourceVolume,
            double[] targetBedElevations,
            int[] targetVolumes,
            double[] openingFractions,
            double dtSeconds
    ) {
        if (targetBedElevations.length != targetVolumes.length
                || openingFractions.length != targetVolumes.length) {
            throw new IllegalArgumentException("Hydraulic target arrays must have equal length");
        }
        int boundedSource = clampVolume(sourceVolume);
        int[] transfers = new int[targetVolumes.length];
        if (boundedSource <= MIN_FLOW_UNITS || targetVolumes.length == 0) {
            return new LateralPlan(boundedSource, transfers);
        }

        double boundedDt = clampFinite(dtSeconds, 0.0, 1.0);
        double sourceDepth = boundedSource / (double) WaterVolumeChunk.UNITS_PER_BLOCK;
        double sourceHead = finiteOrZero(sourceBedElevation) + sourceDepth;
        double equilibriumHead = equilibriumHead(sourceHead, boundedSource,
                targetBedElevations, targetVolumes, openingFractions);
        long candidateTotal = 0L;
        long[] candidates = new long[targetVolumes.length];

        for (int index = 0; index < targetVolumes.length; index++) {
            if (targetVolumes[index] == BLOCKED_TARGET) {
                continue;
            }
            int targetVolume = clampVolume(targetVolumes[index]);
            int capacity = WaterVolumeChunk.UNITS_PER_BLOCK - targetVolume;
            double targetHead = finiteOrZero(targetBedElevations[index])
                    + targetVolume / (double) WaterVolumeChunk.UNITS_PER_BLOCK;
            double headDifference = sourceHead - targetHead;
            double opening = clampFinite(openingFractions[index], 0.0, 1.0);
            if (capacity <= 0 || opening <= 0.0 || headDifference <= MINIMUM_HEAD_METERS) {
                continue;
            }

            double wettedOpening = opening * Math.max(MINIMUM_HEAD_METERS,
                    Math.min(1.0, Math.max(sourceDepth,
                            targetVolume / (double) WaterVolumeChunk.UNITS_PER_BLOCK)));
            double speed = Math.sqrt(2.0 * GRAVITY_METERS_PER_SECOND_SQUARED * headDifference);
            long dischargeUnits = (long) Math.floor(DISCHARGE_COEFFICIENT
                    * wettedOpening
                    * speed
                    * boundedDt
                    * WaterVolumeChunk.UNITS_PER_BLOCK);
            // All open destinations share one equilibrium calculation. Pairwise
            // half-differences overdraw the source when several outlets open together.
            long equalizationLimit = (long) Math.floor(Math.max(0.0,
                    equilibriumHead - targetHead) * WaterVolumeChunk.UNITS_PER_BLOCK);
            long candidate = Math.min(capacity,
                    Math.min(equalizationLimit, Math.max(0L, dischargeUnits)));
            candidates[index] = candidate;
            candidateTotal += candidate;
        }

        if (candidateTotal <= 0L) {
            return new LateralPlan(boundedSource, transfers);
        }

        double scale = Math.min(1.0, boundedSource / (double) candidateTotal);
        int plannedTotal = 0;
        for (int index = 0; index < candidates.length; index++) {
            int transfer = (int) Math.floor(candidates[index] * scale);
            transfer = Math.min(transfer, boundedSource - plannedTotal);
            transfers[index] = Math.max(0, transfer);
            plannedTotal += transfers[index];
        }
        return new LateralPlan(boundedSource - plannedTotal, transfers);
    }

    private static double equilibriumHead(double sourceHead, int sourceVolume,
                                          double[] beds, int[] volumes, double[] openings) {
        double lower = sourceHead - sourceVolume / (double) WaterVolumeChunk.UNITS_PER_BLOCK;
        double upper = sourceHead;
        for (int iteration = 0; iteration < 48; iteration++) {
            double head = (lower + upper) * 0.5;
            double demand = 0.0;
            for (int index = 0; index < volumes.length; index++) {
                if (volumes[index] == BLOCKED_TARGET
                        || clampFinite(openings[index], 0.0, 1.0) <= 0.0) {
                    continue;
                }
                int volume = clampVolume(volumes[index]);
                double targetHead = finiteOrZero(beds[index])
                        + volume / (double) WaterVolumeChunk.UNITS_PER_BLOCK;
                if (sourceHead - targetHead <= MINIMUM_HEAD_METERS) {
                    continue;
                }
                demand += Math.min(WaterVolumeChunk.UNITS_PER_BLOCK - volume,
                        Math.max(0.0, head - targetHead) * WaterVolumeChunk.UNITS_PER_BLOCK);
            }
            double supply = (sourceHead - head) * WaterVolumeChunk.UNITS_PER_BLOCK;
            if (demand > supply) {
                upper = head;
            } else {
                lower = head;
            }
        }
        return lower;
    }

    /** Tick-derived elapsed time; repeat evaluation in the same tick advances nothing. */
    static double elapsedSeconds(Long previousTick, long gameTick) {
        return previousTick == null ? 0.05 : Math.max(0.0, Math.min(1.0,
                (gameTick - previousTick) / 20.0));
    }

    /** Returns acceleration-derived horizontal velocity after drag and a head gradient. */
    static float velocityAfterHeadGradient(
            float previousVelocity,
            double signedHeadGradient,
            double dtSeconds,
            double dragPerSecond,
            double maximumSpeed
    ) {
        double boundedDt = clampFinite(dtSeconds, 0.0, 1.0);
        double drag = Math.exp(-Math.max(0.0, finiteOrZero(dragPerSecond)) * boundedDt);
        double velocity = finiteOrZero(previousVelocity) * drag
                + GRAVITY_METERS_PER_SECOND_SQUARED * finiteOrZero(signedHeadGradient) * boundedDt;
        return (float) clampFinite(velocity, -Math.abs(maximumSpeed), Math.abs(maximumSpeed));
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

    private static double clampFinite(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, finiteOrZero(value)));
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    /** Requested transfer snapshot and the source remainder it implies. */
    record LateralPlan(int sourceRemainder, int[] transfers) {
    }
}
