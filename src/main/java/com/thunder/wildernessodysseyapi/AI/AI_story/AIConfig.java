package com.thunder.wildernessodysseyapi.AI.AI_story;

import java.util.ArrayList;
import java.util.List;

public class AIConfig {

    private final List<String> story = new ArrayList<>();
    private final List<String> corruptedData = new ArrayList<>();
    private final List<String> backgroundHistory = new ArrayList<>();
    private String corruptedPrefix;
    private final Personality personality = new Personality();
    private final Settings settings = new Settings();
    private final LocalModel localModel = new LocalModel();

    public List<String> getStory() {
        return story;
    }

    public List<String> getCorruptedData() {
        return corruptedData;
    }

    public List<String> getBackgroundHistory() {
        return backgroundHistory;
    }

    public String getCorruptedPrefix() {
        return corruptedPrefix;
    }

    public void setCorruptedPrefix(String corruptedPrefix) {
        this.corruptedPrefix = corruptedPrefix;
    }

    public Personality getPersonality() {
        return personality;
    }

    public Settings getSettings() {
        return settings;
    }

    public LocalModel getLocalModel() {
        return localModel;
    }

    public static class LocalModel {
        private Boolean enabled;
        private Boolean autoStart;
        private String baseUrl;
        private String model;
        private String systemPrompt;
        private String startCommand;
        private String bundledServerResource;
        private String bundledServerArgs;
        private Integer timeoutSeconds;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Boolean getAutoStart() {
            return autoStart;
        }

        public void setAutoStart(Boolean autoStart) {
            this.autoStart = autoStart;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public String getStartCommand() {
            return startCommand;
        }

        public void setStartCommand(String startCommand) {
            this.startCommand = startCommand;
        }

        public String getBundledServerResource() {
            return bundledServerResource;
        }

        public void setBundledServerResource(String bundledServerResource) {
            this.bundledServerResource = bundledServerResource;
        }

        public String getBundledServerArgs() {
            return bundledServerArgs;
        }

        public void setBundledServerArgs(String bundledServerArgs) {
            this.bundledServerArgs = bundledServerArgs;
        }

        public Integer getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(Integer timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }


    public static class Personality {
        private String name;
        private String tone;
        private String empathy;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getTone() {
            return tone;
        }

        public void setTone(String tone) {
            this.tone = tone;
        }

        public String getEmpathy() {
            return empathy;
        }

        public void setEmpathy(String empathy) {
            this.empathy = empathy;
        }
    }

    public static class Settings {
        private Boolean voiceEnabled;
        private Boolean speechRecognition;
        private String wakeWord;
        private String model;

        public Boolean getVoiceEnabled() {
            return voiceEnabled;
        }

        public void setVoiceEnabled(Boolean voiceEnabled) {
            this.voiceEnabled = voiceEnabled;
        }

        public Boolean getSpeechRecognition() {
            return speechRecognition;
        }

        public void setSpeechRecognition(Boolean speechRecognition) {
            this.speechRecognition = speechRecognition;
        }

        public String getWakeWord() {
            return wakeWord;
        }

        public void setWakeWord(String wakeWord) {
            this.wakeWord = wakeWord;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
