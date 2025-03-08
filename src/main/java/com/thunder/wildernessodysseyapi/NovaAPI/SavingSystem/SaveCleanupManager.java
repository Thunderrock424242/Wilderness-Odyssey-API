package com.thunder.wildernessodysseyapi.NovaAPI.SavingSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SaveCleanupManager {
    private static final Path SAVE_DIR = Paths.get("config", "NovaAPI", "saves");
    private static final ExecutorService CLEANUP_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int CLEANUP_THRESHOLD_DAYS = 30;

    static {
        CLEANUP_EXECUTOR.execute(SaveCleanupManager::cleanOldFiles);
    }

    private static void cleanOldFiles() {
        try {
            cleanDirectory(SAVE_DIR);
        } catch (IOException e) {
            System.err.println("Failed to clean old save files!");
            e.printStackTrace();
        }
    }

    private static void cleanDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        Instant threshold = Instant.now().minus(CLEANUP_THRESHOLD_DAYS, ChronoUnit.DAYS);

        Files.list(directory)
                .filter(Files::isRegularFile)
                .filter(file -> {
                    try {
                        return Files.getLastModifiedTime(file).toInstant().isBefore(threshold);
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(file -> {
                    try {
                        Files.delete(file);
                        System.out.println("Deleted old save file: " + file);
                    } catch (IOException e) {
                        System.err.println("Failed to delete old save file: " + file);
                    }
                });
    }

    public static void shutdown() {
        CLEANUP_EXECUTOR.shutdown();
    }
}