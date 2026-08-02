package com.thunder.wildernessodysseyapi.weather.system;

import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherSystemStorageCodecTest {

    @Test
    void roundTripPreservesPersistentIdentity() {
        WeatherSystemTracker tracker = new WeatherSystemTracker();
        tracker.restore(8L, List.of(new TrackedWeatherSystem(
                7L, WeatherSystemType.COLD_FRONT, WeatherSystemStage.MATURE,
                -420.0, 880.0, 640.0, 0.73, new WindVector(0.4, -0.2),
                0.66, 1_200L, 8_000L, 2_000L
        )));
        CompoundTag encoded = WeatherSystemStorageCodec.encode(tracker, 48);
        WeatherSystemStorageCodec.DecodeResult decoded = WeatherSystemStorageCodec.decode(encoded, 48);
        assertFalse(decoded.recovered());
        assertEquals(8L, decoded.nextId());
        assertEquals(tracker.systems(), decoded.systems());
    }

    @Test
    void unknownVersionRecoversToEmptyState() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("dataVersion", 99);
        WeatherSystemStorageCodec.DecodeResult decoded = WeatherSystemStorageCodec.decode(tag, 48);
        assertTrue(decoded.recovered());
        assertTrue(decoded.systems().isEmpty());
    }
}
