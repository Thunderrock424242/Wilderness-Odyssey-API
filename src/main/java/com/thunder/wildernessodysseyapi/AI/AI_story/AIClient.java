package com.thunder.wildernessodysseyapi.AI.AI_story;

import com.thunder.wildernessodysseyapi.AI.AI_perf.MemoryStore;
import com.thunder.wildernessodysseyapi.Core.ModConstants;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;

/**
 * AI client that prepares local model prompts using lore, memory,
 * and player context.
 */
public class AIClient {

    private static final String DEFAULT_LOCAL_BASE_URL = "http://127.0.0.1:11434";
    private final List<String> story = new ArrayList<>();
    private final List<String> corruptedLore = new ArrayList<>();
    private final List<String> backgroundHistory = new ArrayList<>();
    private String corruptedPrefix = "";
    private final AISettings settings = new AISettings();
    private final VoiceIntegration voiceIntegration = new VoiceIntegration(settings);
    private final MemoryStore memoryStore = new MemoryStore();
    private final AIKnowledgeStore knowledgeStore = new AIKnowledgeStore();
    private final AIOnboardingStore onboardingStore = new AIOnboardingStore();
    private LocalModelClient localModelClient;
    private String localSystemPrompt;
    private boolean autoStartLocalServer;
    private String localServerStartCommand;
    private String bundledServerResource;
    private String bundledServerArgs;
    private Process localServerProcess;
    private String localModelBaseUrl;
    private String localModelName;
    private boolean onboardingEnabled;
    private String onboardingCompletionMessage = "You're all set. You can ask me anything now.";
    private String onboardingInvalidChoiceMessage = "Pick one of the numbered options so I can guide you.";
    private final List<AIConfig.OnboardingStep> onboardingSteps = new ArrayList<>();

    public AIClient() {
        loadStory();
    }

    public String getWakeWord() {
        return settings.getWakeWord();
    }

    public boolean isAtlasEnabled() {
        return settings.isAtlasEnabled();
    }

    private void loadStory() {
        AIConfig config = AIConfigLoader.load();
        story.addAll(config.getStory());
        corruptedLore.addAll(config.getCorruptedData());
        backgroundHistory.addAll(config.getBackgroundHistory());
        if (config.getCorruptedPrefix() != null) {
            corruptedPrefix = config.getCorruptedPrefix();
        }
        applySettings(config);
        configureLocalModel(config.getLocalModel());
        configureOnboarding(config.getOnboarding());
    }

    public synchronized void scanGameData(MinecraftServer server) {
        if (server == null) {
            return;
        }
        // Crafting assistance is intentionally disabled; leave this hook for future non-recipe scans.
    }

    private void applySettings(AIConfig config) {
        if (config == null) {
            return;
        }
        AIConfig.Settings configSettings = config.getSettings();
        if (configSettings.getAtlasEnabled() != null) {
            settings.setAtlasEnabled(configSettings.getAtlasEnabled());
        }
        if (configSettings.getVoiceEnabled() != null) {
            settings.setVoiceEnabled(configSettings.getVoiceEnabled());
        }
        if (configSettings.getSpeechRecognition() != null) {
            settings.setSpeechRecognition(configSettings.getSpeechRecognition());
        }
        if (configSettings.getWakeWord() != null) {
            settings.setWakeWord(configSettings.getWakeWord());
        }
        if (configSettings.getModel() != null) {
            settings.setModelName(configSettings.getModel());
        }

        AIConfig.Personality personality = config.getPersonality();
        if (personality.getName() != null) {
            settings.setPersonaName(personality.getName());
        }
        if (personality.getTone() != null) {
            settings.setPersonalityTone(personality.getTone());
        }
        if (personality.getEmpathy() != null) {
            settings.setEmpathyLevel(personality.getEmpathy());
        }
    }

    private void configureLocalModel(AIConfig.LocalModel localModel) {
        if (localModel == null || localModel.getEnabled() == null || !localModel.getEnabled()) {
            return;
        }
        autoStartLocalServer = Boolean.TRUE.equals(localModel.getAutoStart());
        localServerStartCommand = localModel.getStartCommand();
        bundledServerResource = localModel.getBundledServerResource();
        bundledServerArgs = localModel.getBundledServerArgs();
        if (autoStartLocalServer) {
            startLocalModelServer();
            stopLocalModelServer();
        }
        String baseUrl = DEFAULT_LOCAL_BASE_URL;
        String modelName = localModel.getModel();
        if (baseUrl == null || baseUrl.isBlank() || modelName == null || modelName.isBlank()) {
            return;
        }
        localModelBaseUrl = baseUrl.trim();
        localModelName = modelName.trim();
        int timeoutSeconds = localModel.getTimeoutSeconds() == null ? 15 : localModel.getTimeoutSeconds();
        localSystemPrompt = localModel.getSystemPrompt();
        localModelClient = new LocalModelClient(localModelBaseUrl, localModelName, java.time.Duration.ofSeconds(timeoutSeconds));
    }

    private void configureOnboarding(AIConfig.Onboarding onboarding) {
        if (onboarding == null) {
            return;
        }
        onboardingEnabled = Boolean.TRUE.equals(onboarding.getEnabled());
        if (onboarding.getCompletionMessage() != null) {
            onboardingCompletionMessage = onboarding.getCompletionMessage();
        }
        if (onboarding.getInvalidChoiceMessage() != null) {
            onboardingInvalidChoiceMessage = onboarding.getInvalidChoiceMessage();
        }
        onboardingSteps.clear();
        onboardingSteps.addAll(onboarding.getSteps());
        if (onboardingEnabled && onboardingSteps.isEmpty()) {
            onboardingSteps.addAll(buildDefaultOnboardingSteps());
        }
    }

    private String formatSystemPrompt(String template) {
        if (template == null || template.isBlank()) {
            return "";
        }
        return template
                .replace("{persona}", settings.getPersonaName())
                .replace("{tone}", settings.getPersonalityTone())
                .replace("{empathy}", settings.getEmpathyLevel());
    }

    /**
     * Adds the message to memory and returns an offline generated reply that
     * blends world lore, recent player context, and a small set of
     * deterministic templates.
     *
     * @param player  player name
     * @param message player message
     * @return AI reply
     */
    public String sendMessage(String player, String message) {
        return sendMessage(null, player, message);
    }

    /**
     * Adds the message to per-world memory and returns an offline generated reply that
     * blends world lore, recent player context, and a small set of
     * deterministic templates.
     *
     * @param world   world or save identifier
     * @param player  player name
     * @param message player message
     * @return AI reply
     */
    public String sendMessage(String world, String player, String message) {
        return sendMessageWithVoice(world, player, message).text();
    }

    public VoiceIntegration.VoiceResult sendMessageWithVoice(String world, String player, String message) {
        if (!settings.isAtlasEnabled()) {
            return voiceIntegration.wrap("");
        }
        String learnedFact = knowledgeStore.extractLearnedFact(message);
        if (learnedFact != null) {
            boolean added = knowledgeStore.addFact(learnedFact);
            String reply = added
                    ? "Got it. I'll remember: " + learnedFact
                    : "I already have that logged: " + learnedFact;
            memoryStore.addAiMessage(world, player, settings.getPersonaName(), reply);
            return voiceIntegration.wrap(reply);
        }
        memoryStore.addPlayerMessage(world, player, message);
        String context = memoryStore.getRecentContext(world, player);
        String knowledgeContext = knowledgeStore.getContextSnippet();
        if (!knowledgeContext.isBlank()) {
            context = context.isBlank() ? knowledgeContext : context + "\n" + knowledgeContext;
        }
        if (localModelClient == null) {
            String reply = buildOfflineReply(world, player, message, true, false);
            memoryStore.addAiMessage(world, player, settings.getPersonaName(), reply);
            return voiceIntegration.wrap(reply);
        }
        String prompt = formatSystemPrompt(localSystemPrompt);
        String localContext = buildLocalModelContext(world, context);
        boolean startAttempted = ensureLocalServerStarted();
        var response = localModelClient.generateReply(prompt, message, localContext);
        if (response.isPresent()) {
            String reply = response.get();
            memoryStore.addAiMessage(world, player, settings.getPersonaName(), reply);
            stopLocalModelServerIfNeeded();
            return voiceIntegration.wrap(reply);
        }
        RetryResult retry = retryLocalModelAfterStart(prompt, message, localContext);
        if (retry.response().isPresent()) {
            String reply = retry.response().get();
            memoryStore.addAiMessage(world, player, settings.getPersonaName(), reply);
            stopLocalModelServerIfNeeded();
            return voiceIntegration.wrap(reply);
        }
        stopLocalModelServerIfNeeded();
        startAttempted = startAttempted || retry.started();
        String reply = buildOfflineReply(world, player, message, true, startAttempted);
        memoryStore.addAiMessage(world, player, settings.getPersonaName(), reply);
        return voiceIntegration.wrap(reply);
    }

    public String handleOnboarding(UUID playerId, String message) {
        if (!onboardingEnabled || onboardingSteps.isEmpty() || playerId == null) {
            return null;
        }
        int stepIndex = onboardingStore.getStep(playerId);
        if (stepIndex >= onboardingSteps.size()) {
            return null;
        }
        AIConfig.OnboardingStep step = onboardingSteps.get(stepIndex);
        int choiceIndex = resolveChoiceIndex(step, message);
        if (choiceIndex < 0) {
            return buildOnboardingPrompt(step, onboardingInvalidChoiceMessage);
        }
        String response = resolveChoiceResponse(step, choiceIndex);
        int nextStepIndex = stepIndex + 1;
        if (nextStepIndex >= onboardingSteps.size()) {
            onboardingStore.setStep(playerId, onboardingSteps.size());
            return combineResponses(response, onboardingCompletionMessage);
        }
        onboardingStore.setStep(playerId, nextStepIndex);
        String nextPrompt = buildOnboardingPrompt(onboardingSteps.get(nextStepIndex), null);
        return combineResponses(response, nextPrompt);
    }

    private boolean isLocalServerRunning() {
        return localServerProcess != null && localServerProcess.isAlive();
    }

    private record RetryResult(java.util.Optional<String> response, boolean started) {}

    private boolean ensureLocalServerStarted() {
        if (autoStartLocalServer && !isLocalServerRunning()) {
            return startLocalModelServer();
        }
        return false;
    }

    private RetryResult retryLocalModelAfterStart(String prompt, String message, String localContext) {
        if (!autoStartLocalServer || isLocalServerRunning()) {
            return new RetryResult(java.util.Optional.empty(), false);
        }
        boolean started = startLocalModelServer();
        return new RetryResult(localModelClient.generateReply(prompt, message, localContext), started);
    }

    private void stopLocalModelServerIfNeeded() {
        if (!autoStartLocalServer) {
            return;
        }
        stopLocalModelServer();
    }

    private void stopLocalModelServer() {
        if (localServerProcess == null) {
            return;
        }
        localServerProcess.destroy();
        localServerProcess = null;
    }

    private boolean startLocalModelServer() {
        if (isLocalServerRunning()) {
            return false;
        }
        if (localServerStartCommand == null || localServerStartCommand.isBlank()) {
            List<String> bundledCommand = buildBundledServerCommand();
            if (!bundledCommand.isEmpty()) {
                return startLocalModelServer(bundledCommand);
            }
            ModConstants.LOGGER.warn("Local model auto-start enabled but no start command is configured.");
            return false;
        }
        List<String> command = parseCommand(localServerStartCommand);
        if (command.isEmpty()) {
            ModConstants.LOGGER.warn("Local model auto-start command could not be parsed.");
            return false;
        }
        return startLocalModelServer(command);
    }

    private boolean startLocalModelServer(List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            localServerProcess = builder.start();
            ModConstants.LOGGER.info("Started local model server with command: {}", String.join(" ", command));
            return true;
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed to start local model server with command: {}", String.join(" ", command), e);
            return false;
        }
    }

    private List<String> parseCommand(String command) {
        List<String> parts = new ArrayList<>();
        if (command == null || command.isBlank()) {
            return parts;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)")
                .matcher(command);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                parts.add(matcher.group(1));
            } else if (matcher.group(2) != null) {
                parts.add(matcher.group(2));
            } else if (matcher.group(3) != null) {
                parts.add(matcher.group(3));
            }
        }
        return parts;
    }

    private List<String> buildBundledServerCommand() {
        if (bundledServerResource == null || bundledServerResource.isBlank()) {
            return List.of();
        }
        Path extractedBinary = extractBundledServerBinary(bundledServerResource);
        if (extractedBinary == null) {
            return List.of();
        }
        String fullCommand = extractedBinary.toAbsolutePath().toString();
        if (bundledServerArgs != null && !bundledServerArgs.isBlank()) {
            fullCommand = fullCommand + " " + bundledServerArgs.trim();
        }
        return parseCommand(fullCommand);
    }

    private Path extractBundledServerBinary(String resourcePath) {
        String normalized = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        Path outputDir = FMLPaths.CONFIGDIR.get().resolve("wildernessodysseyapi").resolve("local-model");
        String fileName = Path.of(normalized).getFileName() == null ? "local-model-server" : Path.of(normalized).getFileName().toString();
        Path outputPath = outputDir.resolve(fileName);
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed to create local model binary output directory at {}.", outputDir, e);
            return null;
        }
        if (Files.exists(outputPath)) {
            ensureExecutable(outputPath);
            return outputPath;
        }
        try (InputStream in = AIClient.class.getClassLoader().getResourceAsStream(normalized)) {
            if (in == null) {
                ModConstants.LOGGER.warn("Bundled local model binary not found at resource path {}.", normalized);
                return null;
            }
            Files.copy(in, outputPath, StandardCopyOption.REPLACE_EXISTING);
            ensureExecutable(outputPath);
            ModConstants.LOGGER.info("Extracted bundled local model server to {}.", outputPath);
            return outputPath;
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed to extract bundled local model binary from {}.", normalized, e);
            return null;
        }
    }

    private void ensureExecutable(Path binaryPath) {
        try {
            binaryPath.toFile().setExecutable(true, false);
        } catch (SecurityException e) {
            ModConstants.LOGGER.warn("Unable to mark bundled local model binary as executable: {}.", binaryPath, e);
        }
    }

    private String buildLocalModelContext(String world, String conversationContext) {
        StringBuilder builder = new StringBuilder();
        if (world != null && !world.isBlank()) {
            builder.append("World: ").append(world.trim()).append("\n");
        }
        if (!story.isEmpty()) {
            builder.append("Lore:\n");
            for (String line : story) {
                builder.append("- ").append(line).append("\n");
            }
        }
        if (!backgroundHistory.isEmpty()) {
            builder.append("Background history:\n");
            for (String line : backgroundHistory) {
                builder.append("- ").append(line).append("\n");
            }
        }
        if (!corruptedLore.isEmpty()) {
            builder.append("Corrupted fragments:\n");
            for (String line : corruptedLore) {
                builder.append("- ").append(line).append("\n");
            }
        }
        List<String> learnedFacts = knowledgeStore.getLearnedFacts();
        if (learnedFacts != null && !learnedFacts.isEmpty()) {
            builder.append("Learned facts:\n");
            for (String line : learnedFacts) {
                builder.append("- ").append(line).append("\n");
            }
        }
        if (conversationContext != null && !conversationContext.isBlank()) {
            builder.append("Recent conversation:\n").append(conversationContext.trim()).append("\n");
        }
        return builder.toString().trim();
    }

    private String buildOfflineReply(String world, String player, String message, boolean modelUnavailable, boolean startAttempted) {
        int seed = Math.floorMod(Objects.hash(world, player, message, settings.getPersonaName()), 10_000);
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        boolean question = (message != null && message.trim().endsWith("?"))
                || containsAny(lower, "how", "what", "where", "why", "can i", "should i", "do i");
        boolean comfort = containsAny(lower, "help", "stuck", "lost", "scared", "afraid", "panic", "worry", "worried");

        List<String> empathyLines = comfort
                ? List.of("I hear you.", "You're not alone out there.", "I'm with you.")
                : List.of("Got it.", "Understood.", "Copy that.");
        List<String> promptLines = question
                ? List.of(
                        "Here’s what I can share from the expedition logs:",
                        "Let me check the field notes—this stands out:",
                        "From the records we kept, try this lead:")
                : List.of(
                        "Thanks for the update—this connects to:",
                        "Noted. It lines up with this fragment:",
                        "I logged that. It echoes a prior report:");
        List<String> followUps = List.of(
                "Want me to keep watching for signals?",
                "If you want, tell me what you spot and I’ll compare notes.",
                "Need a quick scan of nearby ruins or supplies?");

        StringBuilder reply = new StringBuilder();
        reply.append(pickFrom(empathyLines, seed));
        reply.append(" ").append(pickFrom(promptLines, seed + 1));
        String lore = pickLoreLine(seed + 2);
        if (!lore.isBlank()) {
            reply.append(" ").append(lore);
        }
        reply.append(" ").append(pickFrom(followUps, seed + 3));

        if (modelUnavailable) {
            reply.append(" If you want richer answers, make sure the local AI server is running");
            if (localModelBaseUrl != null && !localModelBaseUrl.isBlank()) {
                reply.append(" at ").append(localModelBaseUrl);
            }
            if (startAttempted) {
                reply.append(" and that auto-start is configured correctly");
            }
            reply.append(".");
        }
        return reply.toString().trim();
    }

    private int resolveChoiceIndex(AIConfig.OnboardingStep step, String message) {
        if (step == null || step.getChoices().isEmpty()) {
            return -1;
        }
        if (message == null) {
            return -1;
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < step.getChoices().size(); i++) {
            String option = step.getChoices().get(i);
            int optionNumber = i + 1;
            if (trimmed.equalsIgnoreCase(option) || trimmed.equals(String.valueOf(optionNumber))) {
                return i;
            }
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.contains(option.toLowerCase(Locale.ROOT))) {
                return i;
            }
            if (lower.startsWith(optionNumber + ")") || lower.startsWith(optionNumber + ".")) {
                return i;
            }
        }
        return -1;
    }

    private String resolveChoiceResponse(AIConfig.OnboardingStep step, int choiceIndex) {
        if (step == null) {
            return "";
        }
        if (choiceIndex >= 0 && choiceIndex < step.getResponses().size()) {
            return step.getResponses().get(choiceIndex);
        }
        if (choiceIndex >= 0 && choiceIndex < step.getChoices().size()) {
            return "Logged: " + step.getChoices().get(choiceIndex) + ".";
        }
        return "";
    }

    private String buildOnboardingPrompt(AIConfig.OnboardingStep step, String extraLine) {
        if (step == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (extraLine != null && !extraLine.isBlank()) {
            builder.append(extraLine.trim()).append(" ");
        }
        if (step.getPrompt() != null && !step.getPrompt().isBlank()) {
            builder.append(step.getPrompt().trim());
        }
        if (!step.getChoices().isEmpty()) {
            builder.append(" ");
            for (int i = 0; i < step.getChoices().size(); i++) {
                if (i > 0) {
                    builder.append(" ");
                }
                builder.append(i + 1).append(") ").append(step.getChoices().get(i));
            }
        }
        return builder.toString().trim();
    }

    private String combineResponses(String first, String second) {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();
        if (left.isEmpty()) {
            return right;
        }
        if (right.isEmpty()) {
            return left;
        }
        return left + " " + right;
    }

    private List<AIConfig.OnboardingStep> buildDefaultOnboardingSteps() {
        List<AIConfig.OnboardingStep> steps = new ArrayList<>();
        steps.add(buildStep(
                "Welcome back to the surface. Which briefing do you want first?",
                List.of("Mission goals", "Supply checklist", "Hazard warnings"),
                List.of(
                        "Mission goals loaded: secure shelter, mark resources, and avoid deep craters.",
                        "Supply checklist loaded: water, rations, light sources, and repair tools.",
                        "Hazard warnings loaded: toxic dust, unstable debris, and rogue sensors.")));
        steps.add(buildStep(
                "Pick your expedition focus.",
                List.of("Exploration", "Rescue", "Research"),
                List.of(
                        "Exploration path set. I'll prioritize navigation tips and point-of-interest scans.",
                        "Rescue path set. I'll prioritize survivor signals and safe routes.",
                        "Research path set. I'll prioritize anomaly logs and artifact tracking.")));
        steps.add(buildStep(
                "How should I communicate?",
                List.of("Short updates", "Detailed reports", "Only when asked"),
                List.of(
                        "Short updates enabled.",
                        "Detailed reports enabled.",
                        "Silent standby enabled, I will respond only when addressed.")));
        return steps;
    }

    private AIConfig.OnboardingStep buildStep(String prompt, List<String> choices, List<String> responses) {
        AIConfig.OnboardingStep step = new AIConfig.OnboardingStep();
        step.setPrompt(prompt);
        if (choices != null) {
            step.getChoices().addAll(choices);
        }
        if (responses != null) {
            step.getResponses().addAll(responses);
        }
        return step;
    }

    private String pickLoreLine(int seed) {
        List<String> lore = new ArrayList<>(story.size() + backgroundHistory.size());
        lore.addAll(story);
        lore.addAll(backgroundHistory);
        if (!corruptedLore.isEmpty() && Math.floorMod(seed, 6) == 0) {
            String fragment = pickFrom(corruptedLore, seed);
            if (corruptedPrefix != null && !corruptedPrefix.isBlank()) {
                return corruptedPrefix + " " + fragment;
            }
            return fragment;
        }
        if (!lore.isEmpty()) {
            return pickFrom(lore, seed);
        }
        return "The surface is still unstable; keep your supplies close and move carefully.";
    }

    private static String pickFrom(List<String> options, int seed) {
        if (options == null || options.isEmpty()) {
            return "";
        }
        int index = Math.floorMod(seed, options.size());
        return options.get(index);
    }

    private static boolean containsAny(String value, String... tokens) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
