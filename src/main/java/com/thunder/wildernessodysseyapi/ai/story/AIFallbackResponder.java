package com.thunder.wildernessodysseyapi.ai.story;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic intent responder for A.E.T.H.E.R. It feels conversational by
 * matching intent keywords and context, while staying fully scripted.
 */
public class AIFallbackResponder {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(^|\\D)([1-9][0-9]?)(\\D|$)");
    private static final Pattern NON_TOKEN_PATTERN = Pattern.compile("[^a-z0-9]+");
    private static final List<String> DEFAULT_UNKNOWN_RESPONSES = List.of(
            "I do not have enough recovered data for that request.",
            "Archive gap detected. Give me a location, recovered record, or subsystem.",
            "Creator records corrupted. I remember fragments, not certainties."
    );

    private final List<Persona> personas = new ArrayList<>();
    private final List<String> unknownResponses = new ArrayList<>(DEFAULT_UNKNOWN_RESPONSES);
    private boolean enabled;
    private int minimumKeywordMatches = 1;
    private String menuPrompt = "Available fallback prompts:";
    private String unavailableHint = "If you want lightweight replies, say Aether and ask for prompts.";

    public void configure(AIConfig.Fallback config, String defaultPersonaName, String defaultWakeWord) {
        personas.clear();
        enabled = config != null && Boolean.TRUE.equals(config.getEnabled());
        minimumKeywordMatches = 1;
        unknownResponses.clear();
        unknownResponses.addAll(DEFAULT_UNKNOWN_RESPONSES);
        if (config != null && config.getMinimumKeywordMatches() != null) {
            minimumKeywordMatches = Math.max(1, config.getMinimumKeywordMatches());
        }
        if (config != null && !config.getUnknownResponses().isEmpty()) {
            unknownResponses.clear();
            for (String response : config.getUnknownResponses()) {
                if (response != null && !response.isBlank()) {
                    unknownResponses.add(response.trim());
                }
            }
        }
        if (unknownResponses.isEmpty()) {
            unknownResponses.addAll(DEFAULT_UNKNOWN_RESPONSES);
        }
        if (config != null && config.getMenuPrompt() != null && !config.getMenuPrompt().isBlank()) {
            menuPrompt = config.getMenuPrompt().trim();
        }
        if (config != null && config.getUnavailableHint() != null && !config.getUnavailableHint().isBlank()) {
            unavailableHint = config.getUnavailableHint().trim();
        }
        if (config != null) {
            for (AIConfig.FallbackPersona personaConfig : config.getPersonas()) {
                Persona persona = fromConfig(personaConfig);
                if (persona != null) {
                    personas.add(persona);
                }
            }
        }
        if (personas.isEmpty()) {
            personas.addAll(buildDefaultPersonas(defaultPersonaName, defaultWakeWord));
        }
    }

    public Optional<String> findMentionedPersonaName(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String lower = message.toLowerCase(Locale.ROOT);
        Optional<Persona> aether = findAetherPersona();
        if (aether.isPresent() && mentionsPersona(lower, aether.get())) {
            Optional<Persona> routed = resolveSubsystemFromAether(lower, aether.get());
            if (routed.isPresent()) {
                return Optional.of(routed.get().name());
            }
            return Optional.of(aether.get().name());
        }
        return findMentionedPersona(lower).map(Persona::name);
    }

    public Optional<FallbackReply> buildReply(String message) {
        return buildReply(message, ResponseContext.empty());
    }

    public Optional<FallbackReply> buildReply(String message, ResponseContext context) {
        if (!enabled || message == null || message.isBlank()) {
            return Optional.empty();
        }
        ResponseContext safeContext = context == null ? ResponseContext.empty() : context;
        MatchInput input = MatchInput.from(message);
        Optional<Persona> aether = findAetherPersona();

        if (aether.isPresent() && mentionsPersona(input.lower(), aether.get())) {
            return Optional.of(buildAetherReply(input, aether.get(), safeContext));
        }

        Optional<Persona> personaMatch = findMentionedPersona(input.lower());
        if (personaMatch.isPresent()) {
            return Optional.of(buildPersonaReply(input, personaMatch.get(), safeContext));
        }

        Optional<PersonaPromptMatch> routed = findBestPromptAcross(personas, input, safeContext);
        if (routed.isPresent()) {
            return Optional.of(replyFrom(routed.get().persona(), routed.get().match(), input));
        }

        if (aether.isPresent()) {
            return Optional.of(buildUnknownReply(aether.get(), input));
        }
        if (!personas.isEmpty()) {
            return Optional.of(buildUnknownReply(personas.get(0), input));
        }
        return Optional.empty();
    }

    public String appendUnavailableHint(String baseReply) {
        if (!enabled || unavailableHint == null || unavailableHint.isBlank()) {
            return baseReply;
        }
        if (baseReply == null || baseReply.isBlank()) {
            return unavailableHint;
        }
        return baseReply + " " + unavailableHint;
    }

    private FallbackReply buildAetherReply(MatchInput input, Persona aether, ResponseContext context) {
        Optional<Persona> explicitSubsystem = resolveSubsystemFromAether(input.lower(), aether);
        if (shouldShowMenu(input.lower(), aether)) {
            return new FallbackReply(aether.name(), buildAetherSubsystemMenu(aether), true);
        }

        Optional<PromptMatch> direct = aether.bestPrompt(input, context, minimumKeywordMatches);
        if (direct.isPresent()) {
            return replyFrom(aether, direct.get(), input);
        }

        if (explicitSubsystem.isPresent()) {
            return buildPersonaReply(input, explicitSubsystem.get(), context);
        }

        Optional<PersonaPromptMatch> routed = findBestPromptAcross(subsystemPersonas(aether), input, context);
        if (routed.isPresent()) {
            return replyFrom(routed.get().persona(), routed.get().match(), input);
        }

        return buildUnknownReply(aether, input);
    }

    private FallbackReply buildPersonaReply(MatchInput input, Persona persona, ResponseContext context) {
        if (shouldShowMenu(input.lower(), persona)) {
            return new FallbackReply(persona.name(), persona.buildMenu(menuPrompt), true);
        }
        Optional<PromptMatch> match = persona.bestPrompt(input, context, minimumKeywordMatches);
        if (match.isPresent()) {
            return replyFrom(persona, match.get(), input);
        }
        return buildUnknownReply(persona, input);
    }

    private Optional<PersonaPromptMatch> findBestPromptAcross(List<Persona> candidates, MatchInput input, ResponseContext context) {
        PersonaPromptMatch best = null;
        for (Persona persona : candidates) {
            Optional<PromptMatch> match = persona.bestPrompt(input, context, minimumKeywordMatches);
            if (match.isEmpty()) {
                continue;
            }
            if (best == null || match.get().score() > best.match().score()) {
                best = new PersonaPromptMatch(persona, match.get());
            }
        }
        return Optional.ofNullable(best);
    }

    private FallbackReply replyFrom(Persona persona, PromptMatch match, MatchInput input) {
        return new FallbackReply(persona.name(), match.prompt().selectResponse(input), false);
    }

    private FallbackReply buildUnknownReply(Persona persona, MatchInput input) {
        int index = Math.floorMod((input.normalizedText() + "|" + persona.name()).hashCode(), unknownResponses.size());
        return new FallbackReply(persona.name(), unknownResponses.get(index), false);
    }

    private Optional<Persona> resolveSubsystemFromAether(String lower, Persona aether) {
        List<Persona> subsystems = subsystemPersonas(aether);
        for (Persona subsystem : subsystems) {
            if (mentionsPersona(lower, subsystem) || containsToken(lower, subsystem.name().toLowerCase(Locale.ROOT))) {
                return Optional.of(subsystem);
            }
        }
        int selected = extractMenuNumber(lower);
        if (selected > 0 && selected <= subsystems.size()) {
            return Optional.of(subsystems.get(selected - 1));
        }
        return Optional.empty();
    }

    private int extractMenuNumber(String lower) {
        Matcher matcher = NUMBER_PATTERN.matcher(lower);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private List<Persona> subsystemPersonas(Persona aether) {
        List<Persona> subsystems = new ArrayList<>();
        for (Persona persona : personas) {
            if (persona == aether) {
                continue;
            }
            String lowerName = persona.name().toLowerCase(Locale.ROOT);
            if (isSubsystemName(lowerName)) {
                subsystems.add(persona);
            }
        }
        return subsystems;
    }

    private boolean isSubsystemName(String lowerName) {
        return "aegis".equals(lowerName)
                || "eclipse".equals(lowerName)
                || "terra".equals(lowerName)
                || "helios".equals(lowerName)
                || "enforcer".equals(lowerName)
                || "requiem".equals(lowerName);
    }

    private Optional<Persona> findAetherPersona() {
        for (Persona persona : personas) {
            if ("aether".equals(persona.name().toLowerCase(Locale.ROOT))) {
                return Optional.of(persona);
            }
        }
        return Optional.empty();
    }

    private String buildAetherSubsystemMenu(Persona aether) {
        StringBuilder builder = new StringBuilder();
        if (aether.introduction() != null && !aether.introduction().isBlank()) {
            builder.append(aether.introduction().trim()).append(' ');
        }
        builder.append(menuPrompt == null || menuPrompt.isBlank() ? "Available subsystem prompts:" : menuPrompt.trim());
        List<Persona> subsystems = subsystemPersonas(aether);
        for (int i = 0; i < subsystems.size(); i++) {
            Persona subsystem = subsystems.get(i);
            builder.append(' ').append(i + 1).append(") ").append(subsystem.name());
            if (subsystem.introduction() != null && !subsystem.introduction().isBlank()) {
                builder.append(" - ").append(subsystem.introduction().trim());
            }
        }
        return builder.toString().trim();
    }

    private boolean shouldShowMenu(String lower, Persona persona) {
        if (lower == null || lower.isBlank()) {
            return true;
        }
        for (String alias : persona.aliases()) {
            String normalized = alias.toLowerCase(Locale.ROOT);
            if (lower.equals(normalized)
                    || lower.equals(normalized + "?")
                    || lower.equals("hey " + normalized)
                    || lower.equals("hi " + normalized)) {
                return true;
            }
        }
        return lower.contains("prompt")
                || lower.contains("option")
                || lower.contains("menu")
                || lower.contains("what can you do")
                || lower.contains("what do you do")
                || lower.contains("list")
                || lower.contains("subsystem");
    }

    private Optional<Persona> findMentionedPersona(String lower) {
        for (Persona persona : personas) {
            if (mentionsPersona(lower, persona)) {
                return Optional.of(persona);
            }
        }
        return Optional.empty();
    }

    private boolean mentionsPersona(String lower, Persona persona) {
        for (String alias : persona.aliases()) {
            String normalized = alias.toLowerCase(Locale.ROOT);
            if (containsToken(lower, normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsToken(String haystack, String needle) {
        if (needle == null || needle.isBlank()) {
            return false;
        }
        if (haystack.equals(needle)) {
            return true;
        }
        return haystack.matches(".*(^|[^a-z0-9])" + Pattern.quote(needle) + "([^a-z0-9]|$).*");
    }

    private Persona fromConfig(AIConfig.FallbackPersona personaConfig) {
        if (personaConfig == null || personaConfig.getName() == null || personaConfig.getName().isBlank()) {
            return null;
        }
        List<String> aliases = new ArrayList<>();
        aliases.add(personaConfig.getName().trim());
        for (String alias : personaConfig.getAliases()) {
            if (alias != null && !alias.isBlank()) {
                aliases.add(alias.trim());
            }
        }
        List<Prompt> prompts = new ArrayList<>();
        for (AIConfig.FallbackPrompt promptConfig : personaConfig.getPrompts()) {
            if (promptConfig == null || promptConfig.getLabel() == null || promptConfig.getLabel().isBlank()) {
                continue;
            }
            String response = promptConfig.getResponse() == null ? "" : promptConfig.getResponse().trim();
            List<String> responses = cleanList(promptConfig.getResponses());
            List<String> triggers = new ArrayList<>();
            triggers.add(promptConfig.getLabel().trim());
            triggers.addAll(cleanList(promptConfig.getTriggers()));
            List<String> keywords = cleanList(promptConfig.getKeywords());
            if (keywords.isEmpty()) {
                keywords.addAll(cleanList(promptConfig.getTriggers()));
            }
            int promptMinimum = promptConfig.getMinimumMatches() == null ? 0 : Math.max(1, promptConfig.getMinimumMatches());
            if (!response.isBlank() || !responses.isEmpty()) {
                prompts.add(new Prompt(
                        promptConfig.getLabel().trim(),
                        promptConfig.getIntent() == null ? "" : promptConfig.getIntent().trim(),
                        response,
                        responses,
                        triggers,
                        keywords,
                        promptMinimum,
                        cleanList(promptConfig.getRequiredContext()),
                        cleanList(promptConfig.getBlockedContext())));
            }
        }
        if (prompts.isEmpty()) {
            return null;
        }
        String introduction = personaConfig.getIntroduction() == null ? "" : personaConfig.getIntroduction().trim();
        return new Persona(personaConfig.getName().trim(), introduction, aliases, prompts);
    }

    private List<String> cleanList(List<String> values) {
        List<String> clean = new ArrayList<>();
        if (values == null) {
            return clean;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                clean.add(value.trim());
            }
        }
        return clean;
    }

    private List<Persona> buildDefaultPersonas(String defaultPersonaName, String defaultWakeWord) {
        String name = (defaultPersonaName == null || defaultPersonaName.isBlank()) ? "Aether" : defaultPersonaName.trim();
        String wakeWord = (defaultWakeWord == null || defaultWakeWord.isBlank()) ? name : defaultWakeWord.trim();
        List<Persona> defaults = new ArrayList<>();
        defaults.add(new Persona(
                name,
                "I route recovered intents through A.E.T.H.E.R subsystems.",
                List.of(name, wakeWord, "aether core"),
                List.of(prompt("Subsystem menu",
                        "Select a subsystem: Aegis, Eclipse, Terra, Helios, Enforcer, or Requiem.",
                        List.of("subsystem", "menu", "prompts"),
                        List.of("subsystem", "menu", "prompt")))));
        defaults.add(new Persona("Aegis", "Health / Protection", List.of("aegis"),
                List.of(prompt("Safety guidance",
                        "Aegis: prioritize shelter safety checks, hazard prevention reminders, and defensive readiness before moving out.",
                        List.of("safety", "health", "protection", "hazard prevention"),
                        List.of("safety", "health", "protection", "hazard")))));
        defaults.add(new Persona("Eclipse", "Rift / Anomaly", List.of("eclipse"),
                List.of(prompt("Anomaly scan",
                        "Eclipse: anomaly levels are unstable. Track rift signatures, classify risk, and follow safe response protocols.",
                        List.of("rift", "anomaly", "risk", "scan"),
                        List.of("rift", "anomaly", "risk", "scan")))));
        defaults.add(new Persona("Terra", "Terrain / Restoration / Exploration", List.of("terra"),
                List.of(prompt("Exploration routing",
                        "Terra: use terrain-aware routing, avoid collapse zones, and prioritize restoration-ready pathways.",
                        List.of("terrain", "exploration", "route", "restoration"),
                        List.of("terrain", "exploration", "route", "restoration")))));
        defaults.add(new Persona("Helios", "Energy / Machines / Atmospheric Stability", List.of("helios"),
                List.of(prompt("Power and atmosphere",
                        "Helios: stabilize energy demand, balance machine load, and monitor atmospheric conditions before expansion.",
                        List.of("energy", "machines", "power", "atmosphere"),
                        List.of("energy", "machine", "power", "atmosphere")))));
        defaults.add(new Persona("Enforcer", "Combat / Security", List.of("enforcer"),
                List.of(prompt("Security posture",
                        "Enforcer: set combat readiness, prioritize threats, and reinforce perimeter security before engagement.",
                        List.of("combat", "security", "threat", "defense"),
                        List.of("combat", "security", "threat", "defense")))));
        defaults.add(new Persona("Requiem", "Archive / Memory / History", List.of("requiem"),
                List.of(prompt("Archive continuity",
                        "Requiem: maintain lore continuity, verify historical records, and preserve mission memory for future expeditions.",
                        List.of("archive", "memory", "history", "lore"),
                        List.of("archive", "memory", "history", "lore")))));
        return defaults;
    }

    private Prompt prompt(String label, String response, List<String> triggers, List<String> keywords) {
        return new Prompt(label, "", response, List.of(), triggers, keywords, 0, List.of(), List.of());
    }

    public record FallbackReply(String speaker, String text, boolean menu) {
    }

    public record ResponseContext(Set<String> tags) {
        public ResponseContext {
            Set<String> normalized = new LinkedHashSet<>();
            if (tags != null) {
                for (String tag : tags) {
                    if (tag != null && !tag.isBlank()) {
                        normalized.add(tag.trim().toLowerCase(Locale.ROOT));
                    }
                }
            }
            tags = Set.copyOf(normalized);
        }

        public static ResponseContext empty() {
            return new ResponseContext(Set.of());
        }

        public boolean has(String tag) {
            return tag != null && tags.contains(tag.trim().toLowerCase(Locale.ROOT));
        }

        public String describe() {
            return String.join(", ", tags);
        }
    }

    private record Persona(String name, String introduction, List<String> aliases, List<Prompt> prompts) {
        private Persona {
            aliases = List.copyOf(aliases);
            prompts = List.copyOf(prompts);
        }

        private Optional<PromptMatch> bestPrompt(MatchInput input, ResponseContext context, int defaultMinimumMatches) {
            PromptMatch best = null;
            for (Prompt prompt : prompts) {
                Optional<PromptMatch> match = prompt.match(input, context, defaultMinimumMatches);
                if (match.isEmpty()) {
                    continue;
                }
                if (best == null || match.get().score() > best.score()) {
                    best = match.get();
                }
            }
            return Optional.ofNullable(best);
        }

        private String buildMenu(String menuPrompt) {
            StringBuilder builder = new StringBuilder();
            if (introduction != null && !introduction.isBlank()) {
                builder.append(introduction.trim()).append(' ');
            }
            builder.append(menuPrompt == null || menuPrompt.isBlank() ? "Available prompts:" : menuPrompt.trim());
            for (int i = 0; i < prompts.size(); i++) {
                builder.append(' ')
                        .append(i + 1)
                        .append(") ")
                        .append(prompts.get(i).label());
            }
            return builder.toString().trim();
        }
    }

    private record Prompt(String label,
                          String intent,
                          String response,
                          List<String> responses,
                          List<String> triggers,
                          List<String> keywords,
                          int minimumMatches,
                          List<String> requiredContext,
                          List<String> blockedContext) {
        private Prompt {
            responses = List.copyOf(responses);
            triggers = List.copyOf(triggers);
            keywords = List.copyOf(keywords);
            requiredContext = List.copyOf(requiredContext);
            blockedContext = List.copyOf(blockedContext);
        }

        private Optional<PromptMatch> match(MatchInput input, ResponseContext context, int defaultMinimumMatches) {
            if (!contextMatches(context)) {
                return Optional.empty();
            }
            Set<String> matchedTerms = new LinkedHashSet<>();
            int score = 0;
            for (String trigger : triggers) {
                int termScore = input.scoreTerm(trigger, true);
                if (termScore > 0 && matchedTerms.add(MatchInput.normalize(trigger))) {
                    score += termScore;
                }
            }
            for (String keyword : keywords) {
                int termScore = input.scoreTerm(keyword, false);
                if (termScore > 0 && matchedTerms.add(MatchInput.normalize(keyword))) {
                    score += termScore;
                }
            }

            int threshold = minimumMatches > 0 ? minimumMatches : Math.max(1, defaultMinimumMatches);
            if (score < threshold) {
                return Optional.empty();
            }
            return Optional.of(new PromptMatch(this, score));
        }

        private boolean contextMatches(ResponseContext context) {
            for (String required : requiredContext) {
                if (!context.has(required)) {
                    return false;
                }
            }
            for (String blocked : blockedContext) {
                if (context.has(blocked)) {
                    return false;
                }
            }
            return true;
        }

        private String selectResponse(MatchInput input) {
            List<String> options = new ArrayList<>();
            if (response != null && !response.isBlank()) {
                options.add(response.trim());
            }
            for (String option : responses) {
                if (option != null && !option.isBlank()) {
                    options.add(option.trim());
                }
            }
            if (options.isEmpty()) {
                return "I do not have enough recovered data for that request.";
            }
            int index = Math.floorMod((input.normalizedText() + "|" + intent + "|" + label).hashCode(), options.size());
            return options.get(index);
        }
    }

    private record PromptMatch(Prompt prompt, int score) {
    }

    private record PersonaPromptMatch(Persona persona, PromptMatch match) {
    }

    private record MatchInput(String original, String lower, String normalizedText, Set<String> tokens) {
        private static MatchInput from(String message) {
            String safeOriginal = message == null ? "" : message.trim();
            String lower = safeOriginal.toLowerCase(Locale.ROOT);
            String normalized = normalize(safeOriginal);
            return new MatchInput(safeOriginal, lower, normalized, tokenize(normalized));
        }

        private int scoreTerm(String term, boolean phraseBoost) {
            String normalized = normalize(term);
            if (normalized.isBlank()) {
                return 0;
            }
            if (normalized.contains(" ")) {
                if (containsPhrase(normalized)) {
                    return phraseBoost ? Math.max(2, normalized.split(" ").length) : 1;
                }
                return 0;
            }
            return tokens.contains(simplifyToken(normalized)) ? 1 : 0;
        }

        private boolean containsPhrase(String phrase) {
            if (normalizedText.equals(phrase)) {
                return true;
            }
            return (" " + normalizedText + " ").contains(" " + phrase + " ");
        }

        private static String normalize(String value) {
            if (value == null || value.isBlank()) {
                return "";
            }
            String normalized = NON_TOKEN_PATTERN.matcher(value.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
            return normalized.replaceAll("\\s+", " ");
        }

        private static Set<String> tokenize(String normalized) {
            Set<String> result = new LinkedHashSet<>();
            if (normalized == null || normalized.isBlank()) {
                return result;
            }
            for (String token : normalized.split(" ")) {
                if (!token.isBlank()) {
                    result.add(token);
                    result.add(simplifyToken(token));
                }
            }
            return result;
        }

        private static String simplifyToken(String token) {
            if (token == null) {
                return "";
            }
            String simplified = token.toLowerCase(Locale.ROOT);
            if (simplified.length() > 5 && simplified.endsWith("ing")) {
                return simplified.substring(0, simplified.length() - 3);
            }
            if (simplified.length() > 4 && simplified.endsWith("ed")) {
                return simplified.substring(0, simplified.length() - 2);
            }
            if (simplified.length() > 4 && simplified.endsWith("es")) {
                return simplified.substring(0, simplified.length() - 2);
            }
            if (simplified.length() > 3 && simplified.endsWith("s")) {
                return simplified.substring(0, simplified.length() - 1);
            }
            return simplified;
        }
    }
}
