package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereView;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationIntensity;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dimension-owned collection of large atmospheric cells.
 *
 * <p>The grid owns all mutable cell objects and exposes immutable views only.
 * Cell-center samples are bilinearly interpolated so precipitation, wind, and
 * sky contributions do not reveal the square simulation layout.</p>
 */
public final class AtmosphereGrid {

    private final Long2ObjectOpenHashMap<AtmosphereCell> cells = new Long2ObjectOpenHashMap<>();
    private int cellSize;

    /** Creates an empty grid with the supplied horizontal cell width. */
    public AtmosphereGrid(int cellSize) {
        this.cellSize = validateCellSize(cellSize);
    }

    /** Returns the current configured cell width in blocks. */
    public int cellSize() {
        return cellSize;
    }

    /** Returns the number of retained atmospheric cells. */
    public int size() {
        return cells.size();
    }

    /** Returns whether the grid has no retained state. */
    public boolean isEmpty() {
        return cells.isEmpty();
    }

    /**
     * Reinitializes the grid when a server config changes its spatial layout.
     *
     * @return whether existing cells were discarded
     */
    public boolean ensureCellSize(int configuredCellSize) {
        int validated = validateCellSize(configuredCellSize);
        if (validated == cellSize) {
            return false;
        }
        cells.clear();
        cellSize = validated;
        return true;
    }

    /** Returns an existing immutable cell view, or {@code null}. */
    public AtmosphereView view(AtmosphereCellKey key) {
        AtmosphereCell cell = cells.get(Objects.requireNonNull(key, "key").packed());
        return cell == null ? null : cell.view();
    }

    /** Returns an existing view by packed key, or {@code null}. */
    public AtmosphereView view(long packedKey) {
        AtmosphereCell cell = cells.get(packedKey);
        return cell == null ? null : cell.view();
    }

    /** Returns an immutable copy of every retained cell view. */
    public List<AtmosphereView> views() {
        List<AtmosphereView> result = new ArrayList<>(cells.size());
        for (AtmosphereCell cell : cells.values()) {
            result.add(cell.view());
        }
        return List.copyOf(result);
    }

    /**
     * Captures one immutable-view generation keyed for neighborhood simulation.
     *
     * <p>The returned map is detached from the mutable grid, while each value
     * is already an immutable record. Building it directly avoids allocating
     * an intermediate all-cells list on every weather simulation pass.</p>
     */
    Map<Long, AtmosphereView> snapshotByPackedKey() {
        Map<Long, AtmosphereView> result = new HashMap<>(Math.max(16, cells.size() * 2));
        for (AtmosphereCell cell : cells.values()) {
            AtmosphereView view = cell.view();
            result.put(view.key().packed(), view);
        }
        return result;
    }

    /** Returns existing immutable views inside a bounded square region. */
    public Collection<AtmosphereView> viewsInRegion(AtmosphereCellKey center, int radiusCells) {
        Objects.requireNonNull(center, "center");
        int radius = Math.max(0, radiusCells);
        int diameter = radius * 2 + 1;
        List<AtmosphereView> result = new ArrayList<>(diameter * diameter);
        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                AtmosphereView view = view(new AtmosphereCellKey(center.x() + offsetX, center.z() + offsetZ));
                if (view != null) {
                    result.add(view);
                }
            }
        }
        return List.copyOf(result);
    }

    /** Creates a cell only when no authoritative state exists for its key. */
    public AtmosphereView getOrCreate(AtmosphereCellKey key, WeatherSample initial, long gameTick) {
        Objects.requireNonNull(key, "key");
        AtmosphereCell cell = cells.computeIfAbsent(key.packed(), ignored -> new AtmosphereCell(
                key,
                initial,
                0L,
                Math.max(0L, gameTick),
                Math.max(0L, gameTick)
        ));
        return cell.view();
    }

    /** Advances the activity watermark without exposing the mutable cell. */
    public boolean markActive(AtmosphereCellKey key, long gameTick) {
        AtmosphereCell cell = cells.get(Objects.requireNonNull(key, "key").packed());
        if (cell == null) {
            return false;
        }
        cell.markActive(gameTick);
        return true;
    }

    /**
     * Applies a calculation only if no newer simulation or command changed the cell.
     *
     * @return whether the accepted calculation changed weather values and revision
     */
    public boolean applyIfRevision(
            AtmosphereCellKey key,
            long expectedRevision,
            WeatherSample next,
            long gameTick
    ) {
        AtmosphereCell cell = cells.get(Objects.requireNonNull(key, "key").packed());
        return cell != null && cell.applyIfRevision(expectedRevision, next, gameTick);
    }

    /** Replaces a cell for operator/debug control and increments its revision. */
    public boolean force(AtmosphereCellKey key, WeatherSample next, long gameTick) {
        AtmosphereCell cell = cells.get(Objects.requireNonNull(key, "key").packed());
        if (cell == null) {
            return false;
        }
        cell.force(next, gameTick);
        cell.markActive(gameTick);
        return true;
    }

    /** Restores one validated immutable view from persistent storage. */
    public void restore(AtmosphereView view) {
        Objects.requireNonNull(view, "view");
        cells.put(view.key().packed(), new AtmosphereCell(
                view.key(),
                view.sample(),
                view.revision(),
                view.lastSimulatedTick(),
                view.lastActiveTick()
        ));
    }

    /** Removes all cells, used only for config migration and corrupt-data recovery. */
    public void clear() {
        cells.clear();
    }

    /**
     * Evicts the least valuable dormant cells until the configured bound is met.
     *
     * <p>Cells in {@code protectedKeys} are retained. Remaining cells are
     * ordered by storm energy and recent activity so persistent severe systems
     * survive ahead of quiet, long-abandoned state.</p>
     */
    public int trimToLimit(int maximumCells, Set<Long> protectedKeys) {
        int limit = Math.max(1, maximumCells);
        if (cells.size() <= limit) {
            return 0;
        }
        Set<Long> protectedCopy = protectedKeys == null ? Set.of() : Set.copyOf(protectedKeys);
        List<AtmosphereView> candidates = new ArrayList<>(cells.size());
        for (AtmosphereCell cell : cells.values()) {
            AtmosphereView view = cell.view();
            if (!protectedCopy.contains(view.key().packed())) {
                candidates.add(view);
            }
        }
        candidates.sort(Comparator
                .comparingDouble((AtmosphereView view) -> view.sample().stormEnergy())
                .thenComparingLong(AtmosphereView::lastActiveTick));

        int removed = 0;
        for (AtmosphereView candidate : candidates) {
            if (cells.size() <= limit) {
                break;
            }
            if (cells.remove(candidate.key().packed()) != null) {
                removed++;
            }
        }
        return removed;
    }

    /** Samples smoothly at a block position without creating cells. */
    public WeatherSample sample(BlockPos position) {
        Objects.requireNonNull(position, "position");
        return sample(position.getX(), position.getZ());
    }

    /** Samples smoothly at horizontal world coordinates without creating cells. */
    public WeatherSample sample(double blockX, double blockZ) {
        double gridX = blockX / cellSize - 0.5;
        double gridZ = blockZ / cellSize - 0.5;
        int minimumX = floorToInt(gridX);
        int minimumZ = floorToInt(gridZ);
        double xAmount = gridX - minimumX;
        double zAmount = gridZ - minimumZ;

        WeatherSample northWest = sampleAtCell(minimumX, minimumZ);
        WeatherSample northEast = sampleAtCell(minimumX + 1, minimumZ);
        WeatherSample southWest = sampleAtCell(minimumX, minimumZ + 1);
        WeatherSample southEast = sampleAtCell(minimumX + 1, minimumZ + 1);
        WeatherSample north = interpolateAvailable(northWest, northEast, xAmount);
        WeatherSample south = interpolateAvailable(southWest, southEast, xAmount);
        WeatherSample result = interpolateAvailable(north, south, zAmount);
        return result == null ? WeatherSample.CLEAR : result;
    }

    /**
     * Returns interpolated precipitation intensity without allocating samples.
     *
     * <p>This primitive path is used by vanilla's frequent rain-position checks.
     * Missing edge cells retain the nearest available value, matching
     * {@link #sample(double, double)}.</p>
     */
    public double precipitationIntensity(double blockX, double blockZ) {
        double gridX = blockX / cellSize - 0.5;
        double gridZ = blockZ / cellSize - 0.5;
        int minimumX = floorToInt(gridX);
        int minimumZ = floorToInt(gridZ);
        double xAmount = gridX - minimumX;
        double zAmount = gridZ - minimumZ;

        double north = interpolateAvailableScalar(
                precipitation(sampleAtCell(minimumX, minimumZ)),
                precipitation(sampleAtCell(minimumX + 1, minimumZ)),
                xAmount
        );
        double south = interpolateAvailableScalar(
                precipitation(sampleAtCell(minimumX, minimumZ + 1)),
                precipitation(sampleAtCell(minimumX + 1, minimumZ + 1)),
                xAmount
        );
        double result = interpolateAvailableScalar(north, south, zAmount);
        return Double.isNaN(result) ? 0.0 : result;
    }

    /**
     * Returns the canonical gameplay precipitation type without temporary records.
     */
    public PrecipitationType functionalPrecipitationType(double blockX, double blockZ) {
        double gridX = blockX / cellSize - 0.5;
        double gridZ = blockZ / cellSize - 0.5;
        int minimumX = floorToInt(gridX);
        int minimumZ = floorToInt(gridZ);
        double xAmount = gridX - minimumX;
        double zAmount = gridZ - minimumZ;

        WeatherSample northWest = sampleAtCell(minimumX, minimumZ);
        WeatherSample northEast = sampleAtCell(minimumX + 1, minimumZ);
        WeatherSample southWest = sampleAtCell(minimumX, minimumZ + 1);
        WeatherSample southEast = sampleAtCell(minimumX + 1, minimumZ + 1);
        double northTemperature = interpolateAvailableScalar(
                temperature(northWest),
                temperature(northEast),
                xAmount
        );
        double southTemperature = interpolateAvailableScalar(
                temperature(southWest),
                temperature(southEast),
                xAmount
        );
        // The network quantizes cell endpoints before the client interpolates
        // them. Canonicalizing each endpoint here keeps physical rain/snow
        // classification identical between server and client prediction.
        double northIntensity = interpolateAvailableScalar(
                canonicalPrecipitation(northWest),
                canonicalPrecipitation(northEast),
                xAmount
        );
        double southIntensity = interpolateAvailableScalar(
                canonicalPrecipitation(southWest),
                canonicalPrecipitation(southEast),
                xAmount
        );
        double intensity = interpolateAvailableScalar(northIntensity, southIntensity, zAmount);
        if (Double.isNaN(intensity) || !PrecipitationIntensity.isFunctional(intensity)) {
            return PrecipitationType.NONE;
        }

        PrecipitationType northType = interpolateAvailableType(
                canonicalType(northWest),
                canonicalType(northEast),
                northTemperature,
                northIntensity,
                xAmount
        );
        PrecipitationType southType = interpolateAvailableType(
                canonicalType(southWest),
                canonicalType(southEast),
                southTemperature,
                southIntensity,
                xAmount
        );
        PrecipitationType result = interpolateAvailableType(
                northType,
                southType,
                interpolateAvailableScalar(northTemperature, southTemperature, zAmount),
                intensity,
                zAmount
        );
        return result == null ? PrecipitationType.NONE : result;
    }

    private WeatherSample sampleAtCell(int cellX, int cellZ) {
        AtmosphereCell cell = cells.get(packCell(cellX, cellZ));
        return cell == null ? null : cell.sample();
    }

    private static WeatherSample interpolateAvailable(WeatherSample first, WeatherSample second, double amount) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return WeatherSample.interpolate(first, second, amount);
    }

    private static double interpolateAvailableScalar(double first, double second, double amount) {
        if (Double.isNaN(first)) {
            return second;
        }
        if (Double.isNaN(second)) {
            return first;
        }
        return first + (second - first) * amount;
    }

    private static PrecipitationType interpolateAvailableType(
            PrecipitationType first,
            PrecipitationType second,
            double temperature,
            double intensity,
            double amount
    ) {
        if (first == null && second == null) {
            return null;
        }
        if (!Double.isFinite(intensity) || intensity <= 0.0) {
            return PrecipitationType.NONE;
        }
        if (first == null) {
            return second;
        }
        if (second == null || first == second) {
            return first;
        }
        if (first == PrecipitationType.HAIL || second == PrecipitationType.HAIL) {
            return amount < 0.5 ? first : second;
        }
        if (first == PrecipitationType.NONE) {
            return second;
        }
        if (second == PrecipitationType.NONE) {
            return first;
        }
        if ((first == PrecipitationType.SNOW && second == PrecipitationType.RAIN)
                || (first == PrecipitationType.RAIN && second == PrecipitationType.SNOW)) {
            return temperature <= WeatherSample.SNOW_MAX_TEMPERATURE
                    ? PrecipitationType.SNOW
                    : PrecipitationType.RAIN;
        }
        return amount < 0.5 ? first : second;
    }

    private static double precipitation(WeatherSample sample) {
        return sample == null ? Double.NaN : sample.precipitationIntensity();
    }

    private static double canonicalPrecipitation(WeatherSample sample) {
        return sample == null
                ? Double.NaN
                : PrecipitationIntensity.dequantize(
                        PrecipitationIntensity.quantize(sample.precipitationIntensity())
                );
    }

    private static double temperature(WeatherSample sample) {
        return sample == null ? Double.NaN : sample.temperature();
    }

    private static PrecipitationType canonicalType(WeatherSample sample) {
        if (sample == null) {
            return null;
        }
        return PrecipitationIntensity.quantize(sample.precipitationIntensity()) == 0
                ? PrecipitationType.NONE
                : sample.precipitationType();
    }

    private static long packCell(int cellX, int cellZ) {
        return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
    }

    private static int floorToInt(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private static int validateCellSize(int value) {
        if (value < 16 || value > 4096) {
            throw new IllegalArgumentException("Atmospheric cell size must be between 16 and 4096 blocks");
        }
        return value;
    }
}
