package com.thunder.wildernessodysseyapi.telemetry;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerTelemetryReporterTest {

    @AfterEach
    void clearCaches() {
        PlayerTelemetryReporter.clearCaches();
    }

    @Test
    void immutableSnapshotBuildsPayloadWithoutLivePlayerState() {
        UUID uuid = UUID.fromString("4f3a51d5-6f28-4f14-b748-bae4ddb2f974");
        Instant eventTime = Instant.parse("2026-08-20T12:00:00Z");
        PlayerTelemetryReporter.PlayerSnapshot snapshot = new PlayerTelemetryReporter.PlayerSnapshot(
                uuid,
                "Explorer",
                "203.0.113.8",
                1234L,
                eventTime
        );

        JsonObject payload = PlayerTelemetryReporter.buildPayload(
                snapshot,
                new PlayerTelemetryReporter.GeoInfo("Virginia", "United States"),
                new PlayerTelemetryReporter.AccountAgeInfo(900L, eventTime.minusSeconds(900L * 86400L), "test"),
                null,
                "logout",
                config()
        );

        assertEquals(uuid.toString(), payload.get("uuid").getAsString());
        assertEquals("Explorer", payload.get("player_name").getAsString());
        assertEquals(eventTime.toString(), payload.get("event_timestamp").getAsString());
        assertEquals(1234L, payload.get("total_play_time_seconds").getAsLong());
    }

    @Test
    void sparkWaitIsWorkerSideAndHardCapped() {
        assertEquals(1, PlayerTelemetryReporter.cappedSparkTimeout(0));
        assertEquals(3, PlayerTelemetryReporter.cappedSparkTimeout(3));
        assertEquals(5, PlayerTelemetryReporter.cappedSparkTimeout(120));
        assertTrue(config().blockLogoutUntilSparkSent(), "legacy setting remains readable but is ignored by dispatch");
    }

    @Test
    void periodicMaintenanceEvictsPlayersWithoutAnotherLookup() throws Exception {
        UUID uuid = UUID.fromString("e7b38fb7-a449-40ad-91e0-cf4654075533");
        Instant beforeLookup = Instant.now();
        PlayerTelemetryReporter.resolveGeoInfo(uuid, null, config());
        PlayerTelemetryReporter.resolveAccountAge(uuid, config());

        assertEquals(1, cacheSize("GEO_CACHE"));
        assertEquals(1, cacheSize("ACCOUNT_AGE_CACHE"));

        PlayerTelemetryReporter.evictExpiredCaches(config(), beforeLookup.plusSeconds(120));

        assertEquals(0, cacheSize("GEO_CACHE"));
        assertEquals(0, cacheSize("ACCOUNT_AGE_CACHE"));
    }

    private static int cacheSize(String fieldName) throws Exception {
        Field field = PlayerTelemetryReporter.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).size();
    }

    private static PlayerTelemetryConfig.TelemetryConfigValues config() {
        return new PlayerTelemetryConfig.TelemetryConfigValues(
                true,
                "",
                "",
                "https://example.invalid/sheet",
                1,
                true,
                true,
                "https://example.invalid/spark",
                true,
                120,
                100.0,
                1,
                false,
                "",
                60,
                60,
                0,
                50,
                100
        );
    }
}
