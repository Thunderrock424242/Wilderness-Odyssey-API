package com.thunder.wildernessodysseyapi.weather.system;

import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherSystemTrackerTest {

    @Test
    void identityMovesStrengthensAndDissipatesAcrossUpdates() {
        WeatherSystemTracker tracker = new WeatherSystemTracker();
        WeatherSystemTracker.TrackingSettings settings = WeatherSystemTracker.TrackingSettings.DEFAULT;
        var observation = new WeatherSystemTracker.Observation(
                WeatherSystemType.STORM, 0.0, 0.0, 320.0, 0.72, new WindVector(1.0, 0.0), 0.6
        );
        tracker.update(List.of(observation), 60L, 60, settings);
        long id = tracker.systems().getFirst().id();
        tracker.update(List.of(new WeatherSystemTracker.Observation(
                WeatherSystemType.STORM, 12.0, 0.0, 340.0, 0.86, new WindVector(1.0, 0.0), 0.7
        )), 120L, 60, settings);
        TrackedWeatherSystem moved = tracker.systems().getFirst();
        assertEquals(id, moved.id());
        assertTrue(moved.centerX() > 0.0);
        assertTrue(moved.intensity() > 0.72);

        for (int update = 0; update < 30 && !tracker.systems().isEmpty(); update++) {
            tracker.update(List.of(), 180L + update * 60L, 60, settings);
        }
        assertTrue(tracker.systems().isEmpty());
    }

    @Test
    void overlappingCompatibleStormsMergeIntoOneIdentity() {
        WeatherSystemTracker tracker = new WeatherSystemTracker();
        WeatherSystemTracker.TrackingSettings defaults = WeatherSystemTracker.TrackingSettings.DEFAULT;
        WeatherSystemTracker.TrackingSettings settings = new WeatherSystemTracker.TrackingSettings(
                true, 48, 60, 3.0, 40.0, 520.0, 0.2, 0.08,
                0.035, 0.70, false, defaults.splitIntensity(), defaults.splitOrganization(), 6_000
        );
        tracker.update(List.of(
                new WeatherSystemTracker.Observation(WeatherSystemType.STORM,
                        0.0, 0.0, 420.0, 0.8, WindVector.ZERO, 0.5),
                new WeatherSystemTracker.Observation(WeatherSystemType.STORM,
                        180.0, 0.0, 420.0, 0.7, WindVector.ZERO, 0.5)
        ), 60L, 60, settings);
        assertEquals(1, tracker.systems().size());
        assertTrue(tracker.systems().getFirst().intensity() > 0.8);
    }

    @Test
    void severePromotionRemainsFormingUntilTheNextObservation() {
        WeatherSystemTracker tracker = new WeatherSystemTracker();
        WeatherSystemTracker.TrackingSettings settings = WeatherSystemTracker.TrackingSettings.DEFAULT;
        var ordinary = new WeatherSystemTracker.Observation(
                WeatherSystemType.STORM,
                0.0,
                0.0,
                320.0,
                0.72,
                WindVector.ZERO,
                0.72
        );
        var tornado = new WeatherSystemTracker.Observation(
                WeatherSystemType.TORNADO,
                0.0,
                0.0,
                320.0,
                0.82,
                WindVector.ZERO,
                0.90
        );

        tracker.update(List.of(ordinary), 60L, 60, settings);
        tracker.update(List.of(tornado), 120L, 60, settings);
        assertEquals(WeatherSystemType.TORNADO, tracker.systems().getFirst().type());
        assertEquals(WeatherSystemStage.FORMING, tracker.systems().getFirst().stage());

        tracker.update(List.of(tornado), 180L, 60, settings);
        assertEquals(WeatherSystemStage.MATURE, tracker.systems().getFirst().stage());
    }
}
