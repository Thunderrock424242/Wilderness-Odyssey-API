package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import java.util.Arrays;
import java.util.Locale;

/**
 * Reproducible synthetic CPU benchmark, deliberately outside JUnit timing assertions.
 * It does not measure Minecraft terrain prediction, worldgen, rendering or network performance.
 */
public final class RiverNetworkPlannerBenchmark {
    private static final int WARMUP = 50;
    private static final int MEASUREMENTS = 200;
    private static volatile long consumed;

    private RiverNetworkPlannerBenchmark() { }

    public static void main(String[] args) {
        System.out.println("River planner synthetic benchmark; capture and plan measured separately; "
                + WARMUP + " warmups, " + MEASUREMENTS + " measured iterations per scenario; Java "
                + System.getProperty("java.version") + ", processors=" + Runtime.getRuntime().availableProcessors());
        run("loaded-window-slope", 10, false);
        run("rugged-1024", 32, true);
        run("maximum-4096", 64, true);
        catchment("sealed-pool", 0);
        catchment("receiver-tributary", 1);
        catchment("steep-drop", 2);
        catchment("rugged-rejected", 3);
        System.out.println("Consumed topology checksum=" + consumed);
    }

    private static void run(String name, int size, boolean rugged) {
        var key = new RiverTerrainSnapshot.Key(42, "minecraft:overworld", rugged ? 2 : 1);
        var layout = new RiverTerrainSnapshot.Layout(-size / 2, -size / 2, size, size, 16, size * size);
        RiverTerrainSnapshot.TerrainSampler sampler = (x, z) -> {
            int height = rugged ? 100 + (int) (Math.sin(x * 0.03) * 30 + Math.cos(z * 0.04) * 20)
                    : 300 - Math.floorDiv(x, 16);
            return new RiverTerrainSnapshot.Terrain(height, false);
        };
        long[] capture = new long[MEASUREMENTS];
        long[] plan = new long[MEASUREMENTS];
        for (int iteration = -WARMUP; iteration < MEASUREMENTS; iteration++) {
            long start = System.nanoTime();
            var snapshot = RiverTerrainSnapshot.capture(key, layout, sampler);
            long captured = System.nanoTime();
            var result = RiverNetworkPlanner.plan(snapshot);
            long planned = System.nanoTime();
            if (result.nodes().size() != size * size) throw new IllegalStateException("Lost benchmark terrain cells");
            consumed += result.nodes().getFirst().reachId() ^ result.nodes().getLast().contributingCells();
            if (iteration >= 0) {
                capture[iteration] = captured - start;
                plan[iteration] = planned - captured;
            }
        }
        report(name, "capture", capture);
        report(name, "plan", plan);
    }

    private static void report(String scenario, String operation, long[] measurements) {
        double mean = Arrays.stream(measurements).average().orElseThrow() / 1_000_000.0;
        Arrays.sort(measurements);
        double p95 = measurements[(int) Math.ceil(measurements.length * 0.95) - 1] / 1_000_000.0;
        double p99 = measurements[(int) Math.ceil(measurements.length * 0.99) - 1] / 1_000_000.0;
        System.out.printf(Locale.ROOT,
                "{\"scenario\":\"%s\",\"operation\":\"%s\",\"mean_ms\":%.4f,\"p95_ms\":%.4f,\"p99_ms\":%.4f}%n",
                scenario, operation, mean, p95, p99);
    }

    private static void catchment(String name, int shape) {
        var settings = new RiverGenerationSettings(true, 1, 2, 1, 2, 10, 256, 12288, true, true, true);
        var terrain = RiverTileTerrain.create((x, z) -> switch (shape) {
            case 0 -> 140 + Math.max(Math.abs(x - 16), Math.abs(z - 16)) / 3;
            case 1 -> x >= 23 ? 100 : 100 + (23 - x) / 3;
            case 2 -> x >= 23 ? 100 : 100 + (23 - x) * 2;
            default -> 140 + (int) (Math.sin(x * 0.7) * 15 + Math.cos(z * 0.6) * 12);
        }, (x, z) -> (shape == 1 || shape == 2) && x >= 23, (x, z) -> 0xFFFF);
        long[] times = new long[MEASUREMENTS];
        int columns = 0;
        int writes = 0;
        for (int i = -WARMUP; i < MEASUREMENTS; i++) {
            long start = System.nanoTime();
            var plan = RiverCatchmentPlanner.plan(42, 0, 0, terrain, settings);
            long elapsed = System.nanoTime() - start;
            consumed += plan.basinId();
            columns = plan.columns().size();
            writes = plan.requiredWrites();
            if (i >= 0) times[i] = elapsed;
        }
        report(name, "complete-catchment-plan", times);
        System.out.printf(Locale.ROOT, "{\"scenario\":\"%s\",\"columns\":%d,\"writes\":%d}%n", name, columns, writes);
    }
}
