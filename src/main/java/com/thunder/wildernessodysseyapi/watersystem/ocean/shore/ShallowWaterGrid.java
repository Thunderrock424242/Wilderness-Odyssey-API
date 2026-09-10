package com.thunder.wildernessodysseyapi.watersystem.ocean.shore;

import java.util.Arrays;

/**
 * Conservative, depth-averaged local-inertial shoreline current model.
 *
 * <p>Each face owns one discharge shared by its two cells. A donor limiter
 * makes all of a cell's simultaneous outflows fit its available water, without
 * independently clipping cell elevations. Bed steps use a reconstructed face
 * depth, so resting water stays still and wet water can reach dry tidal flats.
 * Gravity and drag evolve face momentum; nonlinear momentum advection and
 * breaking-wave turbulence are left to the existing wave/SPH models. This is
 * not a full Navier-Stokes or shock-capturing solver.</p>
 *
 * <p>The represented cubic-block volume is diagnostic, not canonical water
 * ownership. Ocean boundary exchanges and bathymetry admission/removal are
 * explicitly counted. No method edits world blocks or claims watershed water.</p>
 */
public final class ShallowWaterGrid {

    private static final double GRAVITY = 9.81;
    private static final double CFL_NUMBER = 0.42;
    private static final double MIN_MOVING_DEPTH = 1.0e-8;
    private static final double BOTTOM_FRICTION = 0.85;
    private static final double MAX_FLOW_SPEED = 8.0;
    private static final double MAX_BOUNDARY_OFFSET = 4.0;
    private static final int MAX_SUBSTEPS = 32;

    private final int width;
    private final int height;
    private final double cellSize;
    private final double cellArea;
    private final double[] bed;
    private final double[] depth;
    private final boolean[] wettable;
    private final int[] negativeCell;
    private final int[] positiveCell;
    private final boolean[] boundaryOpen;
    private final double[] discharge;
    private final double[] outgoing;
    private final double[] incoming;
    private final double[] donorScale;
    private double boundaryInflow;
    private double boundaryOutflow;
    private double bathymetryExchange;
    private double pendingSeconds;
    private double simulatedSeconds;

    /** Creates a bounded regular grid with initially blocked cells and open outer edges. */
    public ShallowWaterGrid(int width, int height, float cellSize) {
        if (width < 3 || height < 3 || width > 257 || height > 257) {
            throw new IllegalArgumentException("A shallow-water grid requires 3..257 cells per axis");
        }
        if (!Float.isFinite(cellSize) || cellSize < 0.25f) {
            throw new IllegalArgumentException("Cell size must be finite and at least 0.25 blocks");
        }
        this.width = width;
        this.height = height;
        this.cellSize = cellSize;
        this.cellArea = (double) cellSize * cellSize;
        int cellCount = width * height;
        bed = new double[cellCount];
        depth = new double[cellCount];
        wettable = new boolean[cellCount];
        outgoing = new double[cellCount];
        incoming = new double[cellCount];
        donorScale = new double[cellCount];
        int faceCount = (width + 1) * height + width * (height + 1);
        negativeCell = new int[faceCount];
        positiveCell = new int[faceCount];
        boundaryOpen = new boolean[faceCount];
        discharge = new double[faceCount];
        Arrays.fill(boundaryOpen, true);
        for (int z = 0; z < height; z++) {
            for (int x = 0; x <= width; x++) {
                int face = xFace(x, z);
                negativeCell[face] = x == 0 ? -1 : z * width + x - 1;
                positiveCell[face] = x == width ? -1 : z * width + x;
            }
        }
        for (int z = 0; z <= height; z++) {
            for (int x = 0; x < width; x++) {
                int face = zFace(x, z);
                negativeCell[face] = z == 0 ? -1 : (z - 1) * width + x;
                positiveCell[face] = z == height ? -1 : z * width + x;
            }
        }
    }

    /**
     * Sets a below-datum bed; zero retains the legacy meaning of blocked land.
     * Use {@link #setTerrain(int, int, float, float)} for a wettable dry cell.
     * Existing water depth is conserved when an admitted bed changes.
     */
    public void setRestDepth(int x, int z, float restDepth) {
        float boundedDepth = (float) bounded(restDepth, 0.0, 64.0);
        if (boundedDepth <= 0.01f) {
            setBlocked(x, z);
        } else {
            setTerrain(x, z, -boundedDepth, boundedDepth);
        }
    }

    /**
     * Admits known terrain, including initially dry beach cells. Bed elevation
     * is relative to the shared ocean datum. Initial depth is used only on
     * first admission; later bed refreshes do not refill or drain the cell.
     */
    public void setTerrain(int x, int z, float bedElevation, float initialDepth) {
        int cell = index(x, z);
        double nextBed = bounded(bedElevation, -64.0, 64.0);
        if (!wettable[cell]) {
            depth[cell] = bounded(initialDepth, 0.0, 64.0);
            bathymetryExchange += depth[cell] * cellArea;
            wettable[cell] = true;
        }
        if (bed[cell] != nextBed) clearCellDischarge(x, z);
        bed[cell] = nextBed;
    }

    /** Closes unknown or solid terrain, accounting for removal of its represented volume. */
    public void setBlocked(int x, int z) {
        int cell = index(x, z);
        bathymetryExchange -= depth[cell] * cellArea;
        depth[cell] = 0.0;
        bed[cell] = 0.0;
        wettable[cell] = false;
        clearCellDischarge(x, z);
    }

    /** Opens or closes one complete exterior side without changing stored water. */
    public void setBoundaryOpen(Side side, boolean open) {
        int length = side == Side.WEST || side == Side.EAST ? height : width;
        for (int coordinate = 0; coordinate < length; coordinate++) {
            setBoundaryOpen(side, coordinate, open);
        }
    }

    /** Opens only a known ocean-facing boundary cell; unknown neighbours should remain closed. */
    public void setBoundaryOpen(Side side, int coordinate, boolean open) {
        int length = side == Side.WEST || side == Side.EAST ? height : width;
        if (coordinate < 0 || coordinate >= length) throw new IndexOutOfBoundsException(coordinate);
        int face = switch (side) {
            case WEST -> xFace(0, coordinate);
            case EAST -> xFace(width, coordinate);
            case NORTH -> zFace(coordinate, 0);
            case SOUTH -> zFace(coordinate, height);
        };
        boundaryOpen[face] = open;
        if (!open) discharge[face] = 0.0;
    }

    /** Adds bounded horizontal momentum, never water, to a wet cell's adjoining faces. */
    public void addImpulse(int x, int z, float impulseX, float impulseZ) {
        int cell = index(x, z);
        if (!wettable[cell] || depth[cell] <= MIN_MOVING_DEPTH) return;
        double impulseDischargeX = bounded(impulseX, -MAX_FLOW_SPEED, MAX_FLOW_SPEED) * depth[cell];
        double impulseDischargeZ = bounded(impulseZ, -MAX_FLOW_SPEED, MAX_FLOW_SPEED) * depth[cell];
        discharge[xFace(x, z)] += impulseDischargeX;
        discharge[xFace(x + 1, z)] += impulseDischargeX;
        discharge[zFace(x, z)] += impulseDischargeZ;
        discharge[zFace(x, z + 1)] += impulseDischargeZ;
    }

    /**
     * Adds elapsed time and performs at most 32 dynamically CFL-limited steps.
     * Unprocessed time remains queued; a zero-time call may drain that queue.
     * The supplied ocean level is held constant during this bounded advance.
     */
    public void step(float deltaSeconds, float boundarySurface) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0.0f) return;
        pendingSeconds += deltaSeconds;
        double boundary = bounded(boundarySurface, -MAX_BOUNDARY_OFFSET, MAX_BOUNDARY_OFFSET);
        for (int substep = 0; substep < MAX_SUBSTEPS && pendingSeconds > 0.0; substep++) {
            double seconds = Math.min(pendingSeconds, stableStepSeconds(boundary));
            solveSubstep(seconds, boundary);
            pendingSeconds -= seconds;
            simulatedSeconds += seconds;
        }
    }

    /** Returns surface elevation relative to the shared ocean datum, or zero for a dry cell. */
    public float surface(int x, int z) {
        int cell = index(x, z);
        return depth[cell] > MIN_MOVING_DEPTH ? (float) (bed[cell] + depth[cell]) : 0.0f;
    }

    /** Returns depth-averaged X velocity in blocks per second. */
    public float velocityX(int x, int z) {
        return sampleVelocity(index(x, z), xFace(x, z), xFace(x + 1, z));
    }

    /** Returns depth-averaged Z velocity in blocks per second. */
    public float velocityZ(int x, int z) {
        return sampleVelocity(index(x, z), zFace(x, z), zFace(x, z + 1));
    }

    /** Returns undisturbed below-datum bathymetric depth in blocks. */
    public float restDepth(int x, int z) {
        return (float) Math.max(0.0, -bed[index(x, z)]);
    }

    /** Returns actual represented depth, including the current wet/dry transition. */
    public float waterDepth(int x, int z) {
        return (float) depth[index(x, z)];
    }

    /** Returns the derived solver's balance, not a second claim on canonical water. */
    public Balance balance() {
        double volume = 0.0;
        for (double cellDepth : depth) volume += cellDepth * cellArea;
        return new Balance(volume, boundaryInflow, boundaryOutflow, bathymetryExchange,
                volume - (bathymetryExchange + boundaryInflow - boundaryOutflow),
                pendingSeconds, simulatedSeconds);
    }

    /**
     * Restores still water at the sampled datum and records the resulting
     * resampling exchange. Pending elapsed time is retained, not discarded.
     */
    public void resetMotion() {
        Arrays.fill(discharge, 0.0);
        for (int cell = 0; cell < depth.length; cell++) {
            double nextDepth = wettable[cell] ? Math.max(0.0, -bed[cell]) : 0.0;
            bathymetryExchange += (nextDepth - depth[cell]) * cellArea;
            depth[cell] = nextDepth;
        }
    }

    private void solveSubstep(double seconds, double boundarySurface) {
        Arrays.fill(outgoing, 0.0);
        Arrays.fill(incoming, 0.0);
        double transportScale = seconds / cellSize;
        double drag = Math.exp(-BOTTOM_FRICTION * seconds);
        for (int face = 0; face < discharge.length; face++) {
            discharge[face] = updateDischarge(face, seconds, boundarySurface, drag);
            int donor = discharge[face] >= 0.0 ? negativeCell[face] : positiveCell[face];
            if (donor >= 0) outgoing[donor] += Math.abs(discharge[face]) * transportScale;
        }
        for (int cell = 0; cell < depth.length; cell++) {
            // One common limiter applies to all outgoing faces of a donor.
            // The one-ULP margin avoids a negative remainder from summation.
            donorScale[cell] = outgoing[cell] > depth[cell]
                    ? Math.max(0.0, Math.nextDown(depth[cell] / outgoing[cell])) : 1.0;
        }
        Arrays.fill(outgoing, 0.0);
        for (int face = 0; face < discharge.length; face++) {
            boolean positive = discharge[face] >= 0.0;
            int donor = positive ? negativeCell[face] : positiveCell[face];
            int receiver = positive ? positiveCell[face] : negativeCell[face];
            if (donor >= 0) discharge[face] *= donorScale[donor];
            double transportedDepth = Math.abs(discharge[face]) * transportScale;
            if (donor >= 0) outgoing[donor] += transportedDepth;
            else boundaryInflow += transportedDepth * cellArea;
            if (receiver >= 0) incoming[receiver] += transportedDepth;
            else boundaryOutflow += transportedDepth * cellArea;
        }
        for (int cell = 0; cell < depth.length; cell++) {
            // Only floating-point roundoff can cross zero after donor limiting.
            // Do not impose a minimum wet depth or upper height clamp: both
            // would silently create or remove water at a moving shoreline.
            depth[cell] = Math.max(0.0, depth[cell] - outgoing[cell]) + incoming[cell];
        }
    }

    private double updateDischarge(int face, double seconds, double boundarySurface, double drag) {
        int negative = negativeCell[face];
        int positive = positiveCell[face];
        if ((negative >= 0 && !wettable[negative]) || (positive >= 0 && !wettable[positive])
                || ((negative < 0 || positive < 0) && !boundaryOpen[face])) return 0.0;
        double negativeBed = bed[negative >= 0 ? negative : positive];
        double positiveBed = bed[positive >= 0 ? positive : negative];
        double negativeSurface = negative >= 0 ? negativeBed + depth[negative]
                : Math.max(negativeBed, boundarySurface);
        double positiveSurface = positive >= 0 ? positiveBed + depth[positive]
                : Math.max(positiveBed, boundarySurface);
        double faceDepth = Math.max(0.0,
                Math.max(negativeSurface, positiveSurface) - Math.max(negativeBed, positiveBed));
        if (faceDepth <= MIN_MOVING_DEPTH) return 0.0;
        double gradient = (positiveSurface - negativeSurface) / cellSize;
        double result = (discharge[face] - GRAVITY * faceDepth * gradient * seconds) * drag;
        return Math.max(-MAX_FLOW_SPEED * faceDepth, Math.min(MAX_FLOW_SPEED * faceDepth, result));
    }

    private double stableStepSeconds(double boundarySurface) {
        double maximumDepth = MIN_MOVING_DEPTH;
        for (int cell = 0; cell < depth.length; cell++) {
            if (wettable[cell]) maximumDepth = Math.max(maximumDepth,
                    Math.max(depth[cell], boundarySurface - bed[cell]));
        }
        // The factor two accounts for simultaneous X/Z gravity-wave transport.
        return CFL_NUMBER * cellSize / (2.0 * (MAX_FLOW_SPEED + Math.sqrt(GRAVITY * maximumDepth)));
    }

    private float sampleVelocity(int cell, int firstFace, int secondFace) {
        if (depth[cell] <= MIN_MOVING_DEPTH) return 0.0f;
        double velocity = (discharge[firstFace] + discharge[secondFace]) / (2.0 * depth[cell]);
        return (float) Math.max(-MAX_FLOW_SPEED, Math.min(MAX_FLOW_SPEED, velocity));
    }

    private void clearCellDischarge(int x, int z) {
        discharge[xFace(x, z)] = 0.0;
        discharge[xFace(x + 1, z)] = 0.0;
        discharge[zFace(x, z)] = 0.0;
        discharge[zFace(x, z + 1)] = 0.0;
    }

    private int xFace(int x, int z) { return z * (width + 1) + x; }
    private int zFace(int x, int z) { return (width + 1) * height + z * width + x; }

    private int index(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= height) {
            throw new IndexOutOfBoundsException("Cell outside shallow-water grid: " + x + ", " + z);
        }
        return z * width + x;
    }

    private static double bounded(float value, double minimum, double maximum) {
        return Float.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : 0.0;
    }

    /** Exterior face orientation; north is negative world Z. */
    public enum Side { WEST, EAST, NORTH, SOUTH }

    /** Cumulative cubic-block exchanges and retained time for conservation diagnostics. */
    public record Balance(double representedVolume, double boundaryInflow, double boundaryOutflow,
                          double bathymetryExchange, double residual,
                          double deferredSeconds, double simulatedSeconds) { }
}
