package com.thunder.wildernessodysseyapi.rules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.Core.ModConstants;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import net.minecraft.server.MinecraftServer;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.GameRules;

public final class GameRulesListManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path RULES_PATH = Paths.get("config", ModConstants.MOD_ID, "game-rules.json");

    private static volatile GameRulesConfig cachedConfig;

    private GameRulesListManager() {
    }

    public static void ensureRulesFileExists(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (Files.exists(RULES_PATH)) {
            return;
        }
        try {
            if (RULES_PATH.getParent() != null) {
                Files.createDirectories(RULES_PATH.getParent());
            }
            writeDefaultRules(RULES_PATH, defaultConfig(server));
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed to create default game rules file {}: {}", RULES_PATH, e.getMessage());
        }
    }

    public static GameRulesConfig getRules() {
        GameRulesConfig current = cachedConfig;
        if (current != null) {
            return current;
        }
        synchronized (GameRulesListManager.class) {
            if (cachedConfig == null) {
                cachedConfig = loadRules();
            }
            return cachedConfig;
        }
    }

    public static void reload() {
        cachedConfig = loadRules();
    }

    private static GameRulesConfig loadRules() {
        if (!Files.exists(RULES_PATH)) {
            return new GameRulesConfig(Collections.emptyList(), Collections.emptyList());
        }
        try (Reader reader = Files.newBufferedReader(RULES_PATH, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                return new GameRulesConfig(Collections.emptyList(), Collections.emptyList());
            }
            List<GameRuleEntry> serverRules = readRuleEntries(root.get("server_rules"));
            List<GameRuleEntry> singlePlayerRules = readRuleEntries(root.get("single_player_rules"));
            return new GameRulesConfig(serverRules, singlePlayerRules);
        } catch (IOException e) {
            ModConstants.LOGGER.warn("Failed to read game rules file {}: {}", RULES_PATH, e.getMessage());
            return new GameRulesConfig(Collections.emptyList(), Collections.emptyList());
        }
    }

    private static List<GameRuleEntry> readRuleEntries(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return Collections.emptyList();
        }
        List<GameRuleEntry> values = new ArrayList<>();
        element.getAsJsonArray().forEach(item -> {
            if (item != null && item.isJsonObject()) {
                JsonObject obj = item.getAsJsonObject();
                String name = obj.has("name") ? obj.get("name").getAsString() : null;
                String value = obj.has("value") ? obj.get("value").getAsString() : null;
                if (name != null && !name.isBlank() && value != null) {
                    values.add(new GameRuleEntry(name, value));
                }
            }
        });
        return values;
    }

    private static void writeDefaultRules(Path path, GameRulesConfig config) throws IOException {
        JsonObject root = new JsonObject();
        root.add("server_rules", GSON.toJsonTree(config.serverRules()));
        root.add("single_player_rules", GSON.toJsonTree(config.singlePlayerRules()));
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }

    public static void applyConfiguredRules(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        GameRulesConfig rules = getRules();
        List<GameRuleEntry> entries = server.isDedicatedServer() ? rules.serverRules() : rules.singlePlayerRules();
        if (entries.isEmpty()) {
            return;
        }
        var commandSource = server.createCommandSourceStack();
        for (GameRuleEntry entry : entries) {
            String command = "gamerule " + entry.name() + " " + entry.value();
            server.getCommands().performPrefixedCommand(commandSource, command);
        }
    }

    private static GameRulesConfig defaultConfig(MinecraftServer server) {
        List<GameRuleEntry> vanillaRules = loadVanillaRuleEntries(server);
        return new GameRulesConfig(vanillaRules, vanillaRules);
    }

    public record GameRulesConfig(List<GameRuleEntry> serverRules, List<GameRuleEntry> singlePlayerRules) {
        public GameRulesConfig {
            serverRules = serverRules == null ? Collections.emptyList() : List.copyOf(serverRules);
            singlePlayerRules = singlePlayerRules == null ? Collections.emptyList() : List.copyOf(singlePlayerRules);
        }
    }

    public record GameRuleEntry(String name, String value) {
        public GameRuleEntry {
            name = name == null ? "" : name.trim();
            value = value == null ? "" : value.trim();
        }
    }

    private static List<GameRuleEntry> loadVanillaRuleEntries(MinecraftServer server) {
        List<GameRuleEntry> entries = new ArrayList<>();
        var gameRules = server.getGameRules();
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                String name = key.getId();
                String value = gameRules.getRule(key).toString();
                entries.add(new GameRuleEntry(name, value));
            }
        });
        entries.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return List.copyOf(entries);
    }
}
