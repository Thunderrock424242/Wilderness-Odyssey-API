package com.thunder.wildernessodysseyapi.analytics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores which player UUIDs can access analytics commands rendered in-game.
 */
public class AnalyticsAccessSettings {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private List<String> allowedPlayerUuids = new ArrayList<>();

    public static AnalyticsAccessSettings load(Path file) {
        if (Files.exists(file)) {
            try {
                AnalyticsAccessSettings settings = GSON.fromJson(Files.readString(file), AnalyticsAccessSettings.class);
                if (settings != null) {
                    return settings;
                }
            } catch (IOException ignored) {
            }
        }
        return new AnalyticsAccessSettings();
    }

    public void save(Path file) {
        try {
            Files.createDirectories(Objects.requireNonNull(file.getParent()));
            Files.writeString(file, Objects.requireNonNull(GSON.toJson(this)));
        } catch (IOException ignored) {
        }
    }

    public boolean isAllowed(UUID uuid) {
        return uuid != null && allowedPlayerUuids.stream()
                .anyMatch(raw -> raw.equalsIgnoreCase(uuid.toString()));
    }

    public List<String> allowedPlayerUuids() {
        return allowedPlayerUuids;
    }
}
