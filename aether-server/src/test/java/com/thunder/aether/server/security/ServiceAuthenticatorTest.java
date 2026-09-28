package com.thunder.aether.server.security;

import com.sun.net.httpserver.Headers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ServiceAuthenticatorTest {
    @TempDir Path temporary;
    private static final String TOKEN = "test-administration-secret-00000000000001";

    @Test void administrationRequiresAnIndependentlyVerifiedAccessServiceToken() {
        var config = GatewaySecurityConfig.load(Map.of("administration_enabled", true,
                "access_issuer", "https://example.cloudflareaccess.com", "access_audience", "gateway-aud",
                "administration_client_id", "kinetic-admin"), Map.of("AETHER_ADMINISTRATION_TOKEN", TOKEN), temporary);
        var auth = new ServiceAuthenticator(config);
        Headers headers = new Headers();
        headers.set("Authorization", "Bearer " + TOKEN);
        assertNull(auth.authenticate(headers, Instant.now()));
        headers.set("Cf-Access-Jwt-Assertion", "forged.header.signature");
        assertNull(auth.authenticate(headers, Instant.now()));
    }

    @Test void expiredCredentialsAndBrowserCallsAreRejected() {
        var config = GatewaySecurityConfig.load(Map.of("inference_expires_at", "2026-01-01T00:00:00Z"),
                Map.of("AETHER_INFERENCE_TOKEN", TOKEN), temporary);
        Headers headers = new Headers();
        headers.set("Authorization", "Bearer " + TOKEN);
        var auth = new ServiceAuthenticator(config);
        assertNull(auth.authenticate(headers, Instant.parse("2026-01-01T00:00:00Z")));
        assertEquals(ServiceAuthenticator.Scope.INFERENCE,
                auth.authenticate(headers, Instant.parse("2025-12-31T23:59:59Z")));
        headers.set("Origin", "https://staff.example.com");
        assertNull(auth.authenticate(headers, Instant.parse("2025-12-31T23:59:59Z")));
    }

    @Test void duplicatedCredentialsCannotCollapseSeparateScopes() {
        assertThrows(IllegalArgumentException.class, () -> GatewaySecurityConfig.load(Map.of(),
                Map.of("AETHER_INFERENCE_TOKEN", TOKEN, "AETHER_MONITORING_TOKEN", TOKEN), temporary));
    }

    @Test void missingOrWeakCredentialsCannotEnableAnonymousAccess() {
        var auth = new ServiceAuthenticator(GatewaySecurityConfig.load(Map.of(), Map.of(), temporary));
        Headers headers = new Headers();
        headers.set("Authorization", "Bearer ");
        assertNull(auth.authenticate(headers, Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> GatewaySecurityConfig.load(Map.of(),
                Map.of("AETHER_INFERENCE_TOKEN", "CHANGE_ME"), temporary));
    }

    @Test void duplicateAuthorizationHeadersAreRejected() {
        var auth = new ServiceAuthenticator(GatewaySecurityConfig.load(Map.of(),
                Map.of("AETHER_INFERENCE_TOKEN", TOKEN), temporary));
        Headers headers = new Headers();
        headers.add("Authorization", "Bearer " + TOKEN);
        headers.add("Authorization", "Bearer " + TOKEN);
        assertNull(auth.authenticate(headers, Instant.now()));
    }
}
