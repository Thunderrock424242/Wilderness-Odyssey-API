package com.thunder.aether.server.security;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

/** Server-only trust configuration, loaded from operator settings and named environment secrets. */
public record GatewaySecurityConfig(String minecraftServerId, ServiceCredential inference,
        ServiceCredential monitoring, ServiceCredential administration, boolean administrationEnabled,
        String accessIssuer, String accessAudience, String monitoringClientId, String administrationClientId,
        Path stateDirectory) {
    public GatewaySecurityConfig {
        if (minecraftServerId == null || !minecraftServerId.matches("[A-Za-z0-9._-]{1,64}")
                || inference == null || monitoring == null || administration == null || stateDirectory == null
                || inference.sameSecret(monitoring) || inference.sameSecret(administration)
                || monitoring.sameSecret(administration)) {
            throw new IllegalArgumentException("INVALID_SECURITY_CONFIGURATION");
        }
        if (!accessIssuer.isEmpty() && !accessIssuer.matches("https://[a-zA-Z0-9-]+\\.cloudflareaccess\\.com")) {
            throw new IllegalArgumentException("INVALID_ACCESS_ISSUER");
        }
        if (administrationEnabled && (accessIssuer.isEmpty() || accessAudience.isBlank()
                || administrationClientId.isBlank())) {
            throw new IllegalArgumentException("ADMINISTRATION_REQUIRES_ACCESS");
        }
        if (accessAudience.length() > 256 || monitoringClientId.length() > 256 || administrationClientId.length() > 256) {
            throw new IllegalArgumentException("INVALID_ACCESS_CONFIGURATION");
        }
    }

    /** Absent settings never restore the former anonymous gateway. */
    public static GatewaySecurityConfig load(Map<?, ?> values, Map<String, String> environment, Path directory) {
        return new GatewaySecurityConfig(text(values, "minecraft_server_id", "wilderness-server"),
                credential(values, environment, "inference", "AETHER_INFERENCE_TOKEN"),
                credential(values, environment, "monitoring", "AETHER_MONITORING_TOKEN"),
                credential(values, environment, "administration", "AETHER_ADMINISTRATION_TOKEN"),
                flag(values, "administration_enabled"), text(values, "access_issuer", ""),
                text(values, "access_audience", ""), text(values, "monitoring_client_id", ""),
                text(values, "administration_client_id", ""), directory);
    }

    public static GatewaySecurityConfig locked() { return load(Map.of(), Map.of(), Path.of("aether-state")); }

    private static ServiceCredential credential(Map<?, ?> values, Map<String, String> env, String role, String defaultEnv) {
        String name = text(values, role + "_token_env", defaultEnv);
        if (!name.matches("[A-Z][A-Z0-9_]{0,127}")) { throw new IllegalArgumentException("INVALID_SECRET_REFERENCE"); }
        String expires = text(values, role + "_expires_at", "");
        return new ServiceCredential(env.get(name), expires.isEmpty() ? null : Instant.parse(expires));
    }

    public static String text(Map<?, ?> values, String name, String fallback) {
        Object value = values.get(name);
        if (value == null) { return fallback; }
        if (!(value instanceof String text)) { throw new IllegalArgumentException("INVALID_SECURITY_CONFIGURATION"); }
        return text;
    }

    public static boolean flag(Map<?, ?> values, String name) {
        Object value = values.get(name);
        if (value != null && !(value instanceof Boolean)) { throw new IllegalArgumentException("INVALID_SECURITY_CONFIGURATION"); }
        return Boolean.TRUE.equals(value);
    }

    @Override public String toString() { return "GatewaySecurityConfig[redacted]"; }
}
