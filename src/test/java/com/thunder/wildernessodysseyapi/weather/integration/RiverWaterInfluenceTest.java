package com.thunder.wildernessodysseyapi.weather.integration;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk.BodyType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RiverWaterInfluenceTest {
    @Test
    void generatedRiverCrossingPlainsRemainsARiverForWeather() {
        assertEquals(BodyType.RIVER, WildernessWeatherWaterInfluence.surfaceKind(BodyType.RIVER, false, false));
        assertEquals(BodyType.RIVER, WildernessWeatherWaterInfluence.surfaceKind(BodyType.SPRING, false, false));
        assertEquals(BodyType.LAKE, WildernessWeatherWaterInfluence.surfaceKind(BodyType.LAKE, false, true));
        assertEquals(BodyType.OCEAN, WildernessWeatherWaterInfluence.surfaceKind(BodyType.OCEAN, false, false));
    }

    @Test
    void unclassifiedNativeWaterRetainsBiomeFallback() {
        assertEquals(BodyType.OCEAN, WildernessWeatherWaterInfluence.surfaceKind(null, true, false));
        assertEquals(BodyType.RIVER, WildernessWeatherWaterInfluence.surfaceKind(null, false, true));
        assertEquals(BodyType.LAKE, WildernessWeatherWaterInfluence.surfaceKind(null, false, false));
    }
}
