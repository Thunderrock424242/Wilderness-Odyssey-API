package com.thunder.wildernessodysseyapi.watersystem.water.reactive;

import com.thunder.wildernessodysseyapi.watersystem.water.fluid.ShallowWaterFaceMath;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;

/** Quantizes shared-face proposals from an immutable canonical snapshot; never stores water. */
final class CanonicalSurfaceFlux {

    static final double MAXIMUM_SPEED = 4.8;
    private static final int FULL = WaterVolumeChunk.UNITS_PER_BLOCK;

    private CanonicalSurfaceFlux() { }

    static Plan plan(int[] volumes, int[] negative, int[] positive, double[] previous, double seconds) {
        if (negative.length != positive.length || negative.length != previous.length
                || !Double.isFinite(seconds) || seconds < 0) {
            throw new IllegalArgumentException("Invalid surface flux inputs");
        }
        double maximumDepth = 0;
        for (int volume : volumes) {
            if (volume < 0 || volume > FULL) throw new IllegalArgumentException("Invalid canonical depth");
            maximumDepth = Math.max(maximumDepth, volume / (double) FULL);
        }
        double dt = Math.min(seconds, ShallowWaterFaceMath.stableStepSeconds(maximumDepth, 1, MAXIMUM_SPEED));
        double[] discharge = new double[negative.length];
        double[] requested = new double[negative.length];
        double[] outgoing = new double[volumes.length];
        double[] incoming = new double[volumes.length];
        for (int face = 0; face < negative.length; face++) {
            int a = negative[face];
            int b = positive[face];
            if (a < 0 || b < 0 || a >= volumes.length || b >= volumes.length || a == b) {
                throw new IllegalArgumentException("Invalid surface face");
            }
            discharge[face] = ShallowWaterFaceMath.discharge(previous[face], 0, 0,
                    volumes[a] / (double) FULL, volumes[b] / (double) FULL,
                    dt, 1, Math.exp(-0.85 * dt), MAXIMUM_SPEED);
            int donor = discharge[face] >= 0 ? a : b;
            int receiver = discharge[face] >= 0 ? b : a;
            requested[face] = Math.abs(discharge[face]) * dt * FULL;
            outgoing[donor] += requested[face];
            incoming[receiver] += requested[face];
        }
        int[] units = new int[negative.length];
        for (int face = 0; face < negative.length; face++) {
            int donor = discharge[face] >= 0 ? negative[face] : positive[face];
            int receiver = discharge[face] >= 0 ? positive[face] : negative[face];
            // Receipts use only snapshot capacity. Neither newly received
            // water nor outgoing capacity can be spent again in this substep.
            double donorScale = outgoing[donor] > volumes[donor] ? volumes[donor] / outgoing[donor] : 1;
            double receiverScale = incoming[receiver] > FULL - volumes[receiver]
                    ? (FULL - volumes[receiver]) / incoming[receiver] : 1;
            double scale = Math.min(donorScale, receiverScale);
            int amount = (int) Math.floor(requested[face] * scale);
            units[face] = discharge[face] >= 0 ? amount : -amount;
            discharge[face] *= scale;
        }
        return new Plan(units, discharge, dt);
    }

    record Plan(int[] signedUnits, double[] discharge, double seconds) { }
}
