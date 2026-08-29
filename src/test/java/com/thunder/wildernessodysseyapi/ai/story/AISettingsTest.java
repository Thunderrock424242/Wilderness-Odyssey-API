package com.thunder.wildernessodysseyapi.ai.story;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies local-provider defaults and compatibility with legacy scripted configs. */
class AISettingsTest {

    @Test
    void defaultsToInstalledLocalProviderShape() {
        AISettings settings = new AISettings();

        assertTrue(settings.isOllamaEnabled());
        assertEquals("http://127.0.0.1:11434", settings.getEndpoint());
        assertEquals("llama3.2:latest", settings.getModelName());
        assertTrue(settings.isOllamaAutostartEnabled());
        assertEquals(20, settings.getOllamaStartupTimeoutSeconds());
        assertEquals("", settings.getOllamaExecutable());
    }

    @Test
    void legacyScriptedModelNameDoesNotReplaceLocalModelDefault() {
        AISettings settings = new AISettings();

        settings.setModelName("scripted-intent-engine");

        assertEquals("llama3.2:latest", settings.getModelName());
    }

    @Test
    void unsupportedProviderFallsBackToScriptedMode() {
        AISettings settings = new AISettings();

        settings.setProvider("remote-cloud-provider");

        assertFalse(settings.isOllamaEnabled());
        assertEquals("scripted", settings.getProvider());
    }

    @Test
    void modelBoundsAlwaysRetainLatestPlayerMessage() {
        AISettings settings = new AISettings();

        settings.setMaxHistoryMessages(0);
        settings.setRequestTimeoutSeconds(999);
        settings.setMaxOutputTokens(1);
        settings.setOllamaStartupTimeoutSeconds(999);

        assertEquals(1, settings.getMaxHistoryMessages());
        assertEquals(18, settings.getRequestTimeoutSeconds());
        assertEquals(32, settings.getMaxOutputTokens());
        assertEquals(60, settings.getOllamaStartupTimeoutSeconds());
    }
}
