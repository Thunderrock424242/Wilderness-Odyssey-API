package com.thunder.wildernessodysseyapi.ModListTracker;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import com.google.gson.Gson;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks and logs changes to the installed mod list.
 */
public class ModTracker {
    private static final Path TRACKING_FILE = Paths.get("config", "mods-tracking.json");
    private static final Path LOG_FILE = Paths.get("logs", "mod-changes.log");

    private static final List<String> addedMods = new ArrayList<>();
    private static final List<String> removedMods = new ArrayList<>();
    private static final List<String> updatedMods = new ArrayList<>();
    private static String versionChange = "";

    /**
     * Compares the current mod list with the previous one and logs any differences.
     */
    public static void checkModChanges() {
        Map<String, String> currentMods = getCurrentMods();
        ModTrackingHistory history = loadHistory();
        Map<String, String> previousMods = Collections.emptyMap();
        if (history != null && history.lastVersion != null) {
            previousMods = history.versions.getOrDefault(history.lastVersion, Collections.emptyMap());
        }

        versionChange = "";
        if (history != null && history.lastVersion != null &&
                !Objects.equals(history.lastVersion, com.thunder.wildernessodysseyapi.Core.ModConstants.VERSION)) {
            versionChange = "Pack updated from " + history.lastVersion + " to " + com.thunder.wildernessodysseyapi.Core.ModConstants.VERSION;
        }

        addedMods.clear();
        removedMods.clear();
        updatedMods.clear();

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

        logChanges(addedMods, removedMods, updatedMods);

        if (history == null) {
            history = new ModTrackingHistory();
        }
        history.lastVersion = com.thunder.wildernessodysseyapi.Core.ModConstants.VERSION;
        history.versions.put(com.thunder.wildernessodysseyapi.Core.ModConstants.VERSION, currentMods);
        saveHistory(history);
    }

    /** Returns the list of newly added mods. */
    public static List<String> getAddedMods() { return addedMods; }
    /** Returns the list of removed mods. */
    public static List<String> getRemovedMods() { return removedMods; }
    /** Returns the list of updated mods. */
    public static List<String> getUpdatedMods() { return updatedMods; }
    /** Returns a message describing pack version changes. */
    public static String getVersionChange() { return versionChange; }

    /** Returns the mods recorded for a specific pack version. */
    public static Map<String, String> getModsForVersion(String version) {
        ModTrackingHistory history = loadHistory();
        if (history == null) return Collections.emptyMap();
        Map<String, String> mods = history.versions.get(version);
        return mods != null ? mods : Collections.emptyMap();
    }

    private static Map<String, String> getCurrentMods() {
        return ModList.get().getMods().stream()
                .filter(mod -> mod instanceof ModInfo)
                .map(mod -> (ModInfo) mod)
                .collect(Collectors.toMap(
                        ModInfo::getModId,
                        mod -> mod.getVersion().toString()
                ));
    }

    private static ModTrackingHistory loadHistory() {
        if (!Files.exists(TRACKING_FILE)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(TRACKING_FILE)) {
            return new Gson().fromJson(reader, ModTrackingHistory.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void saveHistory(ModTrackingHistory history) {
        try (Writer writer = Files.newBufferedWriter(TRACKING_FILE)) {
            new Gson().toJson(history, writer);
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
            Files.createDirectories(LOG_FILE.getParent());
            Files.write(LOG_FILE, logMessage.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ModTrackingHistory {
        String lastVersion;
        Map<String, Map<String, String>> versions = new HashMap<>();
    }
}
