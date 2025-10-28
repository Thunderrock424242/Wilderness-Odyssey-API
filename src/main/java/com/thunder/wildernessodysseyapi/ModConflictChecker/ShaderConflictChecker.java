package com.thunder.wildernessodysseyapi.ModConflictChecker;

import com.thunder.wildernessodysseyapi.ModConflictChecker.Util.LoggerUtil;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.moddiscovery.ModFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/****
 * ShaderConflictChecker for the Wilderness Odyssey API mod.
 */
public class ShaderConflictChecker {

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    public static CompletableFuture<Void> scanForConflictsAsync() {
        if (!RUNNING.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }

        LoggerUtil.log(LoggerUtil.ConflictSeverity.INFO,
                "Scanning mods for potential shader conflicts in background...", false);

        return CompletableFuture.runAsync(() -> {
            try {
                scanForConflictsInternal();
            } finally {
                RUNNING.set(false);
            }
        });
    }

    private static void scanForConflictsInternal() {
        Map<String, List<String>> shaderUsageMap = new HashMap<>();

        for (var mod : ModList.get().getMods()) {
            ModFile file = (ModFile) mod.getOwningFile().getFile();
            if (file == null || !file.getFilePath().toString().endsWith(".jar")) continue;

            try (JarFile jar = new JarFile(file.getFilePath().toFile())) {
                Enumeration<? extends ZipEntry> entries = jar.entries();

                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();

                    if (name.startsWith("assets/") && name.contains("/shaders/") &&
                            (name.endsWith(".vsh") || name.endsWith(".fsh") || name.endsWith(".json"))) {
                        shaderUsageMap.computeIfAbsent(name, k -> new ArrayList<>()).add(mod.getModId());
                    }
                }
            } catch (IOException e) {
                LoggerUtil.log(LoggerUtil.ConflictSeverity.WARN,
                        "Failed to inspect mod: " + mod.getModId() + " for shader conflicts. " + e.getMessage(), false);
            }
        }

        shaderUsageMap.forEach((shaderPath, modIds) -> {
            if (modIds.size() > 1) {
                LoggerUtil.log(LoggerUtil.ConflictSeverity.ERROR,
                        String.format("Shader conflict detected: '%s' is used by multiple mods: %s",
                                shaderPath, String.join(", ", modIds)), false);
            }
        });

        LoggerUtil.log(LoggerUtil.ConflictSeverity.INFO, "Shader conflict scan completed.", false);
    }
}