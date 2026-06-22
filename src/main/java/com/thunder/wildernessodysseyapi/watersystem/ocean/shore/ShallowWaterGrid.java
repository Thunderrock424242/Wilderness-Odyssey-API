package com.thunder.wildernessodysseyapi.watersystem.ocean.shore;

import java.util.Arrays;

/**
 * Solves the depth-averaged shallow-water equations on a regular X/Z grid.
 *
 * <p>This model is intended for shorelines, tidal flats, and river mouths
 * where vertical flow is small compared with horizontal flow. Deep oceans use
 * the cheaper Gerstner spectrum, while breaking spray remains SPH. The solver
 * uses a CFL-limited timestep, wet/dry cells, gravity-driven surface gradients,
 * and bottom friction to remain stable at Minecraft's 20 Hz tick rate.</p>
 */
public final class ShallowWaterGrid {

    private static final float GRAVITY = 9.81f;
    private static final float CFL_NUMBER = 0.42f;
    private static final float MIN_WATER_DEPTH = 0.01f;
    private static final float BOTTOM_FRICTION = 0.85f;
    private static final float BOUNDARY_RELAXATION = 0.12f;

    private final int width;
    private final int height;
    private final float cellSize;
    private final float[] restDepth;
    private final float[] surface;
    private final float[] velocityX;
    private final float[] velocityZ;
    private final float[] nextSurface;
    private final float[] nextVelocityX;
    private final float[] nextVelocityZ;

    /**
     * Creates a shoreline grid.
     *
     * @param width cells along world X, at least three
     * @param height cells along world Z, at least three
     * @param cellSize cell spacing in blocks
     */
    public ShallowWaterGrid(int width, int height, float cellSize) {
        if (width < 3 || height < 3) {
            throw new IllegalArgumentException("A shallow-water grid requires at least 3 x 3 cells");
        }
        if (!(cellSize > 0.0f)) {
            throw new IllegalArgumentException("Cell size must be positive");
        }

        this.width = width;
        this.height = height;
        this.cellSize = cellSize;
        int cellCount = width * height;
        this.restDepth = new float[cellCount];
        this.surface = new float[cellCount];
        this.velocityX = new float[cellCount];
        this.velocityZ = new float[cellCount];
        this.nextSurface = new float[cellCount];
        this.nextVelocityX = new float[cellCount];
        this.nextVelocityZ = new float[cellCount];
    }

    /**
     * Updates static bathymetric depth for one cell.
     *
     * @param x local cell X
     * @param z local cell Z
     * @param depth undisturbed water depth in blocks; zero marks dry terrain
     */
    public void setRestDepth(int x, int z, float depth) {
        int index = index(x, z);
        restDepth[index] = Math.max(0.0f, depth);
        if (restDepth[index] <= MIN_WATER_DEPTH) {
            surface[index] = 0.0f;
            velocityX[index] = 0.0f;
            velocityZ[index] = 0.0f;
        } else {
            surface[index] = Math.max(surface[index], -restDepth[index] + MIN_WATER_DEPTH);
        }
    }

    /**
     * Adds a localized horizontal disturbance such as a breaking wave impulse.
     */
    public void addImpulse(int x, int z, float impulseX, float impulseZ) {
        int index = index(x, z);
        if (restDepth[index] <= MIN_WATER_DEPTH) {
            return;
        }
        velocityX[index] += impulseX;
        velocityZ[index] += impulseZ;
    }

    /**
     * Advances the depth-averaged flow while relaxing open edges toward the
     * current ocean/tide boundary elevation.
     *
     * @param deltaSeconds elapsed simulation time
     * @param boundarySurface ocean boundary elevation relative to rest level
     */
    public void step(float deltaSeconds, float boundarySurface) {
        if (!(deltaSeconds > 0.0f)) {
            return;
        }

        float maxDepth = maximumWaterDepth();
        float maxStableStep = CFL_NUMBER * cellSize
                / Math.max(0.001f, (float) Math.sqrt(GRAVITY * Math.max(maxDepth, MIN_WATER_DEPTH)));
        int substeps = Math.max(1, (int) Math.ceil(deltaSeconds / maxStableStep));
        float stepSeconds = deltaSeconds / substeps;

        for (int substep = 0; substep < substeps; substep++) {
            solveSubstep(stepSeconds, boundarySurface);
        }
    }

    /** Returns the simulated surface displacement at one cell. */
    public float surface(int x, int z) {
        return surface[index(x, z)];
    }

    /** Returns depth-averaged X velocity in blocks per second. */
    public float velocityX(int x, int z) {
        return velocityX[index(x, z)];
    }

    /** Returns depth-averaged Z velocity in blocks per second. */
    public float velocityZ(int x, int z) {
        return velocityZ[index(x, z)];
    }

    /** Returns undisturbed bathymetric depth in blocks. */
    public float restDepth(int x, int z) {
        return restDepth[index(x, z)];
    }

    /** Clears dynamic surface and velocity state while preserving bathymetry. */
    public void resetMotion() {
        Arrays.fill(surface, 0.0f);
        Arrays.fill(velocityX, 0.0f);
        Arrays.fill(velocityZ, 0.0f);
    }

    private void solveSubstep(float deltaSeconds, float boundarySurface) {
        System.arraycopy(surface, 0, nextSurface, 0, surface.length);
        System.arraycopy(velocityX, 0, nextVelocityX, 0, velocityX.length);
        System.arraycopy(velocityZ, 0, nextVelocityZ, 0, velocityZ.length);

        float inverseTwoCells = 0.5f / cellSize;
        float friction = Math.max(0.0f, 1.0f - BOTTOM_FRICTION * deltaSeconds);

        for (int z = 1; z < height - 1; z++) {
            for (int x = 1; x < width - 1; x++) {
                int center = indexUnchecked(x, z);
                if (restDepth[center] <= MIN_WATER_DEPTH) {
                    nextSurface[center] = 0.0f;
                    nextVelocityX[center] = 0.0f;
                    nextVelocityZ[center] = 0.0f;
                    continue;
                }

                int left = indexUnchecked(x - 1, z);
                int right = indexUnchecked(x + 1, z);
                int down = indexUnchecked(x, z - 1);
                int up = indexUnchecked(x, z + 1);

                float surfaceGradientX = (surface[right] - surface[left]) * inverseTwoCells;
                float surfaceGradientZ = (surface[up] - surface[down]) * inverseTwoCells;
                nextVelocityX[center] = (velocityX[center] - GRAVITY * deltaSeconds * surfaceGradientX) * friction;
                nextVelocityZ[center] = (velocityZ[center] - GRAVITY * deltaSeconds * surfaceGradientZ) * friction;

                float fluxLeft = wetDepth(left) * velocityX[left];
                float fluxRight = wetDepth(right) * velocityX[right];
                float fluxDown = wetDepth(down) * velocityZ[down];
                float fluxUp = wetDepth(up) * velocityZ[up];
                float divergence = (fluxRight - fluxLeft + fluxUp - fluxDown) * inverseTwoCells;
                nextSurface[center] = Math.max(
                        -restDepth[center] + MIN_WATER_DEPTH,
                        surface[center] - deltaSeconds * divergence
                );
            }
        }

        relaxOpenBoundary(boundarySurface);
        System.arraycopy(nextSurface, 0, surface, 0, surface.length);
        System.arraycopy(nextVelocityX, 0, velocityX, 0, velocityX.length);
        System.arraycopy(nextVelocityZ, 0, velocityZ, 0, velocityZ.length);
    }

    private void relaxOpenBoundary(float boundarySurface) {
        for (int x = 0; x < width; x++) {
            relaxBoundaryCell(indexUnchecked(x, 0), boundarySurface);
            relaxBoundaryCell(indexUnchecked(x, height - 1), boundarySurface);
        }
        for (int z = 1; z < height - 1; z++) {
            relaxBoundaryCell(indexUnchecked(0, z), boundarySurface);
            relaxBoundaryCell(indexUnchecked(width - 1, z), boundarySurface);
        }
    }

    private void relaxBoundaryCell(int index, float boundarySurface) {
        if (restDepth[index] <= MIN_WATER_DEPTH) {
            nextSurface[index] = 0.0f;
            nextVelocityX[index] = 0.0f;
            nextVelocityZ[index] = 0.0f;
            return;
        }
        nextSurface[index] += (boundarySurface - nextSurface[index]) * BOUNDARY_RELAXATION;
    }

    private float maximumWaterDepth() {
        float result = MIN_WATER_DEPTH;
        for (int i = 0; i < restDepth.length; i++) {
            result = Math.max(result, wetDepth(i));
        }
        return result;
    }

    private float wetDepth(int index) {
        return Math.max(MIN_WATER_DEPTH, restDepth[index] + surface[index]);
    }

    private int index(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= height) {
            throw new IndexOutOfBoundsException("Cell outside shallow-water grid: " + x + ", " + z);
        }
        return indexUnchecked(x, z);
    }

    private int indexUnchecked(int x, int z) {
        return z * width + x;
    }
}
