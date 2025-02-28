package com.thunder.wildernessodysseyapi.NovaAPI.RenderEngine.Threading;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RenderThreadManager {
    private static final ExecutorService RENDER_THREAD = Executors.newSingleThreadExecutor();
    private static final Set<String> incompatibleMods = new HashSet<>();

    public static void execute(String modName, Runnable task) {
        if (incompatibleMods.contains(modName)) {
            // If the mod is marked as incompatible, run it on the main thread immediately
            task.run();
            return;
        }

        RENDER_THREAD.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                markModAsIncompatible(modName, e);
                task.run(); // Retry on main thread
            }
        });
    }

    private static void markModAsIncompatible(String modName, Exception e) {
        if (incompatibleMods.add(modName)) { // Only log the first failure
            System.err.println("[RenderThreadManager] Mod " + modName + " crashed on render thread! Moving it to main thread.");
            logIncompatibleMod(modName, e);
        }
    }

    private static void logIncompatibleMod(String modName, Exception e) {
        try (FileWriter writer = new FileWriter("render_thread_incompatibility.log", true)) {
            writer.write("Mod: " + modName + " is incompatible with the render thread.\n");
            writer.write("Error: " + e.getMessage() + "\n\n");
        } catch (IOException ex) {
            System.err.println("[RenderThreadManager] Failed to write incompatibility log.");
            ex.printStackTrace();
        }
    }

    public static void shutdown() {
        RENDER_THREAD.shutdown();
    }
}
