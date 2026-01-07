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
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

public final class AIConfigLoader {

    private static final String CONFIG_NAME = "ai_config.yaml";
    private static final Yaml YAML = new Yaml();

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
        Object parsed;
        try {
            parsed = YAML.load(content);
        } catch (YAMLException e) {
            ModConstants.LOGGER.warn("Failed to parse AI config YAML.", e);
            return config;
        }
        if (!(parsed instanceof Map<?, ?> root)) {
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
        config.getSettings().setVoiceEnabled(readBoolean(settings.get("voice_enabled")));
        config.getSettings().setSpeechRecognition(readBoolean(settings.get("speech_recognition")));
        config.getSettings().setWakeWord(readStringValue(settings.get("wake_word")));
        config.getSettings().setModel(readStringValue(settings.get("model")));

        Map<String, Object> localModel = readStringObjectMap(root.get("local_model"));
        config.getLocalModel().setEnabled(readBoolean(localModel.get("enabled")));
        config.getLocalModel().setBaseUrl(readStringValue(localModel.get("base_url")));
        config.getLocalModel().setModel(readStringValue(localModel.get("model")));
        config.getLocalModel().setSystemPrompt(readStringValue(localModel.get("system_prompt")));
        config.getLocalModel().setTimeoutSeconds(readInteger(localModel.get("timeout_seconds")));

        return config;
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
