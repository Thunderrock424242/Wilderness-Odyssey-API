package com.thunder.wildernessodysseyapi.ai.story;

/**
 * Immutable server-only public gateway configuration. Settings are never part of packets
 * or diagnostic text. The default is disabled until an explicit backend section exists.
 */
public record AIBackendConfig(boolean enabled, Mode mode, String baseUrl, String serverId,
                              int timeoutSeconds, int retryAttempts, int retryBackoffMillis,
                              int circuitCooldownSeconds, int maxConcurrentRequests, boolean sendPlayerMemory) {
    /** Hosting selection; both enabled modes use the same Aether HTTP protocol. */
    public enum Mode { REMOTE, LOCAL_DEV, DISABLED }

    public AIBackendConfig {
        mode = mode == null ? Mode.DISABLED : mode;
        enabled = enabled && mode != Mode.DISABLED;
        baseUrl = baseUrl == null ? "" : baseUrl.trim();
        serverId = serverId == null || serverId.isBlank() ? "wilderness-server" : serverId.trim();
        if (!serverId.matches("[A-Za-z0-9._-]{1,64}")) {
            enabled = false;
            serverId = "invalid-server-id";
        }
        timeoutSeconds = Math.max(1, Math.min(60, timeoutSeconds));
        retryAttempts = Math.max(1, Math.min(3, retryAttempts));
        retryBackoffMillis = Math.max(0, Math.min(2000, retryBackoffMillis));
        circuitCooldownSeconds = Math.max(1, Math.min(300, circuitCooldownSeconds));
        maxConcurrentRequests = Math.max(1, Math.min(8, maxConcurrentRequests));
        if (mode == Mode.LOCAL_DEV && !isLoopback(baseUrl)) {
            enabled = false;
        }
    }

    /** Older configurations remain deterministic-only until the new backend is configured. */
    public static AIBackendConfig defaults() {
        return new AIBackendConfig(false, Mode.DISABLED, "http://127.0.0.1:8085",
                "wilderness-server", 30, 3, 250, 30, 2, false);
    }

    private static boolean isLoopback(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** Do not let record-generated diagnostics expose a malformed URL containing credentials. */
    @Override
    public String toString() {
        return "AIBackendConfig[enabled=" + enabled + ", mode=" + mode + ", endpoint=<configured>]";
    }
}
