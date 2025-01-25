package com.thunder.wildernessodysseyapi.ModConflictChecker;

import com.thunder.wildernessodysseyapi.ModConflictChecker.Util.LoggerUtil;
import com.thunder.wildernessodysseyapi.ModConflictChecker.Util.LoggerUtil.ConflictSeverity;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@EventBusSubscriber
public class RegistryConflictHandler {

    private static final Map<ResourceLocation, String> biomeRegistry = new HashMap<>();
    private static final Map<ResourceLocation, String> dimensionRegistry = new HashMap<>();
    private static final Map<ResourceLocation, String> blockRegistry = new HashMap<>();
    private static final Map<ResourceLocation, String> itemRegistry = new HashMap<>();
    private static final Map<ResourceLocation, String> entityRegistry = new HashMap<>();
    private static final Map<ResourceLocation, String> structureRegistry = new HashMap<>(); // Tracks original sources of registered structures

    public static void initialize() {
        LoggerUtil.log(ConflictSeverity.INFO, "RegistryConflictHandler initialized.");
    }

    @SubscribeEvent
    public static void onServerStart(ServerStartingEvent event) {
        LoggerUtil.log(ConflictSeverity.INFO, "Server starting. Checking for registry conflicts...");

        // Detect conflicts across different registries
        detectConflicts(NeoForgeRegistries.BIOMES, biomeRegistry, "Biome");
        detectConflicts(NeoForgeRegistries.DIMENSIONS, dimensionRegistry, "Dimension");
        detectConflicts(NeoForgeRegistries.BLOCKS, blockRegistry, "Block");
        detectConflicts(NeoForgeRegistries.ITEMS, itemRegistry, "Item");
        detectConflicts(NeoForgeRegistries.ENTITIES, entityRegistry, "Entity");

        // Check for structure overwrites
        NeoForgeRegistries.STRUCTURES.getKeys().forEach(key -> {
            String modSource = key.getNamespace();

            if (structureRegistry.containsKey(key)) {
                String originalMod = structureRegistry.get(key);

                // Log structure overwrite conflicts
                if (!originalMod.equals(modSource)) {
                    LoggerUtil.log(ConflictSeverity.ERROR, String.format(
                            "Structure overwrite detected: '%s' was originally registered by '%s' but has been replaced by '%s'.",
                            key, originalMod, modSource));
                }
            } else {
                structureRegistry.put(key, modSource);
                LoggerUtil.log(ConflictSeverity.INFO, String.format(
                        "Structure '%s' registered by '%s'.", key, modSource));
            }
        });
    }

    /**
     * Detects conflicts in a specific registry and logs them using LoggerUtil.
     *
     * @param registry        The NeoForge registry to check.
     * @param trackedRegistry The map tracking registered items and their sources.
     * @param type            The type of registry (e.g., "Block", "Item").
     * @param <T>             The type of objects in the registry.
     */
    private static <T> void detectConflicts(NeoForgeRegistries registry, Map<ResourceLocation, String> trackedRegistry, String type) {
        registry.getKeys().forEach(key -> {
            String modSource = key.getNamespace();
            if (trackedRegistry.containsKey(key)) {
                String otherMod = trackedRegistry.get(key);

                // Log if a conflict is detected
                if (!modSource.equals(otherMod)) {
                    LoggerUtil.log(ConflictSeverity.ERROR, String.format(
                            "Conflict detected: %s '%s' registered by both '%s' and '%s'.",
                            (Object) type, Optional.of(key), otherMod, modSource));
                }
            } else {
                trackedRegistry.put(key, modSource);
                LoggerUtil.log(ConflictSeverity.INFO, String.format(
                        "%s '%s' registered by '%s'.", (Object) type, Optional.of(key), modSource));
            }
        });
    }
}
