package com.thunder.wildernessodysseyapi.ModPackPatches.WorldUpgrader;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class WorldUpgrade {


    // Current Mod Data Version
    private static final int CURRENT_VERSION = 2;

    // Custom data storage (example)
    private static Map<String, byte[]> customData = new HashMap<>();

    // Example data fields
    private static long worldSeed;
    private static int modDataVersion;

    /**
     * Writes all custom mod data into a direct byte buffer for async I/O operations.
     */
    public static void writeCustomDataToBuffer(ByteBuffer buffer) {
        buffer.putInt(CURRENT_VERSION); // versioning
        buffer.putLong(worldSeed);

        buffer.putInt(customData.size());
        customData.forEach((key, data) -> {
            writeString(buffer, key);
            buffer.putInt(data.length);
            buffer.put(data);
        });

        logger.info("Successfully wrote custom data to buffer.");
    }

    /**
     * Reads and upgrades mod data from buffer.
     */
    public static void readCustomDataFromBuffer(ByteBuffer buffer) {
        modDataVersion = buffer.getInt();
        worldSeed = buffer.getLong();

        int dataEntries = buffer.getInt();
        customData.clear();

        for (int i = 0; i < dataEntries; i++) {
            String key = readString(buffer);
            int dataLength = buffer.getInt();
            byte[] data = new byte[dataLength];
            buffer.get(data);
            customData.put(key, data);
        }

        if (modDataVersion < CURRENT_VERSION) {
            upgradeData(modDataVersion, CURRENT_VERSION);
        }

        logger.info("Successfully loaded custom data from buffer, data upgraded to version " + CURRENT_VERSION);
    }

    /**
     * Handles upgrading data from older versions to the current version.
     */
    private static void upgradeData(int oldVersion, int newVersion) {
        logger.info("Upgrading world data from version " + oldVersion + " to " + newVersion);

        // Example: Handle different upgrade paths based on version
        if (oldVersion == 1) {
            // Perform upgrade logic for version 1 to 2
            migrateData_v1_to_v2();
        }

        // Future-proof: additional migrations
        // if (oldVersion == 2 && newVersion == 3) { ... }

        logger.info("Data upgrade completed successfully.");
    }

    private static void migrateData_v1_to_v2() {
        // Example upgrade logic
        if (customData.containsKey("oldQuestData")) {
            byte[] oldData = customData.remove("oldQuestData");
            // Transform data format here...
            customData.put("newQuestData", oldData);
        }
        logger.info("Migrated custom quest data from v1 to v2.");
    }

    /**
     * Adds or updates custom mod data entry.
     */
    public static void setCustomData(String key, byte[] data) {
        customData.put(key, data);
    }

    /**
     * Retrieves custom mod data entry.
     */
    public static byte[] getCustomData(String key) {
        return customData.getOrDefault(key, new byte[0]);
    }

    /**
     * Helper method to write strings into buffer efficiently.
     */
    private static void writeString(ByteBuffer buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(bytes.length);
        buffer.put(bytes);
    }

    /**
     * Helper method to read strings from buffer efficiently.
     */
    private static String readString(ByteBuffer buffer) {
        int length = buffer.getInt();
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Setters/Getters for world seed (integrate with your world generation logic)
     */
    public static void setWorldSeed(long seed) {
        worldSeed = seed;
    }

    public static long getWorldSeed() {
        return worldSeed;
    }

    public static int getCurrentDataVersion() {
        return CURRENT_VERSION;
    }
}