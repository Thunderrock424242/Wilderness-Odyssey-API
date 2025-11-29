package com.thunder.wildernessodysseyapi.AI_story;

/**
 * Lightweight settings parsed from ai_config.yaml.
 */
public class AISettings {

    private boolean voiceEnabled = false;
    private boolean speechRecognition = false;
    private String modelName = "local-story-engine";
    private String wakeWord = "atlas";

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
}
