package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import java.util.Objects;

/**
 * Immutable, bounded terrain input for generation planning or loaded-only inspection.
 * The provider owns terrain access; this type has no Minecraft world/chunk reference.
 */
public final class RiverTerrainSnapshot {
    public static final int PLANNER_VERSION = 1;
    public static final int MAXIMUM_SAMPLES = 4096;

    private final Key key;
    private final Layout layout;
    private final Terrain[] cells;

    private RiverTerrainSnapshot(Key key, Layout layout, Terrain[] cells) {
        this.key = key;
        this.layout = layout;
        this.cells = cells;
    }

    /**
     * Captures each lattice point once, in stable Z/X order. All size and coordinate
     * checks finish before the first provider call. Missing terrain remains unknown.
     */
    public static RiverTerrainSnapshot capture(Key key, Layout layout, TerrainSampler sampler) {
        Objects.requireNonNull(key, "Planning identity is required");
        Objects.requireNonNull(layout, "Planning layout is required");
        Objects.requireNonNull(sampler, "Terrain provider is required");
        int count = layout.width() * layout.height();
        Terrain[] cells = new Terrain[count];
        for (int z = 0; z < layout.height(); z++) {
            for (int x = 0; x < layout.width(); x++) {
                cells[z * layout.width() + x] = sampler.sample(
                        layout.worldX(layout.minimumCellX() + x),
                        layout.worldZ(layout.minimumCellZ() + z));
            }
        }
        return new RiverTerrainSnapshot(key, layout, cells);
    }

    public Key key() { return key; }
    public Layout layout() { return layout; }

    /** Returns null for an unavailable point, including outside the captured halo. */
    public Terrain terrainAt(int cellX, int cellZ) {
        long x = (long) cellX - layout.minimumCellX();
        long z = (long) cellZ - layout.minimumCellZ();
        return x < 0 || z < 0 || x >= layout.width() || z >= layout.height()
                ? null : cells[(int) z * layout.width() + (int) x];
    }

    /** World, dimension and relevant generator/settings fingerprint; version is also hashed into reach IDs. */
    public record Key(long worldSeed, String dimension, long terrainIdentity) {
        public Key {
            if (dimension == null || dimension.isBlank() || dimension.length() > 256) {
                throw new IllegalArgumentException("A bounded dimension identity is required");
            }
        }
    }

    /** Fixed world-aligned lattice rectangle, including its caller-selected halo. */
    public record Layout(int minimumCellX, int minimumCellZ, int width, int height,
                         int cellSize, int maximumSamples) {
        public Layout {
            if (width < 1 || height < 1 || width > 64 || height > 64
                    || cellSize < 4 || cellSize > 64 || (cellSize & (cellSize - 1)) != 0
                    || maximumSamples < 1 || maximumSamples > MAXIMUM_SAMPLES
                    || (long) width * height > maximumSamples) {
                throw new IllegalArgumentException("River terrain capture exceeds its bounded layout");
            }
            checkCoordinate(minimumCellX, width, cellSize);
            checkCoordinate(minimumCellZ, height, cellSize);
        }

        private static void checkCoordinate(int minimum, int size, int spacing) {
            long first = (long) minimum * spacing + spacing / 2;
            long last = ((long) minimum + size - 1) * spacing + spacing / 2;
            if (first < Integer.MIN_VALUE || last > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("River lattice exceeds world coordinate range");
            }
        }

        public int worldX(int cellX) { return Math.toIntExact((long) cellX * cellSize + cellSize / 2); }
        public int worldZ(int cellZ) { return Math.toIntExact((long) cellZ * cellSize + cellSize / 2); }
    }

    /** Effective ground or receiving-water surface elevation, never a quantity of spendable water. */
    public record Terrain(int elevation, boolean receivingWater) { }

    /**
     * Must be read-only, deterministic for generation, bounded and chunk-request-free.
     * A generation adapter may use supported noise predictions; runtime adapters
     * must use already-loaded immutable/captured terrain. Null means unavailable.
     */
    @FunctionalInterface
    public interface TerrainSampler {
        Terrain sample(int worldX, int worldZ);
    }
}
