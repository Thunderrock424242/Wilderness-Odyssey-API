package com.thunder.wildernessodysseyapi.cinematic.sequence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryoWakeupHudLayoutTest {
    @Test
    void compactScreensKeepBootAndSubtitleInsideSafeMargins() {
        assertInside(CryoWakeupHudLayout.boot(320, 180), 320, 180);
        assertInside(CryoWakeupHudLayout.subtitle(320, 180, 260, 2), 320, 180);
    }

    @Test
    void telemetryUsesOneBoundedInstrumentPanel() {
        var panel = CryoWakeupHudLayout.telemetry(640, 360).orElseThrow();
        assertInside(panel, 640, 360);
        assertTrue(panel.width() >= 198);
        assertTrue(panel.width() <= 226);
    }

    @Test
    void telemetryYieldsToContentOnVerySmallScreens() {
        assertFalse(CryoWakeupHudLayout.telemetry(240, 135).isPresent());
        assertFalse(CryoWakeupHudLayout.telemetry(320, 180).isPresent());
    }

    @Test
    void threeLineCaptionClearsTelemetryAtMinimumSupportedHeight() {
        var telemetry = CryoWakeupHudLayout.telemetry(320, 210).orElseThrow();
        var subtitle = CryoWakeupHudLayout.subtitle(320, 210, 260, 3);

        assertTrue(telemetry.bottom() < subtitle.y());
    }

    private static void assertInside(CryoWakeupHudLayout.Bounds bounds, int width, int height) {
        assertTrue(bounds.x() >= 0);
        assertTrue(bounds.y() >= 0);
        assertTrue(bounds.right() <= width);
        assertTrue(bounds.bottom() <= height);
    }
}
