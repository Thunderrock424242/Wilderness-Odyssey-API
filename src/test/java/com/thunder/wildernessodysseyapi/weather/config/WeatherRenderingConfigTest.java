package com.thunder.wildernessodysseyapi.weather.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies localized cloud quality settings remain safe for the render thread. */
class WeatherRenderingConfigTest {

    @Test
    void authoredDefaultsEnableBalancedLocalizedCloudRendering() {
        WeatherRenderingConfig.Settings defaults = WeatherRenderingConfig.settings();

        assertTrue(defaults.enabled());
        assertTrue(defaults.volumetricClouds());
        assertTrue(defaults.raymarchedClouds());
        assertEquals(24, defaults.raymarchSteps());
        assertEquals(384, defaults.renderDistanceBlocks());
        assertEquals(5, defaults.rebuildIntervalTicks());
        assertEquals(6.0, defaults.windDetailSpeedBlocksPerSecond(), 1.0E-12);
        assertEquals(4_096, defaults.maximumCloudTiles());
        assertEquals(1.0, defaults.opacityMultiplier(), 1.0E-12);
        assertEquals(8, defaults.volumetricLayerCount());
        assertEquals(0.65, defaults.volumetricDetailStrength(), 1.0E-12);
        assertTrue(defaults.distantRainShafts());
        assertTrue(defaults.windDrivenPrecipitation());
        assertEquals(10.0, defaults.precipitationWindSlantBlocks(), 1.0E-12);
        assertEquals(96, defaults.distantRainDistanceBlocks());
        assertEquals(6, defaults.distantRainSpacingBlocks());
        assertEquals(768, defaults.maximumDistantRainShafts());
        assertEquals(0.82, defaults.precipitationStreakDensity(), 1.0E-12);
        assertEquals(0.78, defaults.precipitationOpacity(), 1.0E-12);
        assertEquals(0.32, defaults.precipitationImpactDensity(), 1.0E-12);
        assertEquals(256, defaults.maximumPrecipitationImpacts());
        assertTrue(defaults.distantCloudLayer());
        assertEquals(1_024, defaults.distantCloudDistanceBlocks());
        assertEquals(48, defaults.distantCloudSpacingBlocks());
        assertEquals(512, defaults.maximumDistantCloudTiles());
        assertEquals(0.55, defaults.cloudShadowStrength(), 1.0E-12);
        assertTrue(defaults.surfaceOverlays());
        assertEquals(24, defaults.surfaceOverlayRadiusBlocks());
        assertEquals(256, defaults.maximumSurfacePatches());
    }

    @Test
    void settingsClampUnsafeLowerAndNonFiniteValues() {
        WeatherRenderingConfig.Settings settings = new WeatherRenderingConfig.Settings(
                true,
                Integer.MIN_VALUE,
                Integer.MIN_VALUE,
                Double.NaN,
                Integer.MIN_VALUE,
                Double.NEGATIVE_INFINITY,
                true,
                Integer.MIN_VALUE,
                Integer.MIN_VALUE,
                Integer.MIN_VALUE
        );

        assertTrue(settings.enabled());
        assertEquals(96, settings.renderDistanceBlocks());
        assertEquals(2, settings.rebuildIntervalTicks());
        assertEquals(0.0, settings.windDetailSpeedBlocksPerSecond(), 1.0E-12);
        assertEquals(256, settings.maximumCloudTiles());
        assertEquals(0.25, settings.opacityMultiplier(), 1.0E-12);
        assertEquals(32, settings.distantRainDistanceBlocks());
        assertEquals(4, settings.distantRainSpacingBlocks());
        assertEquals(64, settings.maximumDistantRainShafts());
    }

    @Test
    void settingsClampUnsafeUpperValuesWithoutChangingEnabledFlag() {
        WeatherRenderingConfig.Settings settings = new WeatherRenderingConfig.Settings(
                false,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                1_000.0,
                Integer.MAX_VALUE,
                100.0,
                false,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE
        );

        assertFalse(settings.enabled());
        assertEquals(512, settings.renderDistanceBlocks());
        assertEquals(40, settings.rebuildIntervalTicks());
        assertEquals(24.0, settings.windDetailSpeedBlocksPerSecond(), 1.0E-12);
        assertEquals(8_192, settings.maximumCloudTiles());
        assertEquals(1.25, settings.opacityMultiplier(), 1.0E-12);
        assertFalse(settings.distantRainShafts());
        assertEquals(192, settings.distantRainDistanceBlocks());
        assertEquals(16, settings.distantRainSpacingBlocks());
        assertEquals(2_048, settings.maximumDistantRainShafts());
    }

    @Test
    void advancedCloudAndImpactControlsAreDefensivelyBounded() {
        WeatherRenderingConfig.Settings settings = new WeatherRenderingConfig.Settings(
                true, true, true, Integer.MAX_VALUE,
                384, 5, 6.0, 4_096, 1.0, 8, 0.65,
                true, true, 10.0, 96, 6, 768,
                Double.NaN, 99.0, -4.0, Integer.MAX_VALUE,
                true, 1_024, 48, 512, 0.55,
                true, 24, 256
        );

        assertTrue(settings.raymarchedClouds());
        assertEquals(64, settings.raymarchSteps());
        assertEquals(0.10, settings.precipitationStreakDensity(), 1.0E-12);
        assertEquals(1.25, settings.precipitationOpacity(), 1.0E-12);
        assertEquals(0.0, settings.precipitationImpactDensity(), 1.0E-12);
        assertEquals(1_024, settings.maximumPrecipitationImpacts());
    }
}
