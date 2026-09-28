package com.thunder.aether.server.security;

import com.sun.net.httpserver.Headers;
import java.time.Instant;
import java.util.List;

/** Non-inheriting service scopes: an administrator credential never permits inference. */
public final class ServiceAuthenticator implements AutoCloseable {
    public enum Scope { INFERENCE, MONITORING, ADMINISTRATION }
    private final GatewaySecurityConfig config;
    private final AccessTokenVerifier access;

    public ServiceAuthenticator(GatewaySecurityConfig config) {
        this(config, createVerifier(config));
    }

    /** Uses an independently composed trusted Access key source. */
    public ServiceAuthenticator(GatewaySecurityConfig config, AccessTokenVerifier access) {
        this.config = config;
        this.access = access;
    }

    private static AccessTokenVerifier createVerifier(GatewaySecurityConfig config) {
        if (config.accessIssuer().isEmpty()) { return null; }
        try { return new AccessTokenVerifier(config.accessIssuer(), config.accessAudience()); }
        catch (Exception invalid) { throw new IllegalArgumentException("INVALID_ACCESS_CONFIGURATION"); }
    }

    /** Rejects browser-origin requests and ambiguous credentials before reading their body. */
    public Scope authenticate(Headers headers, Instant now) {
        if (headers.containsKey("Origin") || headers.containsKey("Sec-Fetch-Site")) { return null; }
        List<String> values = headers.get("Authorization");
        if (values == null || values.size() != 1 || !values.getFirst().startsWith("Bearer ")) { return null; }
        String token = values.getFirst().substring(7);
        if (config.inference().matches(token, now)) { return Scope.INFERENCE; }
        if (config.monitoring().matches(token, now)
                && (config.monitoringClientId().isEmpty() || accessAllows(headers, config.monitoringClientId(), now))) {
            return Scope.MONITORING;
        }
        if (config.administration().matches(token, now)
                && (!config.administrationEnabled() || accessAllows(headers, config.administrationClientId(), now))) {
            return Scope.ADMINISTRATION;
        }
        return null;
    }

    private boolean accessAllows(Headers headers, String service, Instant now) {
        List<String> values = headers.get("Cf-Access-Jwt-Assertion");
        return access != null && values != null && values.size() == 1 && access.accepts(values.getFirst(), service, now);
    }

    @Override public void close() { if (access != null) { access.close(); } }
}
