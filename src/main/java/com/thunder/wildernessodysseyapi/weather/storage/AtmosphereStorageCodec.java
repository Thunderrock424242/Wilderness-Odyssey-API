package com.thunder.wildernessodysseyapi.weather.storage;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereView;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import com.thunder.wildernessodysseyapi.weather.simulation.AtmosphereGrid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Versioned compact NBT codec for persistent atmospheric cells.
 *
 * <p>Schema version one uses six parallel primitive long arrays. Two packed
 * weather words quantize normalized state, while keys, revisions, and activity
 * ticks retain their full range. Parallel arrays avoid a compound-tag object
 * per cell and make a strict configured cell bound inexpensive to enforce.</p>
 */
public final class AtmosphereStorageCodec {
    public static final int DATA_VERSION = 1;

    private static final String VERSION_KEY = "dataVersion";
    private static final String CELL_SIZE_KEY = "cellSize";
    private static final String CELL_KEYS_KEY = "cellKeys";
    private static final String WEATHER_A_KEY = "weatherA";
    private static final String WEATHER_B_KEY = "weatherB";
    private static final String REVISIONS_KEY = "revisions";
    private static final String LAST_SIMULATED_KEY = "lastSimulatedTicks";
    private static final String LAST_ACTIVE_KEY = "lastActiveTicks";

    private static final int UNIT_10_MAX = (1 << 10) - 1;
    private static final int UNIT_12_MAX = (1 << 12) - 1;
    private static final int UNIT_16_MAX = (1 << 16) - 1;
    private static final long UNIT_10_MASK = UNIT_10_MAX;
    private static final long UNIT_12_MASK = UNIT_12_MAX;
    private static final long UNIT_16_MASK = UNIT_16_MAX;
    private static final long WEATHER_B_RESERVED_MASK = ~((1L << 50) - 1L);
    private static final long MAX_WORLD_COORDINATE = 30_000_000L;

    private AtmosphereStorageCodec() {
    }

    /** Encodes at most {@code maximumCells} of the most meaningful grid cells. */
    public static CompoundTag encode(AtmosphereGrid grid, int maximumCells) {
        if (grid == null) {
            return emptyTag(256);
        }
        int limit = clamp(maximumCells, 1, 65_536);
        List<AtmosphereView> views = selectForPersistence(grid.views(), limit);
        int count = views.size();
        long[] keys = new long[count];
        long[] weatherA = new long[count];
        long[] weatherB = new long[count];
        long[] revisions = new long[count];
        long[] lastSimulated = new long[count];
        long[] lastActive = new long[count];

        for (int index = 0; index < count; index++) {
            AtmosphereView view = views.get(index);
            keys[index] = view.key().packed();
            weatherA[index] = encodeWeatherA(view.sample());
            weatherB[index] = encodeWeatherB(view.sample());
            revisions[index] = view.revision();
            lastSimulated[index] = view.lastSimulatedTick();
            lastActive[index] = view.lastActiveTick();
        }

        CompoundTag tag = emptyTag(grid.cellSize());
        tag.putLongArray(CELL_KEYS_KEY, keys);
        tag.putLongArray(WEATHER_A_KEY, weatherA);
        tag.putLongArray(WEATHER_B_KEY, weatherB);
        tag.putLongArray(REVISIONS_KEY, revisions);
        tag.putLongArray(LAST_SIMULATED_KEY, lastSimulated);
        tag.putLongArray(LAST_ACTIVE_KEY, lastActive);
        return tag;
    }

    /**
     * Decodes valid version-one entries and recovers to an empty grid for an
     * absent, unsupported, or structurally malformed payload.
     */
    public static DecodeResult decode(CompoundTag tag, int fallbackCellSize, int maximumCells) {
        int safeFallbackSize = clamp(fallbackCellSize, 16, 4_096);
        int limit = clamp(maximumCells, 1, 65_536);
        if (tag == null) {
            return new DecodeResult(new AtmosphereGrid(safeFallbackSize), 0, 0, 0, true);
        }

        try {
            int version = tag.contains(VERSION_KEY, Tag.TAG_INT) ? tag.getInt(VERSION_KEY) : 0;
            int storedSize = tag.contains(CELL_SIZE_KEY, Tag.TAG_INT) ? tag.getInt(CELL_SIZE_KEY) : safeFallbackSize;
            int cellSize = storedSize >= 16 && storedSize <= 4_096 ? storedSize : safeFallbackSize;
            AtmosphereGrid grid = new AtmosphereGrid(cellSize);
            if (version != DATA_VERSION) {
                return new DecodeResult(grid, version, 0, 0, true);
            }

            long[] keys = readLongArray(tag, CELL_KEYS_KEY);
            long[] weatherA = readLongArray(tag, WEATHER_A_KEY);
            long[] weatherB = readLongArray(tag, WEATHER_B_KEY);
            long[] revisions = readLongArray(tag, REVISIONS_KEY);
            long[] lastSimulated = readLongArray(tag, LAST_SIMULATED_KEY);
            long[] lastActive = readLongArray(tag, LAST_ACTIVE_KEY);
            int commonLength = minimumLength(keys, weatherA, weatherB, revisions, lastSimulated, lastActive);
            int attempted = Math.min(commonLength, limit);
            int structuralSkips = maximumLength(keys, weatherA, weatherB, revisions, lastSimulated, lastActive)
                    - commonLength;
            int skipped = Math.max(0, structuralSkips) + Math.max(0, commonLength - attempted);
            Set<Long> restoredKeys = new HashSet<>(attempted);

            for (int index = 0; index < attempted; index++) {
                long packedKey = keys[index];
                if (!validEntry(
                        packedKey,
                        weatherB[index],
                        revisions[index],
                        lastSimulated[index],
                        lastActive[index],
                        cellSize,
                        restoredKeys
                )) {
                    skipped++;
                    continue;
                }
                WeatherSample sample = decodeWeather(weatherA[index], weatherB[index]);
                if (sample == null) {
                    skipped++;
                    continue;
                }
                AtmosphereCellKey key = AtmosphereCellKey.fromPacked(packedKey);
                grid.restore(new AtmosphereView(
                        key,
                        sample,
                        revisions[index],
                        lastSimulated[index],
                        lastActive[index]
                ));
                restoredKeys.add(packedKey);
            }

            return new DecodeResult(grid, version, grid.size(), skipped, skipped > 0 || storedSize != cellSize);
        } catch (RuntimeException exception) {
            return new DecodeResult(new AtmosphereGrid(safeFallbackSize), 0, 0, 0, true);
        }
    }

    private static CompoundTag emptyTag(int cellSize) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_KEY, DATA_VERSION);
        tag.putInt(CELL_SIZE_KEY, clamp(cellSize, 16, 4_096));
        return tag;
    }

    private static List<AtmosphereView> selectForPersistence(List<AtmosphereView> source, int limit) {
        List<AtmosphereView> selected = new ArrayList<>(source);
        if (selected.size() > limit) {
            selected.sort(Comparator
                    .comparingDouble((AtmosphereView view) -> view.sample().stormEnergy()).reversed()
                    .thenComparing(Comparator.comparingLong(AtmosphereView::lastActiveTick).reversed())
                    .thenComparingLong(view -> view.key().packed()));
            selected.subList(limit, selected.size()).clear();
        }
        selected.sort(Comparator.comparingLong(view -> view.key().packed()));
        return selected;
    }

    private static long encodeWeatherA(WeatherSample sample) {
        long temperature = quantize(
                sample.temperature(),
                WeatherSample.MIN_TEMPERATURE,
                WeatherSample.MAX_TEMPERATURE,
                UNIT_16_MAX
        );
        long humidity = quantizeUnit(sample.humidity(), UNIT_12_MAX);
        long pressure = quantize(
                sample.pressure(),
                WeatherSample.MIN_PRESSURE,
                WeatherSample.MAX_PRESSURE,
                UNIT_16_MAX
        );
        long windX = quantize(sample.wind().x(), -1.0, 1.0, UNIT_10_MAX);
        long windZ = quantize(sample.wind().z(), -1.0, 1.0, UNIT_10_MAX);
        return temperature
                | (humidity << 16)
                | (pressure << 28)
                | (windX << 44)
                | (windZ << 54);
    }

    private static long encodeWeatherB(WeatherSample sample) {
        long cloudWater = quantizeUnit(sample.cloudWater(), UNIT_12_MAX);
        long instability = quantizeUnit(sample.instability(), UNIT_12_MAX);
        long stormEnergy = quantizeUnit(sample.stormEnergy(), UNIT_12_MAX);
        long precipitation = quantizeUnit(sample.precipitationIntensity(), UNIT_12_MAX);
        long precipitationType = sample.precipitationType().ordinal();
        return cloudWater
                | (instability << 12)
                | (stormEnergy << 24)
                | (precipitation << 36)
                | (precipitationType << 48);
    }

    private static WeatherSample decodeWeather(long weatherA, long weatherB) {
        int typeId = (int) ((weatherB >>> 48) & 0x3L);
        if (typeId < 0 || typeId >= PrecipitationType.values().length) {
            return null;
        }
        double temperature = dequantize(
                weatherA & UNIT_16_MASK,
                WeatherSample.MIN_TEMPERATURE,
                WeatherSample.MAX_TEMPERATURE,
                UNIT_16_MAX
        );
        double humidity = dequantizeUnit((weatherA >>> 16) & UNIT_12_MASK, UNIT_12_MAX);
        double pressure = dequantize(
                (weatherA >>> 28) & UNIT_16_MASK,
                WeatherSample.MIN_PRESSURE,
                WeatherSample.MAX_PRESSURE,
                UNIT_16_MAX
        );
        double windX = dequantize((weatherA >>> 44) & UNIT_10_MASK, -1.0, 1.0, UNIT_10_MAX);
        double windZ = dequantize((weatherA >>> 54) & UNIT_10_MASK, -1.0, 1.0, UNIT_10_MAX);
        double cloudWater = dequantizeUnit(weatherB & UNIT_12_MASK, UNIT_12_MAX);
        double instability = dequantizeUnit((weatherB >>> 12) & UNIT_12_MASK, UNIT_12_MAX);
        double stormEnergy = dequantizeUnit((weatherB >>> 24) & UNIT_12_MASK, UNIT_12_MAX);
        double precipitation = dequantizeUnit((weatherB >>> 36) & UNIT_12_MASK, UNIT_12_MAX);
        return new WeatherSample(
                temperature,
                humidity,
                pressure,
                new WindVector(windX, windZ),
                cloudWater,
                instability,
                stormEnergy,
                precipitation,
                PrecipitationType.values()[typeId]
        );
    }

    private static boolean validEntry(
            long packedKey,
            long weatherB,
            long revision,
            long lastSimulated,
            long lastActive,
            int cellSize,
            Set<Long> restoredKeys
    ) {
        if (revision < 0L || lastSimulated < 0L || lastActive < 0L || restoredKeys.contains(packedKey)) {
            return false;
        }
        if ((weatherB & WEATHER_B_RESERVED_MASK) != 0L) {
            return false;
        }
        AtmosphereCellKey key = AtmosphereCellKey.fromPacked(packedKey);
        long blockX = (long) key.x() * cellSize;
        long blockZ = (long) key.z() * cellSize;
        return Math.abs(blockX) <= MAX_WORLD_COORDINATE + cellSize
                && Math.abs(blockZ) <= MAX_WORLD_COORDINATE + cellSize;
    }

    private static long[] readLongArray(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_LONG_ARRAY) ? tag.getLongArray(key) : new long[0];
    }

    private static int minimumLength(long[]... arrays) {
        int minimum = Integer.MAX_VALUE;
        for (long[] array : arrays) {
            minimum = Math.min(minimum, array.length);
        }
        return minimum == Integer.MAX_VALUE ? 0 : minimum;
    }

    private static int maximumLength(long[]... arrays) {
        int maximum = 0;
        for (long[] array : arrays) {
            maximum = Math.max(maximum, array.length);
        }
        return maximum;
    }

    private static long quantizeUnit(double value, int maximum) {
        return quantize(value, 0.0, 1.0, maximum);
    }

    private static long quantize(double value, double minimum, double maximum, int quantizedMaximum) {
        double clamped = Math.max(minimum, Math.min(maximum, value));
        return Math.round((clamped - minimum) / (maximum - minimum) * quantizedMaximum);
    }

    private static double dequantizeUnit(long value, int maximum) {
        return (double) value / maximum;
    }

    private static double dequantize(long value, double minimum, double maximum, int quantizedMaximum) {
        return minimum + ((double) value / quantizedMaximum) * (maximum - minimum);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Result metadata lets debug tools report recovery without runtime log spam. */
    public record DecodeResult(
            AtmosphereGrid grid,
            int dataVersion,
            int restoredCells,
            int skippedCells,
            boolean recovered
    ) {
    }
}
