package com.thunder.wildernessodysseyapi.NovaAPI.SavingSystem;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AutoSaveManager {
    private static final ScheduledExecutorService AUTO_SAVE_EXECUTOR = Executors.newScheduledThreadPool(1);
    private static final int AUTO_SAVE_INTERVAL = 300; // 5 minutes

    public static void startAutoSave() {
        AUTO_SAVE_EXECUTOR.scheduleAtFixedRate(AutoSaveManager::performAutoSave, AUTO_SAVE_INTERVAL, AUTO_SAVE_INTERVAL, TimeUnit.SECONDS);
        System.out.println("[NovaAPI] Auto-Save initialized. Saving every 5 minutes.");
    }

    private static void performAutoSave() {
        System.out.println("[NovaAPI] Auto-Saving...");
        SaveManager.saveDataAsync("world_data", SaveManager.loadData("world_data"));
        KryoSaveManager.saveDataAsync("mod_data", KryoSaveManager.loadData("mod_data", Object.class));
        System.out.println("[NovaAPI] Auto-Save Complete.");
    }

    public static void shutdown() {
        AUTO_SAVE_EXECUTOR.shutdown();
    }
}