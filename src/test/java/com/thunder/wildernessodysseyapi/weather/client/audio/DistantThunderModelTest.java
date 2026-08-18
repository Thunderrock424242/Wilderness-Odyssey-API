package com.thunder.wildernessodysseyapi.weather.client.audio;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import com.thunder.wildernessodysseyapi.weather.config.WeatherRenderingConfig;
import com.thunder.wildernessodysseyapi.weather.networking.DistantThunderSystemSyncPayload;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemStage;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers qualification, motion, distance, cadence, and single-storm selection. */
class DistantThunderModelTest {

    private static final WeatherRenderingConfig.Settings SETTINGS = settings(0.50, 6_144, 8, 75, 1.0);

    @Test
    void clearWeatherWithoutNearbyStormsProducesNoThunder() {
        assertNull(DistantThunderModel.select(
                List.of(), 0.0, 0.0, WeatherSample.CLEAR, SETTINGS, -1L
        ));
    }

    @Test
    void lightRainNeverQualifiesEvenInsideTrackedStorm() {
        var lightRain = storm(
                1L, 3_000.0, -1.0, 0.85,
                PrecipitationType.RAIN, 0.18, 0.80, 0.75, 0.60
        );

        assertFalse(DistantThunderModel.canProduceThunderstormAudio(lightRain, SETTINGS));
        assertNull(DistantThunderModel.select(
                List.of(lightRain), 0.0, 0.0, WeatherSample.CLEAR, SETTINGS, -1L
        ));
    }

    @Test
    void gentleRainWithoutConvectiveEnergyRemainsPeaceful() {
        var gentleRain = storm(
                2L, 2_500.0, -1.0, 0.70,
                PrecipitationType.RAIN, 0.50, 0.32, 0.28, 0.16
        );

        assertFalse(DistantThunderModel.canProduceThunderstormAudio(gentleRain, SETTINGS));
    }

    @Test
    void approachingThunderstormQualifiesBeforeLocalRainArrives() {
        var approaching = storm(
                3L, 4_000.0, -1.0, 0.76,
                PrecipitationType.RAIN, 0.72, 0.78, 0.70, 0.61
        );

        DistantThunderModel.Selection selection = DistantThunderModel.select(
                List.of(approaching), 0.0, 0.0, WeatherSample.CLEAR, SETTINGS, -1L
        );

        assertEquals(3L, selection.storm().id());
        assertEquals(DistantThunderModel.Movement.APPROACHING, selection.movement());
        assertEquals(DistantThunderModel.ThunderstormClassification.SEVERE_THUNDERSTORM,
                selection.classification());
        assertTrue(selection.volume() > 0.0);
    }

    @Test
    void closerStormIsLouderAndReceivesShorterCadence() {
        var far = storm(
                4L, 5_500.0, -1.0, 0.76,
                PrecipitationType.RAIN, 0.72, 0.78, 0.70, 0.61
        );
        var near = storm(
                5L, 1_500.0, -1.0, 0.76,
                PrecipitationType.RAIN, 0.72, 0.78, 0.70, 0.61
        );
        DistantThunderModel.Selection farSelection = DistantThunderModel.select(
                List.of(far), 0.0, 0.0, WeatherSample.CLEAR, SETTINGS, -1L
        );
        DistantThunderModel.Selection nearSelection = DistantThunderModel.select(
                List.of(near), 0.0, 0.0, WeatherSample.CLEAR, SETTINGS, -1L
        );

        assertTrue(nearSelection.volume() > farSelection.volume());
        assertTrue(
                DistantThunderModel.nextIntervalTicks(nearSelection, SETTINGS, 0.5)
                        < DistantThunderModel.nextIntervalTicks(farSelection, SETTINGS, 0.5)
        );
    }

    @Test
    void movingAwayStormFadesAndIsRejectedAtLongRange() {
        var approaching = storm(
                6L, 2_000.0, -1.0, 0.78,
                PrecipitationType.RAIN, 0.75, 0.82, 0.74, 0.66
        );
        var movingAway = storm(
                7L, 2_000.0, 1.0, 0.78,
                PrecipitationType.RAIN, 0.75, 0.82, 0.74, 0.66
        );
        var distantMovingAway = storm(
                8L, 5_000.0, 1.0, 0.78,
                PrecipitationType.RAIN, 0.75, 0.82, 0.74, 0.66
        );
        DistantThunderModel.Selection approachingSelection = DistantThunderModel.select(
                List.of(approaching), 0.0, 0.0, WeatherSample.CLEAR, SETTINGS, -1L
        );
        DistantThunderModel.Selection movingAwaySelection = DistantThunderModel.select(
                List.of(movingAway), 0.0, 0.0, WeatherSample.CLEAR, SETTINGS, -1L
        );

        assertEquals(DistantThunderModel.Movement.MOVING_AWAY, movingAwaySelection.movement());
        assertTrue(movingAwaySelection.volume() < approachingSelection.volume());
        assertNull(DistantThunderModel.select(
                List.of(distantMovingAway), 0.0, 0.0, WeatherSample.CLEAR, SETTINGS, -1L
        ));
    }

    @Test
    void weakeningLifecycleFadesVolumeAndFrequency() {
        var mature = storm(
                12L, 2_000.0, -1.0, 0.78,
                PrecipitationType.RAIN, 0.75, 0.82, 0.74, 0.66
        );
        var weakening = new DistantThunderSystemSyncPayload.StormSnapshot(
                13L, mature.type(), WeatherSystemStage.WEAKENING,
                mature.centerX(), mature.centerZ(), mature.radiusBlocks(), mature.intensity(),
                mature.motionX(), mature.motionZ(), mature.organization(),
                mature.precipitationType(), mature.precipitationIntensity(),
                mature.stormEnergy(), mature.instability(), mature.thunderPotential()
        );
        DistantThunderModel.Selection matureSelection = DistantThunderModel.select(
                List.of(mature), 0.0, 0.0, WeatherSample.CLEAR, SETTINGS, -1L
        );
        DistantThunderModel.Selection weakeningSelection = DistantThunderModel.select(
                List.of(weakening), 0.0, 0.0, WeatherSample.CLEAR, SETTINGS, -1L
        );

        assertTrue(weakeningSelection.volume() < matureSelection.volume());
        assertTrue(
                DistantThunderModel.nextIntervalTicks(weakeningSelection, SETTINGS, 0.5)
                        > DistantThunderModel.nextIntervalTicks(matureSelection, SETTINGS, 0.5)
        );
    }

    @Test
    void multipleStormsProduceOneStableSensibleSelection() {
        var weakerCrossing = new DistantThunderSystemSyncPayload.StormSnapshot(
                9L, WeatherSystemType.STORM, WeatherSystemStage.MATURE,
                1_700.0, 0.0, 300.0, 0.58, 0.0, 1.0, 0.42,
                PrecipitationType.RAIN, 0.65, 0.67, 0.58, 0.45
        );
        var strongApproaching = new DistantThunderSystemSyncPayload.StormSnapshot(
                10L, WeatherSystemType.CYCLONE, WeatherSystemStage.MATURE,
                2_200.0, 0.0, 300.0, 0.88, -1.0, 0.0, 0.80,
                PrecipitationType.HAIL, 0.84, 0.91, 0.86, 0.78
        );

        DistantThunderModel.Selection selection = DistantThunderModel.select(
                List.of(weakerCrossing, strongApproaching),
                0.0, 0.0, WeatherSample.CLEAR, SETTINGS, -1L
        );

        assertEquals(10L, selection.storm().id());
        assertEquals(DistantThunderModel.ThunderstormClassification.EXTREME_STORM,
                selection.classification());
    }

    @Test
    void localLightningEligibleStormHandsBackToNormalThunder() {
        WeatherSample localThunderstorm = new WeatherSample(
                20.0, 0.95, 0.94, WindVector.ZERO,
                0.9, 0.82, 0.86, 0.80, PrecipitationType.RAIN
        );
        var distant = storm(
                11L, 1_500.0, -1.0, 0.88,
                PrecipitationType.RAIN, 0.84, 0.91, 0.86, 0.78
        );

        assertTrue(localThunderstorm.lightningEligible());
        assertNull(DistantThunderModel.select(
                List.of(distant), 0.0, 0.0, localThunderstorm, SETTINGS, -1L
        ));
    }

    private static DistantThunderSystemSyncPayload.StormSnapshot storm(
            long id,
            double centerX,
            double motionX,
            double intensity,
            PrecipitationType precipitationType,
            double precipitationIntensity,
            double stormEnergy,
            double instability,
            double thunderPotential
    ) {
        return new DistantThunderSystemSyncPayload.StormSnapshot(
                id,
                WeatherSystemType.STORM,
                WeatherSystemStage.MATURE,
                centerX,
                0.0,
                300.0,
                intensity,
                motionX,
                0.0,
                0.62,
                precipitationType,
                precipitationIntensity,
                stormEnergy,
                instability,
                thunderPotential
        );
    }

    private static WeatherRenderingConfig.Settings settings(
            double minimumStormIntensity,
            int maximumAudibleDistance,
            int minimumInterval,
            int maximumInterval,
            double volumeMultiplier
    ) {
        return new WeatherRenderingConfig.Settings(
                true, true, true, 24, 384, 5, 6.0, 4_096, 1.0,
                8, 0.65, true, true, 10.0, 96, 6, 768,
                0.82, 0.78, 0.32, 256, true, 1_024, 48, 512,
                0.55, true, 24, 256,
                true, minimumStormIntensity, maximumAudibleDistance,
                minimumInterval, maximumInterval, volumeMultiplier
        );
    }
}
