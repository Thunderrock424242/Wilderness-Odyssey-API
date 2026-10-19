package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

/** Immutable exact pre-decoration surface and a sixteen-block natural-foundation mask. */
public final class RiverTileTerrain {
    public static final int SIZE = 32;
    private final int[] heights;
    private final int[] masks;
    private final boolean[] water;

    private RiverTileTerrain(int[] heights, int[] masks, boolean[] water) {
        this.heights = heights;
        this.masks = masks;
        this.water = water;
    }

    /** Copies bounded inputs; providers and their mutable state are not retained. */
    public static RiverTileTerrain create(IntSampler heights, WaterSampler water, IntSampler masks) {
        int[] capturedHeights = new int[SIZE * SIZE];
        int[] capturedMasks = new int[SIZE * SIZE];
        boolean[] capturedWater = new boolean[SIZE * SIZE];
        for (int z = 0; z < SIZE; z++) for (int x = 0; x < SIZE; x++) {
            int cell = x + z * SIZE;
            capturedHeights[cell] = heights.sample(x, z);
            capturedMasks[cell] = masks.sample(x, z) & 0xFFFF;
            capturedWater[cell] = water.sample(x, z);
        }
        return new RiverTileTerrain(capturedHeights, capturedMasks, capturedWater);
    }

    public int height(int x, int z) { return heights[x + z * SIZE]; }
    public boolean water(int x, int z) { return water[x + z * SIZE]; }
    public boolean naturalAt(int x, int y, int z) {
        int depth = height(x, z) - y;
        return depth >= 0 && depth < 16 && (masks[x + z * SIZE] & (1 << depth)) != 0;
    }

    @FunctionalInterface public interface IntSampler { int sample(int x, int z); }
    @FunctionalInterface public interface WaterSampler { boolean sample(int x, int z); }
}
