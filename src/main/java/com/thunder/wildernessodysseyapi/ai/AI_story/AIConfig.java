package com.thunder.wildernessodysseyapi.ai.AI_story;

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
    private final Onboarding onboarding = new Onboarding();

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

    public Onboarding getOnboarding() {
        return onboarding;
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
        private Integer retryAttempts;
        private Integer retryBackoffMillis;
        private String modelDownloadUrl;
        private String modelDownloadSha256;
        private String modelFileName;

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

        public Integer getRetryAttempts() {
            return retryAttempts;
        }

        public void setRetryAttempts(Integer retryAttempts) {
            this.retryAttempts = retryAttempts;
        }

        public Integer getRetryBackoffMillis() {
            return retryBackoffMillis;
        }

        public void setRetryBackoffMillis(Integer retryBackoffMillis) {
            this.retryBackoffMillis = retryBackoffMillis;
        }

        public String getModelDownloadUrl() {
            return modelDownloadUrl;
        }

        public void setModelDownloadUrl(String modelDownloadUrl) {
            this.modelDownloadUrl = modelDownloadUrl;
        }

        public String getModelDownloadSha256() {
            return modelDownloadSha256;
        }

        public void setModelDownloadSha256(String modelDownloadSha256) {
            this.modelDownloadSha256 = modelDownloadSha256;
        }

        public String getModelFileName() {
            return modelFileName;
        }

        public void setModelFileName(String modelFileName) {
            this.modelFileName = modelFileName;
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
        private Boolean atlasEnabled;
        private Boolean voiceEnabled;
        private Boolean speechRecognition;
        private String wakeWord;
        private String model;

        public Boolean getAtlasEnabled() {
            return atlasEnabled;
        }

        public void setAtlasEnabled(Boolean atlasEnabled) {
            this.atlasEnabled = atlasEnabled;
        }

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

    public static class Onboarding {
        private Boolean enabled;
        private String completionMessage;
        private String invalidChoiceMessage;
        private final List<OnboardingStep> steps = new ArrayList<>();

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getCompletionMessage() {
            return completionMessage;
        }

        public void setCompletionMessage(String completionMessage) {
            this.completionMessage = completionMessage;
        }

        public String getInvalidChoiceMessage() {
            return invalidChoiceMessage;
        }

        public void setInvalidChoiceMessage(String invalidChoiceMessage) {
            this.invalidChoiceMessage = invalidChoiceMessage;
        }

        public List<OnboardingStep> getSteps() {
            return steps;
        }
    }

    public static class OnboardingStep {
        private String prompt;
        private final List<String> choices = new ArrayList<>();
        private final List<String> responses = new ArrayList<>();

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }

        public List<String> getChoices() {
            return choices;
        }

        public List<String> getResponses() {
            return responses;
        }
    }
}
