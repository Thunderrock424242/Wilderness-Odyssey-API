package com.thunder.wildernessodysseyapi.ModPackPatches.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.ModConflictChecker.Util.LoggerUtil;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber
public class WorldVersionChecker {

    /** The default world version for the mod; update this when releasing new versions */
    public static final String MOD_DEFAULT_WORLD_VERSION = "0.0.0"; // Update your Mod Version Here Major , Minor , Patch

    /** Caches the config version to avoid repeated file reads */
    private static volatile String cachedConfigVersion = null;

    /** Keeps track of which worlds have notified operators to avoid redundant messages */
    private static final ConcurrentHashMap<String, Boolean> notifiedWorlds = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        try {
            Path configPath = Paths.get("config/world_version.json");
            cachedConfigVersion = readVersion(configPath);

            int[] newDefaultSegments = parseVersion(MOD_DEFAULT_WORLD_VERSION);
            int[] oldConfigSegments = parseVersion(cachedConfigVersion);

            if (isVersionGreater(newDefaultSegments, oldConfigSegments)) {
                saveVersion(configPath, MOD_DEFAULT_WORLD_VERSION);
                cachedConfigVersion = MOD_DEFAULT_WORLD_VERSION;
                LoggerUtil.log(LoggerUtil.ConflictSeverity.INFO, "Config 'world_version' updated from " + oldConfigSegments[0] + "." + oldConfigSegments[1] + "." + oldConfigSegments[2] + " to new default: " + MOD_DEFAULT_WORLD_VERSION);
            } else {
                LoggerUtil.log(LoggerUtil.ConflictSeverity.INFO, "Config 'world_version' is up to date: " + cachedConfigVersion);
            }
        } catch (Exception e) {
            LoggerUtil.log(LoggerUtil.ConflictSeverity.ERROR, "Exception in WorldVersionChecker.onServerStarted: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            String worldId = getWorldIdentifier(player.serverLevel().getServer());

            if (notifiedWorlds.containsKey(worldId)) return;

            if (cachedConfigVersion == null) {
                cachedConfigVersion = readVersion(Paths.get("config/world_version.json"));
            }

            Path worldPath = player.serverLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("world_version.json");
            String worldVersion = readVersion(worldPath);

            int[] configSegments = parseVersion(cachedConfigVersion);
            int[] worldSegments = parseVersion(worldVersion);

            if (isVersionGreater(configSegments, worldSegments)) {
                String updateType = getUpdateType(worldVersion, cachedConfigVersion);
                if ("Patch".equals(updateType)) {
                    // Auto-update world version for patch bumps
                    try {
                        saveVersion(worldPath, cachedConfigVersion);
                        LoggerUtil.log(LoggerUtil.ConflictSeverity.INFO, "[WorldVersionChecker] Auto-updated patch world version: " + cachedConfigVersion);
                    } catch (Exception e) {
                        LoggerUtil.log(LoggerUtil.ConflictSeverity.ERROR, "[WorldVersionChecker] Failed to auto-update patch: " + e.getMessage());
                    }
                }
                // Notify OP for ALL update types (Major, Minor, Patch)
                if (player.serverLevel().getServer().getPlayerList().isOp(player.getGameProfile())) {
                    String msg = "[WorldVersionChecker] World version " + updateType + " update needed! Please back up your world and run /updateworldversion to apply the update. Old version: " + worldVersion + ", New version: " + cachedConfigVersion;
                    player.sendSystemMessage(Component.literal(msg));
                    LoggerUtil.log(LoggerUtil.ConflictSeverity.INFO, "Sent world version warning to op: " + player.getGameProfile().getName());
                }
                notifiedWorlds.put(worldId, true);
            }
        } catch (Exception e) {
            LoggerUtil.log(LoggerUtil.ConflictSeverity.ERROR, "Exception in WorldVersionChecker.onPlayerJoin: " + e.getMessage());
            e.printStackTrace();
        }
    }


    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("updateworldversion")
                        .requires(cs -> cs.hasPermission(4))
                        .executes(ctx -> {
                            MinecraftServer server = ctx.getSource().getServer();
                            try {
                                Path configPath = Paths.get("config/world_version.json");
                                String configVersion = readVersion(configPath);
                                Path worldPath = server.getWorldPath(LevelResource.ROOT).resolve("world_version.json");
                                saveVersion(worldPath, configVersion);
                                ctx.getSource().sendSuccess(() -> Component.literal("World version updated to " + configVersion), true);
                                LoggerUtil.log(LoggerUtil.ConflictSeverity.INFO, "[WorldVersionChecker] World 'world_version.json' updated to " + configVersion + " via /updateworldversion command.");
                            } catch (Exception e) {
                                ctx.getSource().sendFailure(Component.literal("Failed to update world version: " + e.getMessage()));
                                LoggerUtil.log(LoggerUtil.ConflictSeverity.ERROR, "[WorldVersionChecker] Error updating world version: " + e.getMessage());
                            }
                            return 1;
                        })
        );
    }


    private static String getWorldIdentifier(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).toString();
    }


    public static String readVersion(Path path) throws IOException {
        if (!Files.exists(path)) {
            saveVersion(path, MOD_DEFAULT_WORLD_VERSION);
            LoggerUtil.log(LoggerUtil.ConflictSeverity.INFO, "Created new version file with default "+ MOD_DEFAULT_WORLD_VERSION  + " at " + path);
            return MOD_DEFAULT_WORLD_VERSION;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            return obj.get("world_version").getAsString();
        } catch (Exception e) {
            LoggerUtil.log(LoggerUtil.ConflictSeverity.ERROR, "Failed to read or parse version from: " + path + " -- " + e.getMessage());
            throw new IOException("Failed to read or parse version from: " + path, e);
        }
    }


    public static void saveVersion(Path path, String version) throws IOException {
        if (path.getParent() != null && !Files.exists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("world_version", version);
        try (Writer writer = Files.newBufferedWriter(path)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(obj, writer);
        }
    }


    public static int[] parseVersion(String version) {
        try {
            String[] parts = version.split("\\.");
            if (parts.length != 3) throw new IllegalArgumentException("Version must be in format x.y.z");
            return Arrays.stream(parts).mapToInt(Integer::parseInt).toArray();
        } catch (Exception e) {
            LoggerUtil.log(LoggerUtil.ConflictSeverity.ERROR, "Failed to parse version string '" + version + "': " + e.getMessage());
            return new int[]{0, 0, 0};
        }
    }


    public static boolean isVersionGreater(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] > b[i]) return true;
            if (a[i] < b[i]) return false;
        }
        return false;
    }


    public static String getUpdateType(String oldVersion, String newVersion) {
        int[] oldSegment = parseVersion(oldVersion);
        int[] newSegment = parseVersion(newVersion);
        if (newSegment[0] > oldSegment[0]) return "Major";
        if (newSegment[1] > oldSegment[1]) return "Minor";
        return "Patch";
    }
}