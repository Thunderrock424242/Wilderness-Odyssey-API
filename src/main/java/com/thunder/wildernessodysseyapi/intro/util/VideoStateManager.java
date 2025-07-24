package com.thunder.wildernessodysseyapi.intro.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

public class VideoStateManager {
    private static final String STATE_FILE = "playonjoin_state.json";
    private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
    private static Map<String, Boolean> videoPlayedState = new HashMap();

    public static boolean hasVideoBeenPlayed() {
        String worldId = getCurrentWorldId();
        return (Boolean)videoPlayedState.getOrDefault(worldId, false);
    }

    public static void markVideoAsPlayed() {
        String worldId = getCurrentWorldId();
        videoPlayedState.put(worldId, true);
        saveState();
    }

    private static String getCurrentWorldId() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            IntegratedServer server = mc.getSingleplayerServer();
            if (server != null) {
                return "singleplayer_" + server.getWorldData().worldGenOptions().seed();
            } else {
                String serverIP = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "unknown";
                return "multiplayer_" + serverIP.hashCode();
            }
        } else {
            return "unknown_world";
        }
    }

    public static void loadState() {
        File configDir = new File(new File(Minecraft.getInstance().gameDirectory, "config"), "playonjoin");
        File stateFile = new File(configDir, "playonjoin_state.json");
        if (stateFile.exists()) {
            try (FileReader reader = new FileReader(stateFile)) {
                Type type = (new 1()).getType();
                Map<String, Boolean> loadedState = (Map)GSON.fromJson(reader, type);
                if (loadedState != null) {
                    videoPlayedState = loadedState;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private static void saveState() {
        File configDir = new File(new File(Minecraft.getInstance().gameDirectory, "config"), "playonjoin");
        configDir.mkdirs();
        File stateFile = new File(configDir, "playonjoin_state.json");

        try (FileWriter writer = new FileWriter(stateFile)) {
            GSON.toJson(videoPlayedState, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
