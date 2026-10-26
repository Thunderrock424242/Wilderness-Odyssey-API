package com.thunder.wildernessodysseyapi.playtest.diagnostics;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticHttpTest {
    @Test
    void redeemsInvitationThenScopesPolicyAndRequiresMatchingArtifactAcknowledgement() throws Exception {
        var authorization = new AtomicReference<String>();
        var body = new AtomicReference<String>();
        var response = new AtomicReference<>("{\"schemaVersion\":\"1.0\",\"accepted\":true,\"artifactId\":\"wrong\"}");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/playtest/v1/enroll", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = ("{\"schemaVersion\":\"1.0\",\"testerId\":\"tester-1\",\"campaignId\":\"campaign-1\",\"accessToken\":\"" + "a".repeat(40) + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.createContext("/api/playtest/v1/policy", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = "{\"schemaVersion\":\"1.0\",\"enabled\":false}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.createContext("/api/playtest/v1/artifacts", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var http = new DiagnosticHttp();
            var enrollment = http.enroll("http://127.0.0.1:" + server.getAddress().getPort(), UUID.randomUUID().toString(), "invitation-12345678");
            assertTrue(body.get().contains("\"consentVersion\":\"1\""));
            assertFalse(http.policy(enrollment).enabled());
            assertEquals("Bearer " + "a".repeat(40), authorization.get());
            var artifact = new DiagnosticProtocol.Artifact("1.0", UUID.randomUUID().toString(), "campaign-1", "tester-1", "collection-1", UUID.randomUUID().toString(), "log", "client", Instant.EPOCH.toString(), "text/plain", "utf8", "test");
            assertFalse(http.upload(enrollment, artifact));
            response.set("{\"schemaVersion\":\"1.0\",\"accepted\":true,\"artifactId\":\"" + artifact.artifactId() + "\"}");
            assertTrue(http.upload(enrollment, artifact));
        } finally { server.stop(0); }
    }
}
