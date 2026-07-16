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
        assertEquals(384, defaults.renderDistanceBlocks());
        assertEquals(5, defaults.rebuildIntervalTicks());
        assertEquals(6.0, defaults.windDetailSpeedBlocksPerSecond(), 1.0E-12);
        assertEquals(4_096, defaults.maximumCloudTiles());
        assertEquals(1.0, defaults.opacityMultiplier(), 1.0E-12);
    }

    @Test
    void settingsClampUnsafeLowerAndNonFiniteValues() {
        WeatherRenderingConfig.Settings settings = new WeatherRenderingConfig.Settings(
                true,
                Integer.MIN_VALUE,
                Integer.MIN_VALUE,
                Double.NaN,
                Integer.MIN_VALUE,
                Double.NEGATIVE_INFINITY
        );

        assertTrue(settings.enabled());
        assertEquals(96, settings.renderDistanceBlocks());
        assertEquals(2, settings.rebuildIntervalTicks());
        assertEquals(0.0, settings.windDetailSpeedBlocksPerSecond(), 1.0E-12);
        assertEquals(256, settings.maximumCloudTiles());
        assertEquals(0.25, settings.opacityMultiplier(), 1.0E-12);
    }

    @Test
    void settingsClampUnsafeUpperValuesWithoutChangingEnabledFlag() {
        WeatherRenderingConfig.Settings settings = new WeatherRenderingConfig.Settings(
                false,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                1_000.0,
                Integer.MAX_VALUE,
                100.0
        );

        assertFalse(settings.enabled());
        assertEquals(512, settings.renderDistanceBlocks());
        assertEquals(40, settings.rebuildIntervalTicks());
        assertEquals(24.0, settings.windDetailSpeedBlocksPerSecond(), 1.0E-12);
        assertEquals(8_192, settings.maximumCloudTiles());
        assertEquals(1.25, settings.opacityMultiplier(), 1.0E-12);
    }
}
