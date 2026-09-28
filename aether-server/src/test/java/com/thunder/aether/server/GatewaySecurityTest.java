package com.thunder.aether.server;

import com.thunder.aether.server.config.ServerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real HTTP boundary; no real Ollama process or production service is used. */
class GatewaySecurityTest {
    @Test void monitoringCanReadAdmissionWithoutGrantingMutationRights() throws Exception {
        start(false);
        var response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + gateway.port() + "/v1/admin/admission"))
                .header("Authorization", "Bearer " + MONITOR).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("revision"));
    }
    @TempDir Path temporary;
    private AetherServer gateway;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private static final String INFERENCE = "test-inference-credential-0000000000000001";
    private static final String MONITOR = "test-monitoring-credential-000000000000001";
    private static final String ADMIN = "test-administration-credential-0000000001";
    private static final Map<String, String> ENV = Map.of(
            "AETHER_INFERENCE_TOKEN", INFERENCE,
            "AETHER_MONITORING_TOKEN", MONITOR,
            "AETHER_ADMINISTRATION_TOKEN", ADMIN);
    private static final String REQUEST = """
            {"requestId":"00000000-0000-0000-0000-000000000001","serverId":"official-server",
             "playerId":"00000000-0000-0000-0000-000000000002","playerName":"Player",
             "world":"minecraft:overworld","speaker":"Aether","message":"Hello"}
            """;

    @AfterEach void close() {
        if (gateway != null) { gateway.close(); }
        http.shutdownNow();
    }

    @Test void anonymousAndInvalidCredentialsCannotReadProtectedHealth() throws Exception {
        start(false);
        assertEquals(401, send("GET", "/health", null, null).statusCode());
        assertEquals(401, send("GET", "/health", "wrong", null).statusCode());
        assertEquals(200, send("GET", "/health", MONITOR, null).statusCode());
    }

    @Test void anonymousGenerationIsRejectedBeforeParsingOrOllamaAccess() throws Exception {
        start(false);
        assertEquals(401, send("POST", "/v1/aether/generate", null, "not json").statusCode());
    }

    @Test void credentialsCannotCrossInferenceMonitoringAndAdministrationScopes() throws Exception {
        start(false);
        assertEquals(403, send("GET", "/health", INFERENCE, null).statusCode());
        assertEquals(403, send("POST", "/v1/aether/generate", MONITOR, REQUEST).statusCode());
        assertEquals(403, send("POST", "/v1/aether/generate", ADMIN, REQUEST).statusCode());
        assertEquals(403, send("POST", "/v1/admin/admission", INFERENCE, "{}").statusCode());
        assertEquals(403, send("POST", "/v1/admin/admission", MONITOR, "{}").statusCode());
    }

    @Test void authenticatedInferenceCannotBypassTheHostingAndCapacityGate() throws Exception {
        start(false);
        var response = send("POST", "/v1/aether/generate", INFERENCE, REQUEST);
        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("ACTIVATION_REQUIRED"));
    }

    @Test void inferenceServiceCannotClaimAnotherMinecraftServerIdentity() throws Exception {
        start(true);
        var response = send("POST", "/v1/aether/generate", INFERENCE,
                REQUEST.replace("official-server", "another-server"));
        assertEquals(403, response.statusCode());
        assertTrue(response.body().contains("SERVER_ID_MISMATCH"));
    }

    @Test void legacyConfigurationDoesNotSilentlyRestoreAnonymousService() throws Exception {
        Path config = temporary.resolve("legacy.yml");
        Files.writeString(config, "server:\n  bind: 127.0.0.1\n  port: 0\n");
        gateway = new AetherServer(ServerConfig.load(config, Map.of()));
        gateway.start();
        assertEquals(401, send("GET", "/health", null, null).statusCode());
        assertEquals(401, send("POST", "/v1/aether/generate", null, REQUEST).statusCode());
    }

    private void start(boolean verified) throws Exception {
        Path config = temporary.resolve("gateway.yml");
        Files.writeString(config, """
                server:
                  bind: 127.0.0.1
                  port: 0
                security:
                  minecraft_server_id: official-server
                activation:
                  hosting_verified: %s
                  capacity_verified: %s
                  inference_enabled: true
                  verified_model: aether-custom:8b
                """.formatted(verified, verified));
        gateway = new AetherServer(ServerConfig.load(config, ENV));
        gateway.start();
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + gateway.port() + path))
                .timeout(Duration.ofSeconds(3));
        if (token != null) { request.header("Authorization", "Bearer " + token); }
        if (body != null) { request.header("Content-Type", "application/json"); }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
