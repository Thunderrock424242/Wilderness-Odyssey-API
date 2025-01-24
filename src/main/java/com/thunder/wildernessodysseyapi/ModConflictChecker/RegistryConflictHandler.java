package com.thunder.wildernessodysseyapi.ModConflictChecker;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.LOGGER;

@EventBusSubscriber
public class RegistryConflictHandler {
    private static final Map<ResourceLocation, String> biomeRegistry = new HashMap<>();
    private static final Map<ResourceLocation, String> dimensionRegistry = new HashMap<>();
    private static final Map<ResourceLocation, String> blockRegistry = new HashMap<>();
    private static final Map<ResourceLocation, String> itemRegistry = new HashMap<>();
    private static final Map<ResourceLocation, String> entityRegistry = new HashMap<>();
    // Track the original sources of registered structures
    private static final Map<ResourceLocation, String> structureRegistry = new HashMap<>();

    public static void initialize() {
        LOGGER.info("RegistryConflictHandler initialized.");
    }

    @SubscribeEvent
    public static void onServerStart(ServerStartingEvent event) {
        LOGGER.info("Server starting. Checking for registry conflicts...");
        LOGGER.info("Server starting. Checking for structure overwrites...");

        detectConflicts(NeoForgeRegistries.BIOMES, biomeRegistry, "Biome");
        detectConflicts(NeoForgeRegistries.DIMENSIONS, dimensionRegistry, "Dimension");
        detectConflicts(NeoForgeRegistries.BLOCKS, blockRegistry, "Block");
        detectConflicts(NeoForgeRegistries.ITEMS, itemRegistry, "Item");
        detectConflicts(NeoForgeRegistries.ENTITIES, entityRegistry, "Entity");

        NeoForgeRegistries.STRUCTURES.getKeys().forEach(key -> {
            String modSource = key.getNamespace();

            if (structureRegistry.containsKey(key)) {
                String originalMod = structureRegistry.get(key);

                // If the structure is being overwritten, log the conflict
                if (!originalMod.equals(modSource)) {
                    LOGGER.error("Structure overwrite detected: '{}' was originally registered by '{}' but has been replaced by '{}'.",
                            key, originalMod, modSource);
                }
            } else {
                // Record the original source of the structure
                structureRegistry.put(key, modSource);
            }
        });
    }

    private static <T> void detectConflicts(NeoForgeRegistries registry, Map<ResourceLocation, String> trackedRegistry, String type) {
        registry.getKeys().forEach(key -> {
            String modSource = key.getNamespace();
            if (trackedRegistry.containsKey(key)) {
                String otherMod = trackedRegistry.get(key);
                if (!modSource.equals(otherMod)) {
                    LOGGER.error("Conflict detected: {} '{}' registered by both '{}' and '{}'",
                            (Object) type, Optional.of(key), otherMod, modSource);
                }
            } else {
                trackedRegistry.put(key, modSource);
            }
        });
    }
}