package com.thunder.wildernessodysseyapi.diagnostics.performance;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceSessionTest {
    @Test
    void newSessionDoesNotAcceptOldServerIdentityOrRetainHistory() {
        Object oldServer = new Object();
        var old = session(oldServer, PerformanceDiagnosticsConfig.Mode.AUTO, true);
        old.addSample(System.nanoTime(), Map.of("queued", 5L));
        var next = session(new Object(), PerformanceDiagnosticsConfig.Mode.AUTO, false);
        assertTrue(old.observes(oldServer));
        assertFalse(next.observes(oldServer));
        assertEquals(1, old.report("old", Map.of()).history().size());
        assertTrue(next.report("next", Map.of()).history().isEmpty());
        assertEquals(0, next.report("next", Map.of()).performance().totalTicks());
        assertNotEquals(old.report("old", Map.of()).sessionId(), next.report("next", Map.of()).sessionId());
    }

    @Test
    void offRejectsObservationAndRecordingEvenWhenRecordingConfigIsTrue() {
        Object server = new Object();
        var session = session(server, PerformanceDiagnosticsConfig.Mode.OFF, true);
        assertFalse(session.observes(server));
        assertFalse(session.setRecording(true));
        session.addSample(System.nanoTime(), Map.of());
        assertTrue(session.report("off", Map.of()).history().isEmpty());
    }

    @Test
    void recordingIsBoundedDetachedAndCanPauseWithoutLosingSamples() {
        var session = session(new Object(), PerformanceDiagnosticsConfig.Mode.ON, true);
        var queues = new HashMap<String, Long>();
        for (long sample = 0; sample < 125; sample++) {
            queues.put("queued", sample);
            session.addSample(System.nanoTime(), queues);
        }
        queues.put("queued", -1L);
        var snapshot = session.report("snapshot", queues);
        assertEquals(120, snapshot.history().size());
        assertEquals(5L, snapshot.history().getFirst().workQueues().get("queued"));
        assertEquals(124L, snapshot.history().getLast().workQueues().get("queued"));
        assertTrue(session.setRecording(false));
        session.addSample(System.nanoTime(), queues);
        assertEquals(snapshot.history(), session.report("paused", Map.of()).history());
        assertTrue(session.setRecording(true));
        session.addSample(System.nanoTime(), queues);
        assertEquals(-1L, session.report("resumed", Map.of()).history().getLast().workQueues().get("queued"));
        assertEquals(124L, snapshot.history().getLast().workQueues().get("queued"));
    }

    private static PerformanceDiagnostics.Session session(Object identity, PerformanceDiagnosticsConfig.Mode mode, boolean recording) {
        return new PerformanceDiagnostics.Session(identity,
                new PerformanceDiagnosticsConfig.Values(true, mode, recording, 100, 600, 50, false), Map.of(), Map.of());
    }
}
