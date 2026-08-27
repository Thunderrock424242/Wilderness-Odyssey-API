package com.thunder.wildernessodysseyapi.ai.story;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Owns bounded, local profile memories explicitly shared by each player.
 *
 * <p>Recent dialogue remains owned by {@code MemoryStore}. This store keeps
 * only durable personal details such as a preferred name, interests, or
 * communication preferences. Stored text is always treated as untrusted data
 * when supplied to the local model.</p>
 */
final class AIPlayerProfileStore {

    static final int DEFAULT_MAX_MEMORIES = 12;
    static final int HARD_MAX_MEMORIES = 24;
    static final int MAX_MEMORY_CHARACTERS = 180;

    private static final int MAX_PROFILES = 64;
    private static final String CONFIG_NAME = "aether_player_profiles.yaml";
    private static final String ROOT_KEY = "profiles";
    private static final Set<String> EMPTY_DISCLOSURES = Set.of(
            "it", "that", "this", "things", "stuff", "you", "minecraft"
    );
    private static final List<String> SENSITIVE_MARKERS = List.of(
            "password", "passcode", "api key", "access token", "auth token",
            "secret key", "credit card", "debit card", "social security",
            "ssn", "email", "e-mail", "phone", "telephone", "mobile number",
            "contact number", "contact me at", "home address", "street address",
            "ip address", "address is"
    );

    private final Path configPath;
    private final Map<String, LinkedHashSet<String>> profiles = new LinkedHashMap<>();

    AIPlayerProfileStore() {
        this(FMLPaths.CONFIGDIR.get().resolve(CONFIG_NAME));
    }

    AIPlayerProfileStore(Path configPath) {
        this.configPath = configPath.toAbsolutePath().normalize();
        load();
    }

    /**
     * Learns one stable detail from an explicit memory request or a bounded
     * natural self-disclosure.
     */
    synchronized LearningResult learn(
            String playerKey,
            String message,
            boolean naturalLearningEnabled,
            int maximumMemories
    ) {
        String safeKey = normalizePlayerKey(playerKey);
        String safeMessage = cleanValue(message, MAX_MEMORY_CHARACTERS + 80);
        if (safeKey.isEmpty() || safeMessage.isEmpty()) {
            return LearningResult.none();
        }

        Optional<String> explicitDisclosure = extractExplicitDisclosure(safeMessage);
        boolean explicit = explicitDisclosure.isPresent();
        String disclosure = explicitDisclosure.orElse(safeMessage);
        Optional<String> memory = classifyDisclosure(disclosure, explicit);
        if (memory.isEmpty()) {
            return explicit
                    ? LearningResult.rejected("I couldn't identify a safe, stable detail to save.")
                    : LearningResult.none();
        }
        if (!explicit && !naturalLearningEnabled) {
            return LearningResult.none();
        }
        if (containsSensitiveData(memory.get())) {
            return explicit
                    ? LearningResult.rejected("I won't save passwords, contact details, addresses, or other secrets.")
                    : LearningResult.none();
        }

        AddOutcome outcome = addMemory(safeKey, memory.get(), maximumMemories);
        if (outcome == AddOutcome.REJECTED) {
            return explicit
                    ? LearningResult.rejected("I can't save another local player profile in this installation.")
                    : LearningResult.none();
        }
        return new LearningResult(explicit, true, outcome == AddOutcome.ADDED, memory.get(), "");
    }

    /** Returns profile notes formatted as model data rather than instructions. */
    synchronized String getContextSnippet(String playerKey, int maximumMemories) {
        List<String> memories = getMemories(playerKey, maximumMemories);
        if (memories.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder();
        for (String memory : memories) {
            context.append("- ").append(memory).append("\n");
        }
        return context.toString().trim();
    }

    /** Builds a direct, truthful answer to a player profile-recall request. */
    synchronized String describeForPlayer(String playerKey, int maximumMemories) {
        List<String> memories = getMemories(playerKey, maximumMemories);
        if (memories.isEmpty()) {
            return "I don't have any saved profile memories about you yet. "
                    + "You can tell me what you enjoy, what to call you, or say 'remember that...'";
        }
        return "I remember what you shared: " + String.join("; ", memories)
                + ". You can ask me to forget what I know about you at any time.";
    }

    /** Clears only the requesting player's durable profile. */
    synchronized boolean clear(String playerKey) {
        String safeKey = normalizePlayerKey(playerKey);
        if (safeKey.isEmpty() || profiles.remove(safeKey) == null) {
            return false;
        }
        save();
        return true;
    }

    synchronized List<String> getMemories(String playerKey, int maximumMemories) {
        String safeKey = normalizePlayerKey(playerKey);
        LinkedHashSet<String> memories = profiles.get(safeKey);
        if (memories == null || memories.isEmpty()) {
            return List.of();
        }
        int limit = boundedLimit(maximumMemories);
        List<String> snapshot = new ArrayList<>(Math.min(limit, memories.size()));
        int skip = Math.max(0, memories.size() - limit);
        int index = 0;
        for (String memory : memories) {
            if (index++ >= skip) {
                snapshot.add(memory);
            }
        }
        return List.copyOf(snapshot);
    }

    static boolean isRecallRequest(String message) {
        String normalized = normalizeRequest(message);
        return normalized.contains("what do you remember about me")
                || normalized.contains("what do you know about me")
                || normalized.contains("what have you learned about me")
                || normalized.equals("show my profile")
                || normalized.equals("tell me what you remember about me");
    }

    static boolean isForgetRequest(String message) {
        String normalized = normalizeRequest(message);
        return normalized.contains("forget what you know about me")
                || normalized.contains("forget everything about me")
                || normalized.equals("forget me")
                || normalized.equals("clear my profile")
                || normalized.equals("delete my profile");
    }

    private AddOutcome addMemory(String playerKey, String memory, int maximumMemories) {
        LinkedHashSet<String> existing = profiles.get(playerKey);
        if (existing == null) {
            if (profiles.size() >= MAX_PROFILES) {
                return AddOutcome.REJECTED;
            }
            existing = new LinkedHashSet<>();
            profiles.put(playerKey, existing);
        }

        for (String value : existing) {
            if (value.equalsIgnoreCase(memory)) {
                return AddOutcome.UNCHANGED;
            }
        }

        String replaceablePrefix = replaceablePrefix(memory);
        if (!replaceablePrefix.isEmpty()) {
            existing.removeIf(value -> value.regionMatches(true, 0, replaceablePrefix, 0, replaceablePrefix.length()));
        }

        int limit = boundedLimit(maximumMemories);
        while (existing.size() >= limit) {
            Iterator<String> iterator = existing.iterator();
            if (!iterator.hasNext()) {
                break;
            }
            iterator.next();
            iterator.remove();
        }
        existing.add(memory);
        save();
        return AddOutcome.ADDED;
    }

    private static Optional<String> extractExplicitDisclosure(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        for (String prefix : List.of(
                "aether remember ", "aether learn ", "atlas remember ", "atlas learn ",
                "please remember that ", "remember that ", "learn that ", "remember:", "learn:"
        )) {
            if (lower.startsWith(prefix)) {
                String value = message.substring(prefix.length()).trim();
                return value.isEmpty() ? Optional.empty() : Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> classifyDisclosure(String disclosure, boolean explicit) {
        String cleaned = cleanValue(disclosure, MAX_MEMORY_CHARACTERS);
        if (cleaned.isEmpty()) {
            return Optional.empty();
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);

        Optional<String> preferredName = valueAfterPrefix(cleaned, lower, "call me ")
                .or(() -> valueAfterPrefix(cleaned, lower, "my name is "));
        if (preferredName.isPresent()) {
            String name = cleanName(preferredName.get());
            return name.isEmpty() ? Optional.empty() : Optional.of("Preferred name: " + name);
        }

        Optional<String> value = valueAfterPrefix(cleaned, lower, "i don't like ")
                .or(() -> valueAfterPrefix(cleaned, lower, "i do not like "));
        if (value.isPresent()) {
            return stableValue("Dislikes: ", value.get());
        }
        value = valueAfterPrefix(cleaned, lower, "i like ")
                .or(() -> valueAfterPrefix(cleaned, lower, "i love "));
        if (value.isPresent()) {
            return stableValue("Likes: ", value.get());
        }
        value = valueAfterPrefix(cleaned, lower, "i prefer ");
        if (value.isPresent()) {
            return stableValue("Prefers: ", value.get());
        }
        value = valueAfterPrefix(cleaned, lower, "i'm interested in ")
                .or(() -> valueAfterPrefix(cleaned, lower, "i am interested in "));
        if (value.isPresent()) {
            return stableValue("Interested in: ", value.get());
        }
        value = valueAfterPrefix(cleaned, lower, "my goal is ")
                .or(() -> valueAfterPrefix(cleaned, lower, "i want to focus on "));
        if (value.isPresent()) {
            return stableValue("Current goal: ", value.get());
        }
        if (lower.startsWith("my favorite ")) {
            int separator = lower.indexOf(" is ", "my favorite ".length());
            if (separator > 0) {
                String subject = cleanValue(cleaned.substring("my favorite ".length(), separator), 40);
                String favorite = cleanValue(cleaned.substring(separator + 4), 100);
                if (!subject.isEmpty() && !favorite.isEmpty()) {
                    return Optional.of(cleanValue("Favorite " + subject + ": " + favorite, MAX_MEMORY_CHARACTERS));
                }
            }
        }
        return explicit
                ? Optional.of(cleanValue("Personal note: " + cleaned, MAX_MEMORY_CHARACTERS))
                : Optional.empty();
    }

    private static Optional<String> stableValue(String label, String rawValue) {
        String value = cleanValue(firstDisclosureClause(rawValue), MAX_MEMORY_CHARACTERS - label.length());
        if (value.isEmpty() || EMPTY_DISCLOSURES.contains(value.toLowerCase(Locale.ROOT))) {
            return Optional.empty();
        }
        return Optional.of(label + value);
    }

    private static String firstDisclosureClause(String value) {
        if (value == null) {
            return "";
        }
        int end = value.length();
        for (String separator : List.of("?", "!", ". ", "; ")) {
            int index = value.indexOf(separator);
            if (index >= 0) {
                end = Math.min(end, index);
            }
        }
        return value.substring(0, end);
    }

    private static Optional<String> valueAfterPrefix(String original, String lower, String prefix) {
        if (!lower.startsWith(prefix)) {
            return Optional.empty();
        }
        String value = original.substring(prefix.length()).trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    private static String cleanName(String value) {
        String name = cleanValue(value, 40);
        if (name.isEmpty() || !name.matches("[\\p{L}\\p{N}][\\p{L}\\p{N} ._'\\-]{0,39}")) {
            return "";
        }
        return name;
    }

    private static String cleanValue(String value, int maximumCharacters) {
        if (value == null || maximumCharacters <= 0) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(Math.min(value.length(), maximumCharacters));
        boolean previousWhitespace = false;
        for (int index = 0; index < value.length() && cleaned.length() < maximumCharacters; index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character)) {
                continue;
            }
            if (Character.isWhitespace(character)) {
                if (!previousWhitespace && cleaned.length() > 0) {
                    cleaned.append(' ');
                }
                previousWhitespace = true;
            } else {
                cleaned.append(character);
                previousWhitespace = false;
            }
        }
        String result = cleaned.toString().trim();
        while (!result.isEmpty() && ".!?;,".indexOf(result.charAt(result.length() - 1)) >= 0) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result;
    }

    private static boolean containsSensitiveData(String memory) {
        String lower = memory.toLowerCase(Locale.ROOT);
        return SENSITIVE_MARKERS.stream().anyMatch(lower::contains)
                || lower.matches(".*\\b[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}\\b.*");
    }

    private static String replaceablePrefix(String memory) {
        for (String prefix : List.of("Preferred name: ", "Current goal: ")) {
            if (memory.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return prefix;
            }
        }
        return "";
    }

    private static int boundedLimit(int requested) {
        return Math.max(1, Math.min(HARD_MAX_MEMORIES, requested));
    }

    private static String normalizePlayerKey(String playerKey) {
        if (playerKey == null || playerKey.isBlank()) {
            return "";
        }
        String normalized = playerKey.trim().toLowerCase(Locale.ROOT);
        StringBuilder safe = new StringBuilder(Math.min(normalized.length(), 80));
        for (int index = 0; index < normalized.length() && safe.length() < 80; index++) {
            char character = normalized.charAt(index);
            if (Character.isLetterOrDigit(character) || character == '-' || character == '_' || character == '.') {
                safe.append(character);
            }
        }
        return safe.toString();
    }

    private static String normalizeRequest(String message) {
        return cleanValue(message, 220).toLowerCase(Locale.ROOT);
    }

    private void load() {
        if (!Files.exists(configPath)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(configPath, StandardCharsets.UTF_8);
            boolean inProfiles = false;
            String currentProfile = "";
            for (String line : lines) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int indentation = leadingSpaces(line);
                if (indentation == 0) {
                    inProfiles = trimmed.equals(ROOT_KEY + ":");
                    currentProfile = "";
                    continue;
                }
                if (!inProfiles) {
                    continue;
                }
                if (indentation == 2 && trimmed.endsWith(":")) {
                    String key = unquote(trimmed.substring(0, trimmed.length() - 1).trim());
                    currentProfile = normalizePlayerKey(key);
                    if (!currentProfile.isEmpty() && profiles.size() < MAX_PROFILES) {
                        profiles.computeIfAbsent(currentProfile, ignored -> new LinkedHashSet<>());
                    } else {
                        currentProfile = "";
                    }
                    continue;
                }
                if (indentation >= 4 && !currentProfile.isEmpty() && trimmed.startsWith("- ")) {
                    String memory = cleanValue(unquote(trimmed.substring(2).trim()), MAX_MEMORY_CHARACTERS);
                    LinkedHashSet<String> profile = profiles.get(currentProfile);
                    if (!memory.isEmpty() && profile != null && profile.size() < HARD_MAX_MEMORIES) {
                        profile.add(memory);
                    }
                }
            }
            profiles.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        } catch (IOException exception) {
            ModConstants.LOGGER.warn("Failed to read Aether player profiles at {}.", configPath, exception);
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(configPath.getParent());
            StringBuilder yaml = new StringBuilder();
            yaml.append("# Local Aether profile memories explicitly shared by players.\n")
                    .append("# Say 'forget what you know about me' in private single-player chat to remove your entry.\n")
                    .append(ROOT_KEY).append(":\n");
            for (Map.Entry<String, LinkedHashSet<String>> entry : profiles.entrySet()) {
                yaml.append("  \"").append(escapeYaml(entry.getKey())).append("\":\n");
                for (String memory : entry.getValue()) {
                    yaml.append("    - \"").append(escapeYaml(memory)).append("\"\n");
                }
            }

            Path temporary = configPath.resolveSibling(configPath.getFileName() + ".tmp");
            Files.writeString(temporary, yaml.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        configPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            ModConstants.LOGGER.warn("Failed to persist Aether player profiles at {}.", configPath, exception);
        }
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            value = value.substring(1, value.length() - 1);
        }
        StringBuilder decoded = new StringBuilder(value.length());
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (escaped) {
                decoded.append(character == 'n' ? ' ' : character);
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else {
                decoded.append(character);
            }
        }
        if (escaped) {
            decoded.append('\\');
        }
        return decoded.toString();
    }

    private static String escapeYaml(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    enum AddOutcome {
        ADDED,
        UNCHANGED,
        REJECTED
    }

    /** Result of one bounded profile-learning attempt. */
    record LearningResult(
            boolean explicitRequest,
            boolean accepted,
            boolean changed,
            String memory,
            String rejectionMessage
    ) {
        LearningResult {
            memory = memory == null ? "" : memory;
            rejectionMessage = rejectionMessage == null ? "" : rejectionMessage;
        }

        static LearningResult none() {
            return new LearningResult(false, false, false, "", "");
        }

        static LearningResult rejected(String message) {
            return new LearningResult(true, false, false, "", message);
        }
    }
}
