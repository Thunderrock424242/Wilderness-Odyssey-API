package com.thunder.wildernessodysseyapi.weather.forecast;

import com.thunder.wildernessodysseyapi.weather.api.WeatherThreatForecast;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Short-lived regional cache for approaching-weather forecasts.
 *
 * <p>All callers inside one atmospheric cell and look-ahead window share a
 * single scan of the bounded weather-system tracker. Cache entries never load
 * chunks and are cleared whenever tracker state changes.</p>
 */
public final class RegionalWeatherThreatCache {

    private static final long CACHE_TICKS = 100L;
    private static final int MAXIMUM_ENTRIES = 2_048;
    private final Map<Key, Entry> entries = new HashMap<>();

    /** Returns a cached forecast calculated at the center of the containing region. */
    public WeatherThreatForecast query(
            BlockPos position,
            int regionSize,
            int lookAheadTicks,
            long gameTime,
            Function<BlockPos, WeatherThreatForecast> calculator
    ) {
        int size = Math.max(16, regionSize);
        int regionX = Math.floorDiv(position.getX(), size);
        int regionZ = Math.floorDiv(position.getZ(), size);
        int horizon = Math.max(0, Math.min(24_000, lookAheadTicks));
        Key key = new Key(regionX, regionZ, horizon);
        Entry cached = entries.get(key);
        if (cached != null && cached.expiresAt() > gameTime) {
            return cached.forecast();
        }

        BlockPos center = new BlockPos(
                regionX * size + size / 2,
                position.getY(),
                regionZ * size + size / 2
        );
        WeatherThreatForecast forecast = calculator.apply(center);
        Entry next = new Entry(gameTime + CACHE_TICKS,
                forecast == null ? WeatherThreatForecast.NONE : forecast);
        entries.put(key, next);
        trim();
        return next.forecast();
    }

    /** Invalidates every regional forecast after authoritative tracker changes. */
    public void clear() {
        entries.clear();
    }

    /** Returns the current bounded entry count for diagnostics and tests. */
    public int size() {
        return entries.size();
    }

    private void trim() {
        while (entries.size() > MAXIMUM_ENTRIES) {
            entries.remove(entries.keySet().iterator().next());
        }
    }

    private record Key(int regionX, int regionZ, int lookAheadTicks) {
    }

    private record Entry(long expiresAt, WeatherThreatForecast forecast) {
    }
}
