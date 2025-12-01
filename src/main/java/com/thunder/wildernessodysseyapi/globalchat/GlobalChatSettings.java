package com.thunder.wildernessodysseyapi.globalchat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Simple persistence helper for global chat state.
 */
public class GlobalChatSettings {

    public static final String DEFAULT_RELAY_HOST = "198.51.100.77";
    public static final int DEFAULT_RELAY_PORT = 39876;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String host = DEFAULT_RELAY_HOST;
    private int port = DEFAULT_RELAY_PORT;
    private boolean enabled = true;
    private List<String> downtimeHistory = new ArrayList<>();
    private boolean allowServerAutostart = false;
    private String moderationToken = "";
    private String clusterToken = "";

    public static GlobalChatSettings load(Path file) {
        if (Files.exists(file)) {
            try {
                String raw = Files.readString(file);
                GlobalChatSettings settings = GSON.fromJson(raw, GlobalChatSettings.class);
                if (settings != null) {
                    settings.applyDefaultRelayIfUnset();
                    return settings;
                }
            } catch (IOException ignored) {
                // If parsing fails we fall back to defaults.
            }
        }
        return new GlobalChatSettings();
    }

    public void save(Path file) {
        try {
            Files.createDirectories(Objects.requireNonNull(file.getParent()));
            Files.writeString(file, Objects.requireNonNull(GSON.toJson(this)));
        } catch (IOException ignored) {
            // Configuration saves are best-effort; failures will be logged by callers.
        }
    }

    public void recordDowntime(String reason) {
        downtimeHistory.add(DateTimeFormatter.ISO_INSTANT.format(Instant.now()) + " - " + reason);
        // Keep the last 10 entries to avoid unbounded growth.
        if (downtimeHistory.size() > 10) {
            downtimeHistory = new ArrayList<>(downtimeHistory.subList(downtimeHistory.size() - 10, downtimeHistory.size()));
        }
    }

    public String host() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int port() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> downtimeHistory() {
        return downtimeHistory;
    }

    public boolean allowServerAutostart() {
        return allowServerAutostart;
    }

    public void setAllowServerAutostart(boolean allowServerAutostart) {
        this.allowServerAutostart = allowServerAutostart;
    }

    public String moderationToken() {
        return moderationToken;
    }

    public void setModerationToken(String moderationToken) {
        this.moderationToken = moderationToken == null ? "" : moderationToken;
    }

    public String clusterToken() {
        return clusterToken;
    }

    public void setClusterToken(String clusterToken) {
        this.clusterToken = clusterToken == null ? "" : clusterToken;
    }

    public void applyDefaultRelayIfUnset() {
        if (host == null || host.isEmpty()) {
            host = DEFAULT_RELAY_HOST;
        }
        if (port <= 0) {
            port = DEFAULT_RELAY_PORT;
        }
    }

    public void anchorToDefaultRelay() {
        host = DEFAULT_RELAY_HOST;
        port = DEFAULT_RELAY_PORT;
        enabled = true;
    }
}
