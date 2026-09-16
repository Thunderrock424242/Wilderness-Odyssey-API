package com.thunder.wildernessodysseyapi.ai.story;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Backend configuration cannot reactivate retired runtime management or leak credentials. */
class AIBackendConfigTest {
    @Test
    void parsesNestedRemoteAndLocalDevelopmentSettings() {
        AIConfig config = AIConfigLoader.parse("""
                ai_backend:
                  enabled: true
                  mode: remote
                  server_id: shared-server-a
                  remote:
                    base_url: https://aether.example.test
                    api_key: test-key-only
                    timeout_seconds: 30
                    retry_attempts: 3
                  local_dev:
                    enabled: false
                    base_url: http://127.0.0.1:8085
                """);
        assertTrue(config.getBackend().enabled());
        assertEquals("https://aether.example.test", config.getBackend().baseUrl());
        assertEquals("test-key-only", config.getBackend().apiKey());
        assertEquals("shared-server-a", config.getBackend().serverId());
        assertFalse(config.getBackend().sendPlayerMemory());
        assertFalse(config.getBackend().toString().contains("test-key-only"));
    }

    @Test
    void requiresExplicitLocalDevOptInAndLoopbackEndpoint() {
        for (String enabled : new String[]{"false", "true"}) {
            AIConfig config = AIConfigLoader.parse("""
                    ai_backend:
                      enabled: true
                      mode: local_dev
                      local_dev:
                        enabled: %s
                        base_url: http://example.test:8085
                    """.formatted(enabled));
            assertFalse(config.getBackend().enabled());
        }
        assertTrue(AIConfigLoader.parse("""
                ai_backend:
                  enabled: true
                  mode: local_dev
                  local_dev:
                    enabled: true
                    base_url: http://127.0.0.1:8085
                """).getBackend().enabled());
    }

    @Test
    void preservesLegacyConfigWithoutRedirectingItsOllamaEndpoint() {
        AIConfig config = AIConfigLoader.parse("""
                settings:
                  provider: ollama
                  endpoint: http://127.0.0.1:11434
                  ollama_autostart: true
                local_model:
                  enabled: true
                story:
                  - recovered custom canon
                player_memory:
                  natural_learning_enabled: true
                """);
        assertFalse(config.getBackend().enabled());
        assertEquals("recovered custom canon", config.getStory().getFirst());
        assertTrue(config.getPlayerMemory().getNaturalLearningEnabled());
        assertEquals("http://127.0.0.1:11434", config.getSettings().getEndpoint());
        assertEquals("http://127.0.0.1:8085", config.getBackend().baseUrl());
    }

    @Test
    void malformedDuplicateAndTaggedYamlFailClosed() {
        assertFalse(AIConfigLoader.parse("ai_backend: [invalid").getBackend().enabled());
        assertFalse(AIConfigLoader.parse("ai_backend: !!java.lang.ProcessBuilder {}").getBackend().enabled());
        assertFalse(AIConfigLoader.parse("ai_backend: {}\nai_backend: {}").getBackend().enabled());
        assertFalse(AIConfigLoader.parse("ai_backend:\n  enabled: true\n  mode: invalid").getBackend().enabled());
    }

    @Test
    void boundsLimitsAndRedactsRecordDiagnostics() {
        AIBackendConfig config = new AIBackendConfig(true, AIBackendConfig.Mode.REMOTE,
                "https://user:secret@example.test", "secret", "server", 1000, 999, 9999, 9999, 999, false);
        assertEquals(60, config.timeoutSeconds());
        assertEquals(3, config.retryAttempts());
        assertEquals(2000, config.retryBackoffMillis());
        assertEquals(300, config.circuitCooldownSeconds());
        assertEquals(8, config.maxConcurrentRequests());
        assertFalse(config.toString().contains("secret"));
        assertEquals("", config.withApiKey("CHANGE_ME").apiKey());
    }
}
