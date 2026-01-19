package com.thunder.wildernessodysseyapi.AI.AI_story;

import com.thunder.wildernessodysseyapi.Core.ModConstants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Persists learned player facts to a lightweight YAML config.
 */
public class AIKnowledgeStore {

    private static final String CONFIG_NAME = "ai_learning.yaml";
    private static final String ROOT_KEY = "learned_facts";

    private final Path configPath;
    private final Set<String> learnedFacts = new LinkedHashSet<>();

    public AIKnowledgeStore() {
        this.configPath = FMLPaths.CONFIGDIR.get().resolve(CONFIG_NAME);
        load();
    }

    public synchronized List<String> getLearnedFacts() {
        return List.copyOf(learnedFacts);
    }

    public synchronized String getContextSnippet() {
        if (learnedFacts.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Atlas learned:");
        int count = 0;
        for (String fact : learnedFacts) {
            if (count >= 5) {
                break;
            }
            builder.append("\n- ").append(fact);
            count++;
        }
        return builder.toString();
    }

    public String extractLearnedFact(String message) {
        if (message == null) {
            return null;
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String fact = extractPrefix(lower, trimmed, "atlas remember ");
        if (fact != null) {
            return fact;
        }
        fact = extractPrefix(lower, trimmed, "atlas learn ");
        if (fact != null) {
            return fact;
        }
        fact = extractPrefix(lower, trimmed, "remember:");
        if (fact != null) {
            return fact;
        }
        fact = extractPrefix(lower, trimmed, "learn:");
        if (fact != null) {
            return fact;
        }
        fact = extractPrefix(lower, trimmed, "remember that ");
        if (fact != null) {
            return fact;
        }
        fact = extractPrefix(lower, trimmed, "learn that ");
        return fact;
    }

    public synchronized boolean addFact(String fact) {
        if (fact == null) {
            return false;
        }
        String cleaned = fact.trim();
        if (cleaned.isEmpty()) {
            return false;
        }
        boolean added = learnedFacts.add(cleaned);
        if (added) {
            save();
        }
        return added;
    }

    private void load() {
        if (!Files.exists(configPath)) {
            writeDefaultFile();
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(configPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed to read AI learning config at {}.", configPath, e);
            return;
        }
        boolean inList = false;
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (!line.startsWith(" ")) {
                if (trimmed.startsWith(ROOT_KEY + ":")) {
                    inList = true;
                } else {
                    inList = false;
                }
                continue;
            }
            if (inList && trimmed.startsWith("- ")) {
                String value = stripQuotes(trimmed.substring(2).trim());
                if (!value.isEmpty()) {
                    learnedFacts.add(value);
                }
            }
        }
    }

    private void writeDefaultFile() {
        try {
            Files.createDirectories(configPath.getParent());
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed to create config directory for AI learning.", e);
            return;
        }
        save();
    }

    private synchronized void save() {
        List<String> lines = new ArrayList<>();
        lines.add("# Learned Atlas memories saved from player conversations.");
        lines.add(ROOT_KEY + ":");
        for (String fact : learnedFacts) {
            lines.add("  - \"" + escapeYaml(fact) + "\"");
        }
        try {
            Files.writeString(configPath, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed to persist AI learning config at {}.", configPath, e);
        }
    }

    private static String extractPrefix(String lower, String original, String prefix) {
        if (!lower.startsWith(prefix)) {
            return null;
        }
        String fact = original.substring(prefix.length()).trim();
        return fact.isEmpty() ? null : fact;
    }

    private static String stripQuotes(String value) {
        if (value.length() < 2) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String escapeYaml(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
