package com.thunder.wildernessodysseyapi.AI_story;

/**
 * Lightweight settings parsed from ai_config.yaml.
 */
public class AISettings {

    private boolean voiceEnabled = false;
    private boolean speechRecognition = false;
    private String modelName = "local-story-engine";
    private String wakeWord = "atlas";
    private String personaName = "Atlas";
    private String personalityTone = "warm and conversational";
    private String empathyLevel = "balanced";

    public boolean isVoiceEnabled() {
        return voiceEnabled;
    }

    public void setVoiceEnabled(boolean voiceEnabled) {
        this.voiceEnabled = voiceEnabled;
    }

    public boolean isSpeechRecognition() {
        return speechRecognition;
    }

    public void setSpeechRecognition(boolean speechRecognition) {
        this.speechRecognition = speechRecognition;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
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
