package com.thunder.wildernessodysseyapi.watersystem.water.reactive;

import com.thunder.wildernessodysseyapi.watersystem.water.fluid.ShallowWaterFaceMath;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;

import java.util.Arrays;

/** Quantizes shared-face proposals from an immutable canonical snapshot; never stores water. */
final class CanonicalSurfaceFlux {

    static final double MAXIMUM_SPEED = 4.8;
    private static final int FULL = WaterVolumeChunk.UNITS_PER_BLOCK;

    private CanonicalSurfaceFlux() { }

    static Plan plan(int[] volumes, int[] negative, int[] positive, double[] previous, double seconds) {
        int[] capacities = new int[volumes.length];
        Arrays.fill(capacities, FULL);
        double[] tops = new double[negative.length];
        Arrays.fill(tops, Double.POSITIVE_INFINITY);
        return plan(volumes, capacities, new double[volumes.length], tops, negative, positive, previous, seconds);
    }

    static Plan plan(int[] volumes, int[] capacities, double[] beds, double[] openingTops,
                     int[] negative, int[] positive, double[] previous, double seconds) {
        if (negative.length != positive.length || negative.length != previous.length
                || negative.length != openingTops.length || volumes.length != capacities.length
                || volumes.length != beds.length
                || !Double.isFinite(seconds) || seconds < 0) {
            throw new IllegalArgumentException("Invalid surface flux inputs");
        }
        double maximumDepth = 0;
        for (int cell = 0; cell < volumes.length; cell++) {
            if (volumes[cell] < 0 || capacities[cell] < 0 || volumes[cell] > capacities[cell]
                    || !Double.isFinite(beds[cell])) throw new IllegalArgumentException("Invalid canonical column");
            maximumDepth = Math.max(maximumDepth, volumes[cell] / (double) FULL);
        }
        double dt = Math.min(seconds, ShallowWaterFaceMath.stableStepSeconds(maximumDepth, 1, MAXIMUM_SPEED));
        double[] discharge = new double[negative.length];
        double[] requested = new double[negative.length];
        double[] incoming = new double[volumes.length];
        double[] sills = new double[negative.length];
        int[] donors = new int[negative.length];
        int[] receivers = new int[negative.length];
        for (int face = 0; face < negative.length; face++) {
            int a = negative[face];
            int b = positive[face];
            if (a < 0 || b < 0 || a >= volumes.length || b >= volumes.length || a == b) {
                throw new IllegalArgumentException("Invalid surface face");
            }
            if (Double.isNaN(openingTops[face])) throw new IllegalArgumentException("Invalid opening");
            sills[face] = Math.max(beds[a], beds[b]);
            discharge[face] = ShallowWaterFaceMath.discharge(previous[face], beds[a], beds[b],
                    beds[a] + volumes[a] / (double) FULL, beds[b] + volumes[b] / (double) FULL,
                    dt, 1, Math.exp(-0.85 * dt), MAXIMUM_SPEED,
                    Math.max(0, openingTops[face] - sills[face]));
            int donor = discharge[face] >= 0 ? a : b;
            int receiver = discharge[face] >= 0 ? b : a;
            if (beds[donor] + volumes[donor] / (double) FULL <= sills[face]) discharge[face] = 0;
            donors[face] = donor;
            receivers[face] = receiver;
            requested[face] = Math.abs(discharge[face]) * dt * FULL;
        }
        double[] limited = requested.clone();
        // Outlets at or above a sill share only the water above that sill.
        // Lower outlets are not stalled by a nearly dry higher opening.
        for (int face = 0; face < negative.length; face++) {
            int donor = donors[face];
            double outgoing = 0;
            for (int other = 0; other < limited.length; other++) {
                if (donors[other] == donor && sills[other] >= sills[face]) outgoing += limited[other];
            }
            double available = Math.max(0, Math.min(volumes[donor],
                    (beds[donor] + volumes[donor] / (double) FULL - sills[face]) * FULL));
            if (outgoing > available) {
                double scale = available / outgoing;
                for (int other = 0; other < limited.length; other++) {
                    if (donors[other] == donor && sills[other] >= sills[face]) limited[other] *= scale;
                }
            }
        }
        for (int face = 0; face < negative.length; face++) incoming[receivers[face]] += limited[face];
        int[] units = new int[negative.length];
        for (int face = 0; face < negative.length; face++) {
            int receiver = receivers[face];
            // Receipts use only snapshot capacity. Neither newly received
            // water nor outgoing capacity can be spent again in this substep.
            double receiverScale = incoming[receiver] > capacities[receiver] - volumes[receiver]
                    ? (capacities[receiver] - volumes[receiver]) / incoming[receiver] : 1;
            int amount = (int) Math.floor(limited[face] * receiverScale);
            units[face] = discharge[face] >= 0 ? amount : -amount;
            if (requested[face] > 0) discharge[face] *= limited[face] / requested[face] * receiverScale;
        }
        return new Plan(units, discharge, dt);
    }

    record Plan(int[] signedUnits, double[] discharge, double seconds) { }
}
