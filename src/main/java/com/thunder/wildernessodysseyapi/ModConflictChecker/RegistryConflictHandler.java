package com.thunder.wildernessodysseyapi.ModConflictChecker;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

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

    public static void initialize() {
        LOGGER.info("RegistryConflictHandler initialized.");
    }

    @SubscribeEvent
    public static void onServerStart(ServerStartingEvent event) {
        LOGGER.info("Server starting. Checking for registry conflicts...");

        detectConflicts(ForgeRegistries.BIOMES, biomeRegistry, "Biome");
        detectConflicts(ForgeRegistries.DIMENSIONS, dimensionRegistry, "Dimension");
        detectConflicts(ForgeRegistries.BLOCKS, blockRegistry, "Block");
        detectConflicts(ForgeRegistries.ITEMS, itemRegistry, "Item");
        detectConflicts(ForgeRegistries.ENTITIES, entityRegistry, "Entity");
    }

    private static <T> void detectConflicts(ForgeRegistries<T> registry, Map<ResourceLocation, String> trackedRegistry, String type) {
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