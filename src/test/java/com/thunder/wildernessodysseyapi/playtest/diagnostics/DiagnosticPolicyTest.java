package com.thunder.wildernessodysseyapi.playtest.diagnostics;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticPolicyTest {
    @Test
    void acceptsOnlyHttpsOriginsAndLiteralLoopbackDevelopment() {
        assertEquals(URI.create("https://example.org"), DiagnosticProtocol.origin("https://example.org/"));
        assertEquals(URI.create("http://127.0.0.1:8123"), DiagnosticProtocol.origin("http://127.0.0.1:8123"));
        for (String value : new String[]{"http://example.org", "https://u:p@example.org", "https://example.org/path",
                "https://example.org?token=x", "file:///tmp", "https://example.org/#x"}) {
            assertThrows(IllegalArgumentException.class, () -> DiagnosticProtocol.origin(value), value);
        }
    }

    @Test
    void expiresUsingMonotonicTimeAndCannotRenewAfterLocalStop() {
        var gate = new DiagnosticLease();
        long generation = gate.generation();
        assertTrue(gate.accept(generation, new DiagnosticProtocol.Policy(true, "campaign-1", "tester-1", "collection-1", true, true, 600), 100));
        assertTrue(gate.active(100 + 59_000_000_000L));
        assertFalse(gate.active(100 + 60_000_000_000L));
        gate.stop();
        assertFalse(gate.accept(generation, new DiagnosticProtocol.Policy(true, "campaign-1", "tester-1", "collection-1", true, true, 60), 200));
    }

    @Test
    void deniesDisabledMalformedAndChangedIdentityPolicies() {
        var gate = new DiagnosticLease();
        long generation = gate.generation();
        gate.accept(generation, new DiagnosticProtocol.Policy(true, "campaign-1", "tester-1", "collection-1", true, false, 30), 0);
        assertFalse(gate.accept(generation, new DiagnosticProtocol.Policy(true, "campaign-2", "tester-1", "collection-1", true, false, 30), 1));
        assertFalse(gate.active(1));
        assertThrows(IllegalArgumentException.class, () -> DiagnosticProtocol.parsePolicy("{\"enabled\":\"true\"}"));
        assertThrows(IllegalArgumentException.class, () -> DiagnosticProtocol.parsePolicy("{\"enabled\":true}"));
        assertFalse(DiagnosticProtocol.parsePolicy("{\"schemaVersion\":\"1.0\",\"enabled\":false}").enabled());
    }
}
