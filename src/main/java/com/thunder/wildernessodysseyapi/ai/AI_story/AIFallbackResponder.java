package com.thunder.wildernessodysseyapi.ai.AI_story;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic fallback menu that can answer a small set of prompt-driven
 * requests when the local model is disabled, unavailable, or intentionally not used.
 */
public class AIFallbackResponder {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(^|\\D)([1-9][0-9]?)(\\D|$)");

    private final List<Persona> personas = new ArrayList<>();
    private boolean enabled;
    private String menuPrompt = "Available fallback prompts:";
    private String unavailableHint = "If you want lightweight replies, say Aether and ask for prompts.";

    public void configure(AIConfig.Fallback config, String defaultPersonaName, String defaultWakeWord) {
        personas.clear();
        enabled = config != null && Boolean.TRUE.equals(config.getEnabled());
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
        if (!enabled || message == null || message.isBlank()) {
            return Optional.empty();
        }
        String lower = message.toLowerCase(Locale.ROOT);
        Optional<Persona> aether = findAetherPersona();
        if (aether.isPresent() && mentionsPersona(lower, aether.get())) {
            Optional<Persona> subsystem = resolveSubsystemFromAether(lower, aether.get());
            if (subsystem.isPresent()) {
                return buildPersonaReply(lower, subsystem.get());
            }
            return Optional.of(new FallbackReply(aether.get().name(), buildAetherSubsystemMenu(aether.get()), true));
        }

        Optional<Persona> personaMatch = findMentionedPersona(lower);
        if (personaMatch.isEmpty()) {
            return Optional.empty();
        }
        return buildPersonaReply(lower, personaMatch.get());
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

    private Optional<FallbackReply> buildPersonaReply(String lower, Persona persona) {
        if (shouldShowMenu(lower, persona)) {
            return Optional.of(new FallbackReply(persona.name(), persona.buildMenu(menuPrompt), true));
        }
        for (Prompt prompt : persona.prompts()) {
            if (prompt.matches(lower)) {
                return Optional.of(new FallbackReply(persona.name(), prompt.response(), false));
            }
        }
        return Optional.of(new FallbackReply(persona.name(), persona.buildMenu(menuPrompt), true));
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
                || lower.contains("help")
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
            List<String> triggers = new ArrayList<>();
            triggers.add(promptConfig.getLabel().trim());
            for (String trigger : promptConfig.getTriggers()) {
                if (trigger != null && !trigger.isBlank()) {
                    triggers.add(trigger.trim());
                }
            }
            prompts.add(new Prompt(promptConfig.getLabel().trim(), response, triggers));
        }
        if (prompts.isEmpty()) {
            return null;
        }
        String introduction = personaConfig.getIntroduction() == null ? "" : personaConfig.getIntroduction().trim();
        return new Persona(personaConfig.getName().trim(), introduction, aliases, prompts);
    }

    private List<Persona> buildDefaultPersonas(String defaultPersonaName, String defaultWakeWord) {
        String name = (defaultPersonaName == null || defaultPersonaName.isBlank()) ? "Aether" : defaultPersonaName.trim();
        String wakeWord = (defaultWakeWord == null || defaultWakeWord.isBlank()) ? name : defaultWakeWord.trim();
        List<Persona> defaults = new ArrayList<>();
        defaults.add(new Persona(
                name,
                "I route requests to A.E.T.H.E.R subsystems for lightweight responses.",
                List.of(name, wakeWord, "aether core"),
                List.of(new Prompt("Subsystem menu", "Select a subsystem: Aegis, Eclipse, Terra, Helios, Enforcer, or Requiem.",
                        List.of("subsystem", "menu", "prompts")))));
        defaults.add(new Persona("Aegis", "Health / Protection", List.of("aegis"),
                List.of(new Prompt("Safety guidance",
                        "Aegis: prioritize shelter safety checks, hazard prevention reminders, and defensive readiness before moving out.",
                        List.of("safety", "health", "protection", "hazard prevention")))));
        defaults.add(new Persona("Eclipse", "Rift / Anomaly", List.of("eclipse"),
                List.of(new Prompt("Anomaly scan",
                        "Eclipse: anomaly levels are unstable. Track rift signatures, classify risk, and follow safe response protocols.",
                        List.of("rift", "anomaly", "risk", "scan")))));
        defaults.add(new Persona("Terra", "Terrain / Restoration / Exploration", List.of("terra"),
                List.of(new Prompt("Exploration routing",
                        "Terra: use terrain-aware routing, avoid collapse zones, and prioritize restoration-ready pathways.",
                        List.of("terrain", "exploration", "route", "restoration")))));
        defaults.add(new Persona("Helios", "Energy / Machines / Atmospheric Stability", List.of("helios"),
                List.of(new Prompt("Power and atmosphere",
                        "Helios: stabilize energy demand, balance machine load, and monitor atmospheric conditions before expansion.",
                        List.of("energy", "machines", "power", "atmosphere")))));
        defaults.add(new Persona("Enforcer", "Combat / Security", List.of("enforcer"),
                List.of(new Prompt("Security posture",
                        "Enforcer: set combat readiness, prioritize threats, and reinforce perimeter security before engagement.",
                        List.of("combat", "security", "threat", "defense")))));
        defaults.add(new Persona("Requiem", "Archive / Memory / History", List.of("requiem"),
                List.of(new Prompt("Archive continuity",
                        "Requiem: maintain lore continuity, verify historical records, and preserve mission memory for future expeditions.",
                        List.of("archive", "memory", "history", "lore")))));
        return defaults;
    }

    public record FallbackReply(String speaker, String text, boolean menu) {
    }

    private record Persona(String name, String introduction, List<String> aliases, List<Prompt> prompts) {
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

    private record Prompt(String label, String response, List<String> triggers) {
        private boolean matches(String lower) {
            if (lower == null || lower.isBlank()) {
                return false;
            }
            for (String trigger : triggers) {
                if (trigger == null || trigger.isBlank()) {
                    continue;
                }
                String normalized = trigger.toLowerCase(Locale.ROOT);
                if (lower.contains(normalized)) {
                    return true;
                }
            }
            return false;
        }
    }
}
