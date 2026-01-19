package com.thunder.wildernessodysseyapi.changelog;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChangelogManager {

    private static final Path CHANGELOG_PATH = Paths.get("config", ModConstants.MOD_ID, "changelog.txt");
    private static final String DEFAULT_RESOURCE = "/config/wildernessodysseyapi/changelog.txt";

    private ChangelogManager() {
    }

    public static List<String> getAvailableVersions() {
        return new ArrayList<>(loadChangelog().keySet());
    }

    public static int sendChangelog(CommandSourceStack source, String version) {
        Map<String, List<String>> entries = loadChangelog();
        List<String> lines = entries.get(version);
        if (lines == null) {
            source.sendFailure(Component.literal("No changelog found for version " + version + "."));
            return 0;
        }
        List<Component> components = buildChangelogComponents(version, lines);
        for (Component component : components) {
            source.sendSuccess(() -> component, false);
        }
        return 1;
    }

    public static boolean sendChangelog(ServerPlayer player, String version) {
        Map<String, List<String>> entries = loadChangelog();
        List<String> lines = entries.get(version);
        if (lines == null) {
            player.sendSystemMessage(Component.literal("No changelog found for version " + version + ".")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        List<Component> components = buildChangelogComponents(version, lines);
        for (Component component : components) {
            player.sendSystemMessage(component);
        }
        return true;
    }

    public static void writeSeenFile(Path seenPath, String version) {
        try {
            if (seenPath.getParent() != null && !Files.exists(seenPath.getParent())) {
                Files.createDirectories(seenPath.getParent());
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("last_seen_version", version);
            try (Writer writer = Files.newBufferedWriter(seenPath, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(obj, writer);
            }
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed to write changelog seen file at {}: {}", seenPath, e.getMessage());
        }
    }

    private static List<Component> buildChangelogComponents(String version, List<String> lines) {
        List<Component> components = new ArrayList<>();
        components.add(Component.literal("Wilderness Odyssey Changelog " + version)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.endsWith(":")) {
                components.add(Component.literal(trimmed)
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
                continue;
            }
            if (trimmed.startsWith("-")) {
                String item = trimmed.startsWith("- ") ? trimmed.substring(2) : trimmed.substring(1);
                components.add(Component.literal("• " + item)
                        .withStyle(ChatFormatting.GRAY));
                continue;
            }
            components.add(Component.literal(trimmed).withStyle(ChatFormatting.WHITE));
        }
        return components;
    }

    private static Map<String, List<String>> loadChangelog() {
        ensureChangelogFile();
        Map<String, List<String>> entries = new LinkedHashMap<>();
        try {
            List<String> lines = Files.readAllLines(CHANGELOG_PATH, StandardCharsets.UTF_8);
            String currentVersion = null;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("## ")) {
                    currentVersion = trimmed.substring(3).trim();
                    entries.putIfAbsent(currentVersion, new ArrayList<>());
                    continue;
                }
                if (currentVersion != null) {
                    entries.get(currentVersion).add(line);
                }
            }
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed to read changelog file {}: {}", CHANGELOG_PATH, e.getMessage());
        }
        return entries;
    }

    private static void ensureChangelogFile() {
        try {
            if (Files.exists(CHANGELOG_PATH)) {
                return;
            }
            if (CHANGELOG_PATH.getParent() != null) {
                Files.createDirectories(CHANGELOG_PATH.getParent());
            }
            if (copyBundledChangelog()) {
                return;
            }
            List<String> defaultLines = List.of(
                    "# Wilderness Odyssey Changelog",
                    "## " + ModConstants.VERSION,
                    "Added:",
                    "- Welcome to your new world!",
                    "Changed:",
                    "- Customize this file in config/" + ModConstants.MOD_ID + "/changelog.txt",
                    "Fixed:",
                    "- ",
                    "",
                    "## 0.0.3",
                    "Added:",
                    "- Example previous release entry",
                    "Changed:",
                    "- ",
                    "Fixed:",
                    "- "
            );
            Files.write(CHANGELOG_PATH, defaultLines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed to create default changelog file {}: {}", CHANGELOG_PATH, e.getMessage());
        }
    }

    private static boolean copyBundledChangelog() {
        try (InputStream stream = ChangelogManager.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                return false;
            }
            Files.copy(stream, CHANGELOG_PATH);
            return true;
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed to copy bundled changelog from {}: {}", DEFAULT_RESOURCE, e.getMessage());
            return false;
        }
    }
}
