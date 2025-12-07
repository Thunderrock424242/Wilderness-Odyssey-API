package com.thunder.wildernessodysseyapi.ModPackPatches.ModConflictChecker;

import com.thunder.wildernessodysseyapi.ModPackPatches.ModConflictChecker.Util.LoggerUtil;
import com.thunder.wildernessodysseyapi.ModPackPatches.ModConflictChecker.Util.LoggerUtil.ConflictSeverity;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Detects conflicts within registries handled by the server, such as structures and POIs.
 */
public class StructureConflictChecker {

    private static final Map<ResourceLocation, String> structureRegistry = new HashMap<>();
    private static final Map<ResourceLocation, String> poiRegistry = new HashMap<>();

    private StructureConflictChecker() {
        // Utility class
    }

    public static void checkStructureConflicts(Registry<?> structureRegistry) {
        checkRegistryConflicts(structureRegistry, StructureConflictChecker.structureRegistry, "Structure");
    }

    public static void checkPoiConflicts(Registry<?> poiRegistry) {
        checkRegistryConflicts(poiRegistry, StructureConflictChecker.poiRegistry, "POI");
    }

    private static <T> void checkRegistryConflicts(Registry<T> registry, Map<ResourceLocation, String> trackedRegistry, String type) {
        registry.keySet().forEach(key -> {
            String modSource = key.getNamespace();
            if (trackedRegistry.containsKey(key)) {
                String originalMod = trackedRegistry.get(key);

                if (!originalMod.equals(modSource)) {
                    LoggerUtil.log(ConflictSeverity.ERROR, String.format(
                            "Conflict detected: %s '%s' was originally registered by '%s' but has been overwritten by '%s'.",
                            type, key, originalMod, modSource), false);
                }
            } else {
                trackedRegistry.put(key, modSource);
                LoggerUtil.log(ConflictSeverity.INFO, String.format(
                        "%s '%s' registered by '%s'.", type, key, modSource), false);
            }
        });
    }
}
