package com.thunder.wildernessodysseyapi.telemetry;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PlayerTelemetrySessionsTest {
    @Test
    void matchesOneStartAndEndWithMonotonicElapsedSeconds() {
        var sessions = new PlayerTelemetrySessions();
        UUID player = UUID.randomUUID();
        Instant start = Instant.parse("2026-09-14T12:00:00Z");
        var session = sessions.begin(player, start, 1_000_000_000L);
        assertNotNull(session);
        assertNull(sessions.begin(player, start.plusSeconds(1), 2_000_000_000L));
        var end = sessions.end(player);
        assertSame(session, end);
        assertEquals(125, end.durationSeconds(126_500_000_000L));
        assertEquals(start, end.startedAt());
        assertNull(sessions.end(player));
        assertNotEquals(session.id(), sessions.begin(player, start.plusSeconds(130), 131_000_000_000L).id());
        sessions.clear();
        assertNull(sessions.end(player));
    }

    @Test
    void loginAndLogoutHandlersNeverGateOnDedicatedOrClientDistribution() throws Exception {
        // A live GameTest covers registration. This guard specifically reproduces the old
        // dedicated-server early-return regression, which GameTestServer alone cannot model.
        Path project = Path.of(System.getProperty("wildernessodysseyapi.projectDir", "."));
        String source = Files.readString(project.resolve(
                "src/main/java/com/thunder/wildernessodysseyapi/telemetry/PlayerTelemetryReporter.java"));
        String handlers = source.substring(source.indexOf("public static void onPlayerLogin"),
                source.indexOf("private static String resolveIpAddress"));
        assertFalse(handlers.contains("isDedicatedServer("));
        assertFalse(handlers.contains("FMLEnvironment"));
        assertFalse(handlers.contains("Dist.CLIENT"));
        assertTrue(handlers.contains("instanceof ServerPlayer"));
        assertTrue(handlers.contains("SESSIONS.begin("));
        assertTrue(handlers.contains("SESSIONS.end("));
    }
}