package com.thunder.wildernessodysseyapi.ai.story;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model representing the parsed AI YAML/JSON configuration structure.
 */
public class AIConfig {

    private final List<String> story = new ArrayList<>();
    private final List<String> corruptedData = new ArrayList<>();
    private final List<String> backgroundHistory = new ArrayList<>();
    private final List<String> authoritativeKnowledge = new ArrayList<>();
    private final List<String> knowledgeBoundaries = new ArrayList<>();
    private final List<Subsystem> subsystems = new ArrayList<>();
    private String corruptedPrefix;
    private final Personality personality = new Personality();
    private final Settings settings = new Settings();
    private final PlayerMemory playerMemory = new PlayerMemory();
    private final Onboarding onboarding = new Onboarding();
    private final Fallback fallback = new Fallback();

    public List<String> getStory() {
        return story;
    }

    public List<String> getCorruptedData() {
        return corruptedData;
    }

    public List<String> getBackgroundHistory() {
        return backgroundHistory;
    }

    public List<String> getAuthoritativeKnowledge() {
        return authoritativeKnowledge;
    }

    public List<String> getKnowledgeBoundaries() {
        return knowledgeBoundaries;
    }

    public List<Subsystem> getSubsystems() {
        return subsystems;
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

    public PlayerMemory getPlayerMemory() {
        return playerMemory;
    }

    public Onboarding getOnboarding() {
        return onboarding;
    }

    public Fallback getFallback() {
        return fallback;
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
        private String wakeWord;
        private String provider;
        private String endpoint;
        private String model;
        private Integer requestTimeoutSeconds;
        private Integer maxHistoryMessages;
        private Integer maxResponseCharacters;
        private Integer maxOutputTokens;

        public Boolean getAtlasEnabled() {
            return atlasEnabled;
        }

        public void setAtlasEnabled(Boolean atlasEnabled) {
            this.atlasEnabled = atlasEnabled;
        }

        public String getWakeWord() {
            return wakeWord;
        }

        public void setWakeWord(String wakeWord) {
            this.wakeWord = wakeWord;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Integer getRequestTimeoutSeconds() {
            return requestTimeoutSeconds;
        }

        public void setRequestTimeoutSeconds(Integer requestTimeoutSeconds) {
            this.requestTimeoutSeconds = requestTimeoutSeconds;
        }

        public Integer getMaxHistoryMessages() {
            return maxHistoryMessages;
        }

        public void setMaxHistoryMessages(Integer maxHistoryMessages) {
            this.maxHistoryMessages = maxHistoryMessages;
        }

        public Integer getMaxResponseCharacters() {
            return maxResponseCharacters;
        }

        public void setMaxResponseCharacters(Integer maxResponseCharacters) {
            this.maxResponseCharacters = maxResponseCharacters;
        }

        public Integer getMaxOutputTokens() {
            return maxOutputTokens;
        }

        public void setMaxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
        }
    }

    /** Privacy and size controls for persistent conversational player profiles. */
    public static class PlayerMemory {
        private Boolean enabled;
        private Boolean naturalLearningEnabled;
        private Integer maxMemoriesPerPlayer;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Boolean getNaturalLearningEnabled() {
            return naturalLearningEnabled;
        }

        public void setNaturalLearningEnabled(Boolean naturalLearningEnabled) {
            this.naturalLearningEnabled = naturalLearningEnabled;
        }

        public Integer getMaxMemoriesPerPlayer() {
            return maxMemoriesPerPlayer;
        }

        public void setMaxMemoriesPerPlayer(Integer maxMemoriesPerPlayer) {
            this.maxMemoriesPerPlayer = maxMemoriesPerPlayer;
        }
    }

    /** Configured LLM personality operating beneath the central Aether intelligence. */
    public static class Subsystem {
        private String name;
        private String role;
        private String personality;
        private final List<String> aliases = new ArrayList<>();
        private final List<String> knowledge = new ArrayList<>();
        private final List<String> boundaries = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getPersonality() {
            return personality;
        }

        public void setPersonality(String personality) {
            this.personality = personality;
        }

        public List<String> getAliases() {
            return aliases;
        }

        public List<String> getKnowledge() {
            return knowledge;
        }

        public List<String> getBoundaries() {
            return boundaries;
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

    public static class Fallback {
        private Boolean enabled;
        private String menuPrompt;
        private String unavailableHint;
        private Integer minimumKeywordMatches;
        private final List<String> unknownResponses = new ArrayList<>();
        private final List<FallbackPersona> personas = new ArrayList<>();

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getMenuPrompt() {
            return menuPrompt;
        }

        public void setMenuPrompt(String menuPrompt) {
            this.menuPrompt = menuPrompt;
        }

        public String getUnavailableHint() {
            return unavailableHint;
        }

        public void setUnavailableHint(String unavailableHint) {
            this.unavailableHint = unavailableHint;
        }

        public Integer getMinimumKeywordMatches() {
            return minimumKeywordMatches;
        }

        public void setMinimumKeywordMatches(Integer minimumKeywordMatches) {
            this.minimumKeywordMatches = minimumKeywordMatches;
        }

        public List<String> getUnknownResponses() {
            return unknownResponses;
        }

        public List<FallbackPersona> getPersonas() {
            return personas;
        }
    }

    public static class FallbackPersona {
        private String name;
        private String introduction;
        private String promptFile;
        private final List<String> aliases = new ArrayList<>();
        private final List<FallbackPrompt> prompts = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getIntroduction() {
            return introduction;
        }

        public void setIntroduction(String introduction) {
            this.introduction = introduction;
        }

        public String getPromptFile() {
            return promptFile;
        }

        public void setPromptFile(String promptFile) {
            this.promptFile = promptFile;
        }

        public List<String> getAliases() {
            return aliases;
        }

        public List<FallbackPrompt> getPrompts() {
            return prompts;
        }
    }

    public static class FallbackPrompt {
        private String label;
        private String intent;
        private String response;
        private Integer minimumMatches;
        private final List<String> triggers = new ArrayList<>();
        private final List<String> keywords = new ArrayList<>();
        private final List<String> responses = new ArrayList<>();
        private final List<String> requiredContext = new ArrayList<>();
        private final List<String> blockedContext = new ArrayList<>();

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getIntent() {
            return intent;
        }

        public void setIntent(String intent) {
            this.intent = intent;
        }

        public String getResponse() {
            return response;
        }

        public void setResponse(String response) {
            this.response = response;
        }

        public Integer getMinimumMatches() {
            return minimumMatches;
        }

        public void setMinimumMatches(Integer minimumMatches) {
            this.minimumMatches = minimumMatches;
        }

        public List<String> getTriggers() {
            return triggers;
        }

        public List<String> getKeywords() {
            return keywords;
        }

        public List<String> getResponses() {
            return responses;
        }

        public List<String> getRequiredContext() {
            return requiredContext;
        }

        public List<String> getBlockedContext() {
            return blockedContext;
        }
    }
}
