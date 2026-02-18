package com.thunder.wildernessodysseyapi.ai.AI_story;

import com.thunder.wildernessodysseyapi.core.ModConstants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Persists onboarding progress per player so the AI can switch to chat mode.
 */
public class AIOnboardingStore {

    private static final String CONFIG_NAME = "ai_onboarding.yaml";
    private static final String ROOT_KEY = "players";

    private final Path configPath;
    private final Map<UUID, Integer> playerSteps = new HashMap<>();

    public AIOnboardingStore() {
        this.configPath = FMLPaths.CONFIGDIR.get().resolve(CONFIG_NAME);
        load();
    }

    public synchronized int getStep(UUID playerId) {
        return playerSteps.getOrDefault(playerId, 0);
    }

    public synchronized void setStep(UUID playerId, int step) {
        if (playerId == null) {
            return;
        }
        if (step <= 0) {
            playerSteps.remove(playerId);
        } else {
            playerSteps.put(playerId, step);
        }
        save();
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
            ModConstants.LOGGER.warn("Failed to read AI onboarding config at {}.", configPath, e);
            return;
        }
        boolean inList = false;
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (!line.startsWith(" ")) {
                inList = trimmed.startsWith(ROOT_KEY + ":");
                continue;
            }
            if (!inList) {
                continue;
            }
            String[] parts = trimmed.split(":", 2);
            if (parts.length < 2) {
                continue;
            }
            try {
                UUID playerId = UUID.fromString(parts[0].trim());
                int step = Integer.parseInt(parts[1].trim());
                if (step > 0) {
                    playerSteps.put(playerId, step);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed entries.
            }
        }
    }

    private void writeDefaultFile() {
        try {
            Files.createDirectories(configPath.getParent());
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed to create config directory for AI onboarding.", e);
            return;
        }
        save();
    }

    private synchronized void save() {
        StringBuilder builder = new StringBuilder();
        builder.append("# Tracks Atlas onboarding progress per player UUID.\n");
        builder.append(ROOT_KEY).append(":\n");
        for (Map.Entry<UUID, Integer> entry : playerSteps.entrySet()) {
            builder.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        try {
            Files.writeString(configPath, builder.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed to persist AI onboarding config at {}.", configPath, e);
        }
    }
}
