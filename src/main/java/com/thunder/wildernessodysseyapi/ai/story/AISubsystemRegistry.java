package com.thunder.wildernessodysseyapi.ai.story;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Owns the bounded set of LLM-selectable personalities beneath A.E.T.H.E.R.
 *
 * <p>Player and model output can select only a canonical name registered here.
 * This keeps conversational routing independent from the scripted outage
 * responder and prevents chat text from creating arbitrary speaker labels.</p>
 */
final class AISubsystemRegistry {

    private static final int MAX_SUBSYSTEMS = 12;
    private static final int MAX_ALIASES = 8;
    private static final int MAX_ENTRIES = 16;
    private static final int MAX_TEXT_LENGTH = 300;

    private final String centralName;
    private final List<Profile> profiles;
    private final List<String> allowedSpeakers;
    private final Map<String, String> canonicalNames;

    AISubsystemRegistry(String centralName, List<AIConfig.Subsystem> configuredSubsystems) {
        this.centralName = clean(centralName, "Aether");
        Map<String, Profile> uniqueProfiles = new LinkedHashMap<>();
        if (configuredSubsystems != null) {
            for (AIConfig.Subsystem configured : configuredSubsystems) {
                if (configured == null || uniqueProfiles.size() >= MAX_SUBSYSTEMS) {
                    break;
                }
                Profile profile = fromConfig(configured);
                if (profile == null || profile.name().equalsIgnoreCase(this.centralName)) {
                    continue;
                }
                uniqueProfiles.putIfAbsent(profile.name().toLowerCase(Locale.ROOT), profile);
            }
        }
        this.profiles = List.copyOf(uniqueProfiles.values());

        Map<String, String> names = new LinkedHashMap<>();
        names.put(this.centralName.toLowerCase(Locale.ROOT), this.centralName);
        for (Profile profile : profiles) {
            names.put(profile.name().toLowerCase(Locale.ROOT), profile.name());
        }
        this.allowedSpeakers = List.copyOf(names.values());
        this.canonicalNames = Map.copyOf(names);
    }

    String centralName() {
        return centralName;
    }

    List<Profile> profiles() {
        return profiles;
    }

    List<String> allowedSpeakers() {
        return allowedSpeakers;
    }

    Optional<String> canonicalSpeaker(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(canonicalNames.get(candidate.trim().toLowerCase(Locale.ROOT)));
    }

    String canonicalOrCentral(String candidate) {
        return canonicalSpeaker(candidate).orElse(centralName);
    }

    Optional<Profile> profileFor(String speaker) {
        Optional<String> canonical = canonicalSpeaker(speaker);
        if (canonical.isEmpty() || canonical.get().equalsIgnoreCase(centralName)) {
            return Optional.empty();
        }
        return profiles.stream().filter(profile -> profile.name().equals(canonical.get())).findFirst();
    }

    Optional<String> findExplicitSpeaker(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String lower = message.toLowerCase(Locale.ROOT);
        // A named specialist takes precedence over a simultaneous mention of
        // Aether, such as "Aether, ask Eclipse about this fracture."
        for (Profile profile : profiles) {
            if (mentions(lower, profile.name()) || profile.aliases().stream().anyMatch(alias -> mentions(lower, alias))) {
                return Optional.of(profile.name());
            }
        }
        if (mentions(lower, centralName) || mentions(lower, "a.e.t.h.e.r") || mentions(lower, "central ai")) {
            return Optional.of(centralName);
        }
        return Optional.empty();
    }

    private static Profile fromConfig(AIConfig.Subsystem configured) {
        String name = clean(configured.getName(), "");
        String role = clean(configured.getRole(), "");
        if (name.isEmpty() || role.isEmpty()) {
            return null;
        }
        return new Profile(
                name,
                boundedList(configured.getAliases(), MAX_ALIASES),
                role,
                clean(configured.getPersonality(), "measured and helpful"),
                boundedList(configured.getKnowledge(), MAX_ENTRIES),
                boundedList(configured.getBoundaries(), MAX_ENTRIES)
        );
    }

    private static List<String> boundedList(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(Math.min(limit, values.size()));
        for (String value : values) {
            if (result.size() >= limit) {
                break;
            }
            String cleaned = clean(value, "");
            if (!cleaned.isEmpty() && !result.contains(cleaned)) {
                result.add(cleaned);
            }
        }
        return List.copyOf(result);
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String cleaned = value.replace("\u0000", "").trim();
        return cleaned.length() <= MAX_TEXT_LENGTH ? cleaned : cleaned.substring(0, MAX_TEXT_LENGTH).trim();
    }

    private static boolean mentions(String lowerMessage, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        String lowerCandidate = candidate.toLowerCase(Locale.ROOT);
        int from = 0;
        while (from < lowerMessage.length()) {
            int index = lowerMessage.indexOf(lowerCandidate, from);
            if (index < 0) {
                return false;
            }
            int end = index + lowerCandidate.length();
            boolean leftBoundary = index == 0 || !Character.isLetterOrDigit(lowerMessage.charAt(index - 1));
            boolean rightBoundary = end == lowerMessage.length()
                    || !Character.isLetterOrDigit(lowerMessage.charAt(end));
            if (leftBoundary && rightBoundary) {
                return true;
            }
            from = index + 1;
        }
        return false;
    }

    /** Immutable, prompt-safe subsystem definition. */
    record Profile(
            String name,
            List<String> aliases,
            String role,
            String personality,
            List<String> knowledge,
            List<String> boundaries
    ) {
    }
}
