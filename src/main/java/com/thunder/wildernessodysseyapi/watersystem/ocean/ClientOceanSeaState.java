package com.thunder.wildernessodysseyapi.watersystem.ocean;

import com.thunder.wildernessodysseyapi.watersystem.water.network.OceanSeaStatePayload;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Holds spatially and temporally interpolated regional sea-state snapshots.
 *
 * <p>This class deliberately uses only common Minecraft types so network
 * registration remains dedicated-server safe. All mutations occur on the
 * client main thread through enqueued packet and tick handlers.</p>
 */
public final class ClientOceanSeaState {

    private static final float SNAPSHOT_INTERPOLATION = 0.12f;
    private static final Map<Level, RegionalState> STATES = new IdentityHashMap<>();

    private ClientOceanSeaState() {
    }

    /** Replaces the active regional targets with one authoritative payload. */
    public static void accept(Level level, OceanSeaStatePayload payload) {
        if (level == null || payload == null) {
            return;
        }
        RegionalState regional = STATES.computeIfAbsent(level, ignored -> new RegionalState());
        regional.localized = payload.localized();
        regional.cellSize = payload.cellSize();
        if (!payload.localized()) {
            regional.cells.clear();
            return;
        }

        Set<Long> retained = new HashSet<>(payload.cells().size());
        for (OceanSeaStatePayload.CellSnapshot cell : payload.cells()) {
            long key = cell.packedKey();
            retained.add(key);
            CellState state = regional.cells.get(key);
            if (state == null) {
                regional.cells.put(key, new CellState(cell.sample(), cell.sample()));
            } else {
                state.target = cell.sample();
            }
        }
        regional.cells.keySet().retainAll(retained);
    }

    /** Smoothly approaches all received targets once per client tick. */
    public static void tick(Level level) {
        RegionalState regional = STATES.get(level);
        if (regional == null || !regional.localized) {
            return;
        }
        for (CellState state : regional.cells.values()) {
            state.current = state.current.interpolate(
                    state.target,
                    SNAPSHOT_INTERPOLATION
            );
        }
    }

    /** Returns the spatially interpolated sea state at world coordinates. */
    public static OceanSeaState.Sample sampleAt(Level level, double worldX, double worldZ) {
        RegionalState regional = STATES.get(level);
        if (regional == null || !regional.localized || regional.cells.isEmpty()) {
            return level == null
                    ? OceanSeaState.CALM
                    : OceanSeaState.vanillaFallback(level, 0.0f);
        }
        double gridX = worldX / regional.cellSize - 0.5;
        double gridZ = worldZ / regional.cellSize - 0.5;
        int minimumX = floorToInt(gridX);
        int minimumZ = floorToInt(gridZ);
        float blendX = (float) (gridX - minimumX);
        float blendZ = (float) (gridZ - minimumZ);
        OceanSeaState.Sample northWest = regional.sample(minimumX, minimumZ, worldX, worldZ);
        OceanSeaState.Sample northEast = regional.sample(minimumX + 1, minimumZ, worldX, worldZ);
        OceanSeaState.Sample southWest = regional.sample(minimumX, minimumZ + 1, worldX, worldZ);
        OceanSeaState.Sample southEast = regional.sample(minimumX + 1, minimumZ + 1, worldX, worldZ);
        OceanSeaState.Sample north = northWest.interpolate(northEast, blendX);
        OceanSeaState.Sample south = southWest.interpolate(southEast, blendX);
        return north.interpolate(south, blendZ);
    }

    /** Returns whether a regional server field currently owns sea response. */
    public static boolean controls(Level level) {
        RegionalState state = STATES.get(level);
        return state != null && state.localized && !state.cells.isEmpty();
    }

    /**
     * Compatibility alias for old camera-global callers.
     *
     * <p>New code should call {@link #sampleAt(Level, double, double)} with its
     * actual render or physics position.</p>
     */
    @Deprecated(forRemoval = false)
    public static OceanSeaState.Sample current(Level level) {
        return sampleAt(level, 0.0, 0.0);
    }

    /** Releases client-world identity state during disconnect or dimension unload. */
    public static void clear(Level level) {
        STATES.remove(level);
    }

    private static int floorToInt(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private static long packed(int cellX, int cellZ) {
        return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
    }

    private static final class RegionalState {
        private final Map<Long, CellState> cells = new HashMap<>();
        private boolean localized;
        private int cellSize = 128;

        private OceanSeaState.Sample sample(
                int cellX,
                int cellZ,
                double worldX,
                double worldZ
        ) {
            CellState exact = cells.get(packed(cellX, cellZ));
            if (exact != null) {
                return exact.current;
            }

            // A packet edge may temporarily omit one interpolation corner.
            // Use the nearest received center instead of producing a hard calm seam.
            CellState nearest = null;
            double nearestDistance = Double.POSITIVE_INFINITY;
            for (Map.Entry<Long, CellState> entry : cells.entrySet()) {
                int x = (int) (entry.getKey() >> 32);
                int z = (int) (long) entry.getKey();
                double centerX = (x + 0.5) * cellSize;
                double centerZ = (z + 0.5) * cellSize;
                double distance = (centerX - worldX) * (centerX - worldX)
                        + (centerZ - worldZ) * (centerZ - worldZ);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = entry.getValue();
                }
            }
            return nearest == null ? OceanSeaState.CALM : nearest.current;
        }
    }

    private static final class CellState {
        private OceanSeaState.Sample current;
        private OceanSeaState.Sample target;

        private CellState(OceanSeaState.Sample current, OceanSeaState.Sample target) {
            this.current = current;
            this.target = target;
        }
    }
}
