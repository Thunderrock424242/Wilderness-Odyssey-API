package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import java.util.Arrays;

import static com.thunder.wildernessodysseyapi.watersystem.water.hydrology.HydrologicReservoir.FLOODPLAIN;
import static com.thunder.wildernessodysseyapi.watersystem.water.hydrology.RegionalHydrologyState.Boundary.*;

/** Reproducible arithmetic workload; deliberately makes no gameplay FPS/MSPT or GPU claim. */
public final class WaterDynamicsBenchmark {
    private static volatile double blackhole;
    private static final int REGIONS = 32;
    private static final int STEPS = 3300;
    private static final int JET_SAMPLES = 100000;

    private WaterDynamicsBenchmark() { }

    public static void main(String[] arguments) {
        System.out.printf(java.util.Locale.ROOT, "water-dynamics java=%s vendor=%s processors=%d maxHeapMiB=%d%n",
                System.getProperty("java.version"), System.getProperty("java.vendor"),
                Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() / (1024 * 1024));
        for (int warmup = 0; warmup < 3; warmup++) measure();
        long[] times = new long[7];
        for (int round = 0; round < times.length; round++) {
            times[round] = measure();
            System.out.printf(java.util.Locale.ROOT,
                    "water-dynamics round=%d regionalSteps=%d waterfallSamples=%d elapsedMs=%.3f residual=0 finalClaims=0%n",
                    round + 1, REGIONS * STEPS, JET_SAMPLES, times[round] / 1e6);
        }
        Arrays.sort(times);
        System.out.printf(java.util.Locale.ROOT,
                "water-dynamics measuredRounds=7 warmupRounds=3 medianMs=%.3f slowestMs=%.3f checksum=%.6f%n",
                times[3] / 1e6, times[6] / 1e6, blackhole);
    }

    private static long measure() {
        var regions = new RegionalHydrologyState[REGIONS];
        int[] projected = new int[REGIONS];
        for (int i = 0; i < regions.length; i++) {
            regions[i] = new RegionalHydrologyState(i, 0, 63);
            regions[i].ocean = true;
        }
        var parameters = new RegionalHydrologyModel.Parameters(0, 0, .045, 1.25, 1800, false, false, true, 120);
        long start = System.nanoTime();
        for (int step = 0; step < STEPS; step++) {
            for (int index = 0; index < regions.length; index++) {
                var region = regions[index];
                region.surgeTargetMetres = step < 1800 && step % 600 < 300 ? 1.5 : 0;
                RegionalHydrologyModel.advance(region, null, parameters, 2, projected[index] * 1000L);
                if (step == 60) {
                    try (var parcel = RegionalWaterProjection.reserve(region, FLOODPLAIN, 4096)) {
                        if (parcel == null) throw new AssertionError("Storm did not fund projection");
                        parcel.commit();
                        projected[index] = 4096;
                    }
                }
                if (projected[index] > 0 && region.stored(FLOODPLAIN) == 0) {
                    RegionalWaterProjection.returnParcel(region, projected[index], false);
                    projected[index] = 0;
                }
                if (region.residualMilliUnits() != 0
                        || region.receipt(PROJECTION_OUT) - region.receipt(PROJECTION_IN) != projected[index] * 1000L) {
                    throw new AssertionError("Budget or projection residual during sustained storms");
                }
            }
        }
        double checksum = 0;
        for (int i = 0; i < JET_SAMPLES; i++) {
            var jet = WaterfallHydraulics.sample(16, 0, (i % 17 - 8) / 10.0,
                    i % 11 == 0 ? 4 : 0, (i % 13 - 6) / 10.0, 1.2f, 0, 0, i);
            checksum += jet.x() + jet.y() + jet.z() + jet.foam();
        }
        for (int i = 0; i < regions.length; i++) {
            if (regions[i].totalStored() != 0 || projected[i] != 0
                    || regions[i].receipt(OCEAN_IN) != regions[i].receipt(OCEAN_OUT)) {
                throw new AssertionError("Final recession left water or lost an ocean receipt");
            }
        }
        blackhole = checksum;
        return System.nanoTime() - start;
    }
}
