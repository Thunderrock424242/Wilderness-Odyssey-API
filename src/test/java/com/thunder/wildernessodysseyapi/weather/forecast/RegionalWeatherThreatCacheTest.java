package com.thunder.wildernessodysseyapi.weather.forecast;

import com.thunder.wildernessodysseyapi.weather.api.WeatherThreat;
import com.thunder.wildernessodysseyapi.weather.api.WeatherThreatForecast;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemStage;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemType;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionalWeatherThreatCacheTest {

    @Test
    void sharesOneCalculationWithinRegionAndExpiresAfterFiveSeconds() {
        RegionalWeatherThreatCache cache = new RegionalWeatherThreatCache();
        AtomicInteger calculations = new AtomicInteger();

        cache.query(new BlockPos(2, 64, 2), 128, 7_200, 0L,
                ignored -> forecast(calculations.incrementAndGet()));
        cache.query(new BlockPos(120, 70, 120), 128, 7_200, 80L,
                ignored -> forecast(calculations.incrementAndGet()));
        assertEquals(1, calculations.get());
        assertEquals(1, cache.size());

        cache.query(new BlockPos(2, 64, 2), 128, 7_200, 100L,
                ignored -> forecast(calculations.incrementAndGet()));
        assertEquals(2, calculations.get());
    }

    @Test
    void keepsDifferentLookAheadWindowsIndependent() {
        RegionalWeatherThreatCache cache = new RegionalWeatherThreatCache();
        AtomicInteger calculations = new AtomicInteger();

        cache.query(BlockPos.ZERO, 128, 2_400, 0L,
                ignored -> forecast(calculations.incrementAndGet()));
        cache.query(BlockPos.ZERO, 128, 7_200, 0L,
                ignored -> forecast(calculations.incrementAndGet()));

        assertEquals(2, calculations.get());
        assertEquals(2, cache.size());
    }

    private static WeatherThreatForecast forecast(long id) {
        return new WeatherThreatForecast(
                WeatherThreat.THUNDERSTORM,
                0.65,
                400.0,
                2_400L,
                0.8,
                id,
                WeatherSystemType.STORM,
                WeatherSystemStage.MATURE
        );
    }
}
