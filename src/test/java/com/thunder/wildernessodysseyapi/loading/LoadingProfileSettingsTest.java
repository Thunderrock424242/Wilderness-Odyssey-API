package com.thunder.wildernessodysseyapi.loading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LoadingProfileSettingsTest {
    @Test
    void boundsMalformedAndExcessiveReportDelays() {
        for (String value : new String[]{"", "oops", "-5", "0", "1:bad", "-1:30", "1:2:3:4", "9223372036854775807:59"}) {
            assertEquals(60_000, LoadingProfileSettings.reportDelayMillis(value));
        }
        assertEquals(3_600_000, LoadingProfileSettings.reportDelayMillis("999999999999"));
    }

    @Test
    void retainsLegacyMinuteTickAndColonOverrides() {
        assertEquals(300_000, LoadingProfileSettings.reportDelayMillis("5"));
        assertEquals(120_000, LoadingProfileSettings.reportDelayMillis("2400t"));
        assertEquals(120_000, LoadingProfileSettings.reportDelayMillis("1:30"));
        assertEquals(3_600_000, LoadingProfileSettings.reportDelayMillis("1:00:00"));
    }

    @Test
    void tracksWorldPreparationMessagesWithoutTreatingSaveOrMenuMessagesAsLoads() {
        assertEquals(LoadingScreenPhase.WORLD_DATA, LoadingScreenPhase.fromMessageKey("selectWorld.data_read"));
        assertEquals(LoadingScreenPhase.WORLD_RESOURCES, LoadingScreenPhase.fromMessageKey("selectWorld.resource_load"));
        assertEquals(LoadingScreenPhase.WORLD_RESOURCES, LoadingScreenPhase.fromMessageKey("dataPack.validation.working"));
        assertEquals(LoadingScreenPhase.WORLD_PREPARATION, LoadingScreenPhase.fromMessageKey("selectWorld.preparing"));
        assertNull(LoadingScreenPhase.fromMessageKey("menu.savingLevel"));
        assertNull(LoadingScreenPhase.fromMessageKey("menu.singleplayer"));
    }
}
