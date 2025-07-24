package com.thunder.wildernessodysseyapi.ModConflictChecker;

import com.thunder.wildernessodysseyapi.ModConflictChecker.Util.LoggerUtil;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.moddiscovery.ModFile;

import java.io.IOException;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/****
 * ShaderConflictChecker for the Wilderness Odyssey API mod.
 */
public class ShaderConflictChecker {

    private static final Map<String, List<String>> shaderUsageMap = new HashMap<>();

    public static void scanForConflicts() {
        LoggerUtil.log(LoggerUtil.ConflictSeverity.INFO, "Scanning mods for potential shader conflicts...");

        for (var mod : ModList.get().getMods()) {
            ModFile file = (ModFile) mod.getOwningFile().getFile();
            if (file == null || !file.getFilePath().toString().endsWith(".jar")) continue;

            try (JarFile jar = new JarFile(file.getFilePath().toFile())) {
                Enumeration<? extends ZipEntry> entries = jar.entries();

                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();

                    if (name.startsWith("assets/") && name.contains("/shaders/") && (name.endsWith(".vsh") || name.endsWith(".fsh") || name.endsWith(".json"))) {
                        shaderUsageMap.computeIfAbsent(name, k -> new ArrayList<>()).add(mod.getModId());
                    }
                }
            } catch (IOException e) {
                LoggerUtil.log(LoggerUtil.ConflictSeverity.WARN, "Failed to inspect mod: " + mod.getModId() + " for shader conflicts. " + e.getMessage());
            }
        }

        // Detect and log conflicts
        shaderUsageMap.forEach((shaderPath, modIds) -> {
            if (modIds.size() > 1) {
                LoggerUtil.log(LoggerUtil.ConflictSeverity.ERROR,
                        String.format("Shader conflict detected: '%s' is used by multiple mods: %s",
                                shaderPath, String.join(", ", modIds)));
            }
        });
    }
}