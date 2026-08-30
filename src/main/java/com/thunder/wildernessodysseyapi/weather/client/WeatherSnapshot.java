package com.thunder.wildernessodysseyapi.weather.client;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.client.cloud.CloudFieldSample;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable client-side copy of one server-synchronized atmospheric region.
 *
 * <p>Cell samples are stored at atmospheric-cell centers. Queries use bilinear
 * interpolation across the four surrounding centers, while missing edge
 * neighbors reuse the nearest available value so the bounded network region
 * does not visibly fade before the next regional replacement arrives.</p>
 */
public final class WeatherSnapshot {

    private static final int TEMPERATURE_FIELD = 0;
    private static final int PRECIPITATION_FIELD = 1;

    private final ResourceLocation dimension;
    private final int dataVersion;
    private final long sequence;
    private final long serverTick;
    private final int cellSize;
    private final Long2ObjectMap<SnapshotCell> cells;

    WeatherSnapshot(
            ResourceLocation dimension,
            int dataVersion,
            long sequence,
            long serverTick,
            int cellSize,
            Map<Long, SnapshotCell> cells
    ) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.dataVersion = dataVersion;
        this.sequence = sequence;
        this.serverTick = Math.max(0L, serverTick);
        this.cellSize = cellSize;
        Map<Long, SnapshotCell> sourceCells = Objects.requireNonNull(cells, "cells");
        Long2ObjectOpenHashMap<SnapshotCell> copiedCells = new Long2ObjectOpenHashMap<>(sourceCells.size());
        for (Map.Entry<Long, SnapshotCell> entry : sourceCells.entrySet()) {
            copiedCells.put(entry.getKey().longValue(), entry.getValue());
        }
        this.cells = Long2ObjectMaps.unmodifiable(copiedCells);
    }

    /** Retains the weather-v4 snapshot construction shape for focused tests. */
    WeatherSnapshot(
            ResourceLocation dimension,
            int dataVersion,
            long sequence,
            int cellSize,
            Map<Long, SnapshotCell> cells
    ) {
        this(dimension, dataVersion, sequence, 0L, cellSize, cells);
    }

    /** Returns the dimension whose authoritative cells this snapshot contains. */
    public ResourceLocation dimension() {
        return dimension;
    }

    /** Returns the wire/persistence schema version used to construct the snapshot. */
    public int dataVersion() {
        return dataVersion;
    }

    /** Returns the newest accepted server sequence represented by this snapshot. */
    public long sequence() {
        return sequence;
    }

    /** Returns the authoritative level tick captured with this region. */
    public long serverTick() {
        return serverTick;
    }

    /** Returns the atmospheric-cell width in blocks. */
    public int cellSize() {
        return cellSize;
    }

    /** Returns the number of synchronized atmospheric cells. */
    public int cellCount() {
        return cells.size();
    }

    /**
     * Samples this region at world coordinates using center-based bilinear interpolation.
     */
    public WeatherSample sample(double blockX, double blockZ) {
        double gridX = blockX / cellSize - 0.5D;
        double gridZ = blockZ / cellSize - 0.5D;
        int minimumCellX = floorToInt(gridX);
        int minimumCellZ = floorToInt(gridZ);
        double xAmount = gridX - minimumCellX;
        double zAmount = gridZ - minimumCellZ;

        WeatherSample northWest = sampleInCell(minimumCellX, minimumCellZ);
        WeatherSample northEast = sampleInCell(minimumCellX + 1, minimumCellZ);
        WeatherSample southWest = sampleInCell(minimumCellX, minimumCellZ + 1);
        WeatherSample southEast = sampleInCell(minimumCellX + 1, minimumCellZ + 1);

        WeatherSample north = interpolateAvailable(northWest, northEast, xAmount);
        WeatherSample south = interpolateAvailable(southWest, southEast, xAmount);
        WeatherSample result = interpolateAvailable(north, south, zAmount);
        return result == null ? WeatherSample.CLEAR : result;
    }

    /**
     * Samples only the atmospheric fields needed to construct cloud geometry.
     *
     * <p>Unlike general gameplay sampling, this path retains the fraction of
     * the bilinear footprint backed by synchronized cells. The renderer can
     * therefore end a bounded cloud field cleanly instead of repeating its
     * nearest edge cell across the horizon.</p>
     */
    public CloudFieldSample cloudField(double blockX, double blockZ) {
        double gridX = blockX / cellSize - 0.5D;
        double gridZ = blockZ / cellSize - 0.5D;
        int minimumCellX = floorToInt(gridX);
        int minimumCellZ = floorToInt(gridZ);
        double xAmount = gridX - minimumCellX;
        double zAmount = gridZ - minimumCellZ;

        return CloudFieldSample.spatial(
                sampleInCell(minimumCellX, minimumCellZ),
                sampleInCell(minimumCellX + 1, minimumCellZ),
                sampleInCell(minimumCellX, minimumCellZ + 1),
                sampleInCell(minimumCellX + 1, minimumCellZ + 1),
                xAmount,
                zAmount
        );
    }

    /**
     * Returns precipitation intensity without constructing interpolated sample records.
     *
     * <p>The vanilla precipitation renderer asks for a classification at many
     * columns per frame, so this scalar path keeps that loop allocation-light.</p>
     */
    double precipitationIntensity(double blockX, double blockZ) {
        return sampleScalar(blockX, blockZ, PRECIPITATION_FIELD, 0.0);
    }

    /**
     * Returns visual precipitation with missing synchronized cells treated as clear.
     *
     * <p>The general scalar path repeats the nearest edge value to keep gameplay
     * queries stable. Renderers instead use this weighted path so rain, cloud
     * cover, and distant shafts fade together at the bounded payload edge.</p>
     */
    double supportedPrecipitationIntensity(double blockX, double blockZ) {
        double gridX = blockX / cellSize - 0.5D;
        double gridZ = blockZ / cellSize - 0.5D;
        int minimumCellX = floorToInt(gridX);
        int minimumCellZ = floorToInt(gridZ);
        double xAmount = gridX - minimumCellX;
        double zAmount = gridZ - minimumCellZ;

        double northWest = clearIfMissing(
                scalarInCell(minimumCellX, minimumCellZ, PRECIPITATION_FIELD)
        );
        double northEast = clearIfMissing(
                scalarInCell(minimumCellX + 1, minimumCellZ, PRECIPITATION_FIELD)
        );
        double southWest = clearIfMissing(
                scalarInCell(minimumCellX, minimumCellZ + 1, PRECIPITATION_FIELD)
        );
        double southEast = clearIfMissing(
                scalarInCell(minimumCellX + 1, minimumCellZ + 1, PRECIPITATION_FIELD)
        );
        double north = northWest + (northEast - northWest) * xAmount;
        double south = southWest + (southEast - southWest) * xAmount;
        return north + (south - north) * zAmount;
    }

    /** Returns interpolated temperature through the renderer's scalar query path. */
    double temperature(double blockX, double blockZ) {
        return sampleScalar(blockX, blockZ, TEMPERATURE_FIELD, WeatherSample.CLEAR.temperature());
    }

    /**
     * Interpolates the authoritative categorical precipitation field without
     * constructing temporary weather records in the render-column hot path.
     */
    PrecipitationType precipitationType(double blockX, double blockZ) {
        double gridX = blockX / cellSize - 0.5D;
        double gridZ = blockZ / cellSize - 0.5D;
        int minimumCellX = floorToInt(gridX);
        int minimumCellZ = floorToInt(gridZ);
        double xAmount = gridX - minimumCellX;
        double zAmount = gridZ - minimumCellZ;

        WeatherSample northWest = sampleInCell(minimumCellX, minimumCellZ);
        WeatherSample northEast = sampleInCell(minimumCellX + 1, minimumCellZ);
        WeatherSample southWest = sampleInCell(minimumCellX, minimumCellZ + 1);
        WeatherSample southEast = sampleInCell(minimumCellX + 1, minimumCellZ + 1);

        double northTemperature = interpolateAvailableScalar(
                scalar(northWest, TEMPERATURE_FIELD),
                scalar(northEast, TEMPERATURE_FIELD),
                xAmount
        );
        double southTemperature = interpolateAvailableScalar(
                scalar(southWest, TEMPERATURE_FIELD),
                scalar(southEast, TEMPERATURE_FIELD),
                xAmount
        );
        double northIntensity = interpolateAvailableScalar(
                scalar(northWest, PRECIPITATION_FIELD),
                scalar(northEast, PRECIPITATION_FIELD),
                xAmount
        );
        double southIntensity = interpolateAvailableScalar(
                scalar(southWest, PRECIPITATION_FIELD),
                scalar(southEast, PRECIPITATION_FIELD),
                xAmount
        );
        PrecipitationType northType = interpolateAvailableType(
                type(northWest),
                type(northEast),
                northTemperature,
                northIntensity
        );
        PrecipitationType southType = interpolateAvailableType(
                type(southWest),
                type(southEast),
                southTemperature,
                southIntensity
        );
        return interpolateAvailableType(
                northType,
                southType,
                interpolateAvailableScalar(northTemperature, southTemperature, zAmount),
                interpolateAvailableScalar(northIntensity, southIntensity, zAmount)
        );
    }

    /**
     * Produces an immutable transition snapshot without mutating either endpoint.
     *
     * <p>This work happens only when a payload arrives, never once per frame. It
     * lets the coordinator start a new transition from the currently displayed
     * state even when server updates arrive faster than the visual blend time.</p>
     */
    static WeatherSnapshot blend(WeatherSnapshot from, WeatherSnapshot to, double amount) {
        return timeline(from, to, clamp01(amount));
    }

    /** Builds a packet-boundary snapshot from interpolation or bounded extrapolation. */
    static WeatherSnapshot timeline(WeatherSnapshot from, WeatherSnapshot to, double amount) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (!from.dimension.equals(to.dimension) || from.cellSize != to.cellSize) {
            return to;
        }

        double boundedAmount = Math.max(
                0.0D,
                Math.min(1.0D + ClientWeatherTimeline.MAX_EXTRAPOLATION, amount)
        );
        if (boundedAmount <= 0.0D) {
            return from;
        }
        if (boundedAmount == 1.0D) {
            return to;
        }

        Long2ObjectOpenHashMap<SnapshotCell> blendedCells = new Long2ObjectOpenHashMap<>(
                Math.max(from.cells.size(), to.cells.size()) * 2
        );
        for (Long2ObjectMap.Entry<SnapshotCell> entry : to.cells.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            SnapshotCell target = entry.getValue();
            SnapshotCell source = from.cells.get(key);
            WeatherSample sourceSample = source == null ? WeatherSample.CLEAR : source.sample();
            blendedCells.put(key, new SnapshotCell(
                    target.cellX(),
                    target.cellZ(),
                    source == null ? target.revision() : Math.max(source.revision(), target.revision()),
                    ClientWeatherTimeline.sample(sourceSample, target.sample(), boundedAmount)
            ));
        }
        for (Long2ObjectMap.Entry<SnapshotCell> entry : from.cells.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            if (to.cells.containsKey(key)) {
                continue;
            }
            SnapshotCell source = entry.getValue();
            blendedCells.put(key, new SnapshotCell(
                    source.cellX(),
                    source.cellZ(),
                    source.revision(),
                    ClientWeatherTimeline.sample(source.sample(), WeatherSample.CLEAR, boundedAmount)
            ));
        }
        return new WeatherSnapshot(
                to.dimension,
                to.dataVersion,
                to.sequence,
                to.serverTick,
                to.cellSize,
                blendedCells
        );
    }

    static long packCell(int cellX, int cellZ) {
        return ((long) cellX & 0xFFFFFFFFL) | ((long) cellZ << 32);
    }

    Map<Long, SnapshotCell> mutableCellCopy() {
        Map<Long, SnapshotCell> copy = new HashMap<>(cells.size() * 2);
        for (Long2ObjectMap.Entry<SnapshotCell> entry : cells.long2ObjectEntrySet()) {
            copy.put(entry.getLongKey(), entry.getValue());
        }
        return copy;
    }

    /** Returns the fraction of this snapshot's cells also present in another bounded region. */
    double sharedCellFraction(WeatherSnapshot other) {
        if (other == null || cells.isEmpty()) {
            return 0.0D;
        }
        int shared = 0;
        for (long key : cells.keySet()) {
            if (other.cells.containsKey(key)) {
                shared++;
            }
        }
        return shared / (double) Math.max(cells.size(), other.cells.size());
    }

    SnapshotCell cell(int cellX, int cellZ) {
        return cells.get(packCell(cellX, cellZ));
    }

    private WeatherSample sampleInCell(int cellX, int cellZ) {
        SnapshotCell cell = cell(cellX, cellZ);
        return cell == null ? null : cell.sample();
    }

    private double sampleScalar(
            double blockX,
            double blockZ,
            int field,
            double fallback
    ) {
        double gridX = blockX / cellSize - 0.5D;
        double gridZ = blockZ / cellSize - 0.5D;
        int minimumCellX = floorToInt(gridX);
        int minimumCellZ = floorToInt(gridZ);
        double xAmount = gridX - minimumCellX;
        double zAmount = gridZ - minimumCellZ;

        double northWest = scalarInCell(minimumCellX, minimumCellZ, field);
        double northEast = scalarInCell(minimumCellX + 1, minimumCellZ, field);
        double southWest = scalarInCell(minimumCellX, minimumCellZ + 1, field);
        double southEast = scalarInCell(minimumCellX + 1, minimumCellZ + 1, field);
        double north = interpolateAvailableScalar(northWest, northEast, xAmount);
        double south = interpolateAvailableScalar(southWest, southEast, xAmount);
        double result = interpolateAvailableScalar(north, south, zAmount);
        return Double.isNaN(result) ? fallback : result;
    }

    private double scalarInCell(int cellX, int cellZ, int field) {
        SnapshotCell cell = cells.get(packCell(cellX, cellZ));
        if (cell == null) {
            return Double.NaN;
        }
        return field == TEMPERATURE_FIELD
                ? cell.sample().temperature()
                : cell.sample().precipitationIntensity();
    }

    private static double interpolateAvailableScalar(double from, double to, double amount) {
        if (Double.isNaN(from)) {
            return to;
        }
        if (Double.isNaN(to)) {
            return from;
        }
        return from + (to - from) * clamp01(amount);
    }

    private static double clearIfMissing(double value) {
        return Double.isNaN(value) ? 0.0 : value;
    }

    private static double scalar(WeatherSample sample, int field) {
        if (sample == null) {
            return Double.NaN;
        }
        return field == TEMPERATURE_FIELD
                ? sample.temperature()
                : sample.precipitationIntensity();
    }

    private static PrecipitationType type(WeatherSample sample) {
        return sample == null ? null : sample.precipitationType();
    }

    private static PrecipitationType interpolateAvailableType(
            PrecipitationType from,
            PrecipitationType to,
            double temperature,
            double intensity
    ) {
        if (intensity <= 1.0E-4D || (from == null && to == null)) {
            return PrecipitationType.NONE;
        }
        if (from == null) {
            return to;
        }
        if (to == null || from == to) {
            return from;
        }
        if (from == PrecipitationType.HAIL || to == PrecipitationType.HAIL) {
            return intensity >= 0.18D ? PrecipitationType.HAIL
                    : from == PrecipitationType.HAIL ? to : from;
        }
        if (from == PrecipitationType.NONE) {
            return to;
        }
        if (to == PrecipitationType.NONE) {
            return from;
        }
        return temperature <= WeatherSample.SNOW_MAX_TEMPERATURE
                ? PrecipitationType.SNOW
                : PrecipitationType.RAIN;
    }

    private static WeatherSample interpolateAvailable(
            WeatherSample from,
            WeatherSample to,
            double amount
    ) {
        if (from == null) {
            return to;
        }
        if (to == null) {
            return from;
        }
        return interpolateSamples(from, to, amount);
    }

    static WeatherSample interpolateSamples(WeatherSample from, WeatherSample to, double amount) {
        return WeatherSample.interpolate(from, to, clamp01(amount));
    }

    private static int floorToInt(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    /** Immutable rendering state copied from one network cell DTO. */
    record SnapshotCell(int cellX, int cellZ, long revision, WeatherSample sample) {
        SnapshotCell {
            if (revision < 0L) {
                throw new IllegalArgumentException("Cell revision cannot be negative");
            }
            Objects.requireNonNull(sample, "sample");
        }
    }
}
