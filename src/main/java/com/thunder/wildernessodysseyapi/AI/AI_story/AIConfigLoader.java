package com.thunder.wildernessodysseyapi.AI.AI_story;

import com.thunder.wildernessodysseyapi.Core.ModConstants;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.neoforged.fml.loading.FMLPaths;
public final class AIConfigLoader {

    private static final String CONFIG_NAME = "ai_config.yaml";

    private AIConfigLoader() {
    }

    public static AIConfig load() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(CONFIG_NAME);
        ensureDefaultConfig(configPath);
        String content = readConfig(configPath);
        if (content == null || content.isBlank()) {
            content = readBundledConfig();
        }
        return parse(content);
    }

    private static void ensureDefaultConfig(Path configPath) {
        if (Files.exists(configPath)) {
            return;
        }
        try {
            Files.createDirectories(configPath.getParent());
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed to create config directory for AI config.", e);
            return;
        }
        try (InputStream in = AIConfigLoader.class.getClassLoader().getResourceAsStream(CONFIG_NAME)) {
            if (in == null) {
                return;
            }
            Files.copy(in, configPath);
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed to seed default AI config at {}.", configPath, e);
        }
    }

    private static String readConfig(Path configPath) {
        if (!Files.exists(configPath)) {
            return null;
        }
        try {
            return Files.readString(configPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed to read AI config at {}.", configPath, e);
            return null;
        }
    }

    private static String readBundledConfig() {
        try (InputStream in = AIConfigLoader.class.getClassLoader().getResourceAsStream(CONFIG_NAME)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed to read bundled AI config.", e);
            return null;
        }
    }

    private static AIConfig parse(String content) {
        AIConfig config = new AIConfig();
        if (content == null || content.isBlank()) {
            return config;
        }
        Map<?, ?> root = parseWithSnakeYaml(content);
        if (root == null) {
            root = parseSimpleYaml(content);
        }
        if (root == null) {
            return config;
        }

        config.getStory().addAll(readStringList(root.get("story")));
        config.getBackgroundHistory().addAll(readStringList(root.get("background_history")));
        config.getCorruptedData().addAll(readStringList(root.get("corrupted_data")));
        config.setCorruptedPrefix(readStringValue(root.get("corrupted_prefix")));
        Map<String, Object> personality = readStringObjectMap(root.get("personality"));
        config.getPersonality().setName(readStringValue(personality.get("name")));
        config.getPersonality().setTone(readStringValue(personality.get("tone")));
        config.getPersonality().setEmpathy(readStringValue(personality.get("empathy")));

        Map<String, Object> settings = readStringObjectMap(root.get("settings"));
        config.getSettings().setAtlasEnabled(readBoolean(settings.get("atlas_enabled")));
        config.getSettings().setVoiceEnabled(readBoolean(settings.get("voice_enabled")));
        config.getSettings().setSpeechRecognition(readBoolean(settings.get("speech_recognition")));
        config.getSettings().setWakeWord(readStringValue(settings.get("wake_word")));
        config.getSettings().setModel(readStringValue(settings.get("model")));

        Map<String, Object> localModel = readStringObjectMap(root.get("local_model"));
        config.getLocalModel().setEnabled(readBoolean(localModel.get("enabled")));
        config.getLocalModel().setAutoStart(readBoolean(localModel.get("auto_start")));
        config.getLocalModel().setBaseUrl(readStringValue(localModel.get("base_url")));
        config.getLocalModel().setModel(readStringValue(localModel.get("model")));
        config.getLocalModel().setSystemPrompt(readStringValue(localModel.get("system_prompt")));
        config.getLocalModel().setStartCommand(readStringValue(localModel.get("start_command")));
        config.getLocalModel().setBundledServerResource(readStringValue(localModel.get("bundled_server_resource")));
        config.getLocalModel().setBundledServerArgs(readStringValue(localModel.get("bundled_server_args")));
        config.getLocalModel().setTimeoutSeconds(readInteger(localModel.get("timeout_seconds")));

        return config;
    }

    private static Map<?, ?> parseWithSnakeYaml(String content) {
        try {
            Class<?> yamlClass = Class.forName("org.yaml.snakeyaml.Yaml");
            Object yaml = yamlClass.getDeclaredConstructor().newInstance();
            Object parsed = yamlClass.getMethod("load", String.class).invoke(yaml, content);
            if (parsed instanceof Map<?, ?> map) {
                return map;
            }
            return null;
        } catch (ClassNotFoundException e) {
            ModConstants.LOGGER.warn("SnakeYAML not found on the classpath; falling back to a minimal parser.");
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            ModConstants.LOGGER.warn("Failed to parse AI config YAML with SnakeYAML.", e);
            return null;
        }
    }

    private static Map<String, Object> parseSimpleYaml(String content) {
        Map<String, Object> root = new LinkedHashMap<>();
        String currentKey = null;
        for (String line : content.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int indent = countIndent(line);
            if (indent == 0) {
                int colonIndex = trimmed.indexOf(':');
                if (colonIndex < 0) {
                    continue;
                }
                currentKey = trimmed.substring(0, colonIndex).trim();
                String valuePart = trimmed.substring(colonIndex + 1).trim();
                if (!valuePart.isEmpty()) {
                    root.put(currentKey, stripQuotes(valuePart));
                }
            } else if (currentKey != null) {
                if (trimmed.startsWith("- ")) {
                    Object existing = root.get(currentKey);
                    List<String> list = existing instanceof List<?> ? castStringList(existing) : new ArrayList<>();
                    list.add(stripQuotes(trimmed.substring(2).trim()));
                    root.put(currentKey, list);
                } else {
                    int colonIndex = trimmed.indexOf(':');
                    if (colonIndex < 0) {
                        continue;
                    }
                    Object existing = root.get(currentKey);
                    Map<String, Object> map = existing instanceof Map<?, ?> ? castStringMap(existing) : new LinkedHashMap<>();
                    String key = trimmed.substring(0, colonIndex).trim();
                    String valuePart = trimmed.substring(colonIndex + 1).trim();
                    map.put(key, valuePart.isEmpty() ? "" : stripQuotes(valuePart));
                    root.put(currentKey, map);
                }
            }
        }
        return root;
    }

    private static int countIndent(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
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

    private static List<String> castStringList(Object value) {
        List<String> results = new ArrayList<>();
        if (value instanceof Iterable<?> items) {
            for (Object entry : items) {
                if (entry != null) {
                    results.add(entry.toString());
                }
            }
        }
        return results;
    }

    private static Map<String, Object> castStringMap(Object value) {
        Map<String, Object> results = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    results.put(entry.getKey().toString(), entry.getValue());
                }
            }
        }
        return results;
    }

    private static List<String> readStringList(Object value) {
        if (!(value instanceof Iterable<?> items)) {
            return List.of();
        }
        List<String> results = new ArrayList<>();
        for (Object entry : items) {
            if (entry != null) {
                String text = entry.toString().trim();
                if (!text.isEmpty()) {
                    results.add(text);
                }
            }
        }
        return results;
    }

    private static Map<String, Object> readStringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> results = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            results.put(entry.getKey().toString(), entry.getValue());
        }
        return results;
    }

    private static String readStringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static Boolean readBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        String text = value.toString().trim().toLowerCase();
        if (text.isEmpty()) {
            return null;
        }
        if ("true".equals(text) || "yes".equals(text) || "1".equals(text)) {
            return true;
        }
        if ("false".equals(text) || "no".equals(text) || "0".equals(text)) {
            return false;
        }
        return null;
    }

    private static Integer readInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
