package com.thunder.wildernessodysseyapi.globalchat;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

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

    private static final Moshi MOSHI = new Moshi.Builder().build();
    private static final JsonAdapter<GlobalChatSettings> ADAPTER = MOSHI.adapter(GlobalChatSettings.class);

    private String host = "";
    private int port = 0;
    private boolean enabled = false;
    private List<String> downtimeHistory = new ArrayList<>();
    private boolean allowServerAutostart = false;
    private String moderationToken = "";
    private String clusterToken = "";

    public static GlobalChatSettings load(Path file) {
        if (Files.exists(file)) {
            try {
                String raw = Files.readString(file);
                GlobalChatSettings settings = ADAPTER.fromJson(raw);
                if (settings != null) {
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
            Files.writeString(file, Objects.requireNonNull(ADAPTER.toJson(this)));
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
}
