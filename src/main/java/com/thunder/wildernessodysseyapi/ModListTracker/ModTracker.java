package com.thunder.wildernessodysseyapi.ModListTracker;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ModTracker {
    private static final Path TRACKING_FILE = Paths.get("config", "mods-tracking.json");
    private static final Path LOG_FILE = Paths.get("logs", "mod-changes.log");

    public static void checkModChanges() {
        // Get current mods with versions as Strings
        Map<String, String> currentMods = getCurrentMods();

        // Load previous mods
        Map<String, String> previousMods = loadPreviousMods();

        // Compare mods
        List<String> addedMods = new ArrayList<>();
        List<String> removedMods = new ArrayList<>();
        List<String> updatedMods = new ArrayList<>();

        for (String modId : currentMods.keySet()) {
            if (!previousMods.containsKey(modId)) {
                addedMods.add(modId + " v" + currentMods.get(modId));
            } else if (!Objects.equals(previousMods.get(modId), currentMods.get(modId))) {
                updatedMods.add(modId + " updated from " + previousMods.get(modId) + " to " + currentMods.get(modId));
            }
        }

        for (String modId : previousMods.keySet()) {
            if (!currentMods.containsKey(modId)) {
                removedMods.add(modId + " v" + previousMods.get(modId));
            }
        }

        // Log changes
        logChanges(addedMods, removedMods, updatedMods);

        // Save current mod list for next launch
        saveModList(currentMods);
    }

    private static Map<String, String> getCurrentMods() {
        return ModList.get().getMods().stream()
                .filter(mod -> mod instanceof ModInfo) // Ensure we're working with ModInfo
                .map(mod -> (ModInfo) mod) // Explicitly cast to ModInfo
                .collect(Collectors.toMap(
                        ModInfo::getModId,
                        mod -> mod.getVersion().toString() // Convert ArtifactVersion to String
                ));
    }





    private static Map<String, String> loadPreviousMods() {
        if (!Files.exists(TRACKING_FILE)) {
            return new HashMap<>();
        }
        try (Reader reader = Files.newBufferedReader(TRACKING_FILE)) {
            return new Gson().fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
        } catch (IOException e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    private static void saveModList(Map<String, String> modList) {
        try (Writer writer = Files.newBufferedWriter(TRACKING_FILE)) {
            new Gson().toJson(modList, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void logChanges(List<String> added, List<String> removed, List<String> updated) {
        StringBuilder logMessage = new StringBuilder();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        logMessage.append("\n[Mod Tracker] === Mod Changes at ").append(timestamp).append(" ===\n");

        if (!added.isEmpty()) {
            logMessage.append("[Added Mods]: ").append(String.join(", ", added)).append("\n");
        }
        if (!removed.isEmpty()) {
            logMessage.append("[Removed Mods]: ").append(String.join(", ", removed)).append("\n");
        }
        if (!updated.isEmpty()) {
            logMessage.append("[Updated Mods]: ").append(String.join(", ", updated)).append("\n");
        }

        if (added.isEmpty() && removed.isEmpty() && updated.isEmpty()) {
            logMessage.append("No mod changes detected.\n");
        }

        System.out.println(logMessage.toString());
        writeToFile(logMessage.toString());
    }

    private static void writeToFile(String logMessage) {
        try {
            Files.createDirectories(LOG_FILE.getParent()); // Ensure logs directory exists
            Files.write(LOG_FILE, logMessage.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
