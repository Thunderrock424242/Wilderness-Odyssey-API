package com.thunder.wildernessodysseyapi.ai.story;

import java.util.Locale;

/**
 * Lightweight settings parsed from ai_config.yaml.
 */
public class AISettings {

    private static final String DEFAULT_OLLAMA_MODEL = "llama3.2:latest";

    private boolean atlasEnabled = true;
    private String provider = "ollama";
    private String endpoint = "http://127.0.0.1:11434";
    private String modelName = DEFAULT_OLLAMA_MODEL;
    private int requestTimeoutSeconds = 15;
    private int maxHistoryMessages = 12;
    private int maxResponseCharacters = 800;
    private int maxOutputTokens = 180;
    private String wakeWord = "atlas";
    private String personaName = "Atlas";
    private String personalityTone = "warm and conversational";
    private String empathyLevel = "balanced";

    public boolean isAtlasEnabled() {
        return atlasEnabled;
    }

    public void setAtlasEnabled(boolean atlasEnabled) {
        this.atlasEnabled = atlasEnabled;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return;
        }
        String normalized = modelName.trim();
        if ("scripted-intent-engine".equalsIgnoreCase(normalized)
                || "local-story-engine".equalsIgnoreCase(normalized)) {
            return;
        }
        this.modelName = normalized;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return;
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        this.provider = "ollama".equals(normalized) ? "ollama" : "scripted";
    }

    public boolean isOllamaEnabled() {
        return "ollama".equals(provider);
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        if (endpoint != null && !endpoint.isBlank()) {
            this.endpoint = endpoint.trim();
        }
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
        this.requestTimeoutSeconds = Math.max(2, Math.min(18, requestTimeoutSeconds));
    }

    public int getMaxHistoryMessages() {
        return maxHistoryMessages;
    }

    public void setMaxHistoryMessages(int maxHistoryMessages) {
        this.maxHistoryMessages = Math.max(1, Math.min(20, maxHistoryMessages));
    }

    public int getMaxResponseCharacters() {
        return maxResponseCharacters;
    }

    public void setMaxResponseCharacters(int maxResponseCharacters) {
        this.maxResponseCharacters = Math.max(128, Math.min(2000, maxResponseCharacters));
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = Math.max(32, Math.min(512, maxOutputTokens));
    }

    public String getWakeWord() {
        return wakeWord;
    }

    public void setWakeWord(String wakeWord) {
        if (wakeWord != null && !wakeWord.isBlank()) {
            this.wakeWord = wakeWord.trim().toLowerCase();
        }
    }

    public String getPersonaName() {
        return personaName;
    }

    public void setPersonaName(String personaName) {
        if (personaName != null && !personaName.isBlank()) {
            this.personaName = personaName.trim();
        }
    }

    public String getPersonalityTone() {
        return personalityTone;
    }

    public void setPersonalityTone(String personalityTone) {
        if (personalityTone != null && !personalityTone.isBlank()) {
            this.personalityTone = personalityTone.trim();
        }
    }

    public String getEmpathyLevel() {
        return empathyLevel;
    }

    public void setEmpathyLevel(String empathyLevel) {
        if (empathyLevel != null && !empathyLevel.isBlank()) {
            this.empathyLevel = empathyLevel.trim();
        }
    }
}
