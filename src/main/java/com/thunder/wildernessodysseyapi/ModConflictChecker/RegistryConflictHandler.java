package com.thunder.wildernessodysseyapi.ModConflictChecker;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashMap;
import java.util.Map;

import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.LOGGER;

@EventBusSubscriber
public class RegistryConflictHandler {

    // Track the original sources of registered structures, POIs, and biomes
    private static final Map<ResourceLocation, String> structureRegistry = new HashMap<>();
    private static final Map<ResourceLocation, String> poiRegistry = new HashMap<>();
    private static final Map<ResourceLocation, String> biomeRegistry = new HashMap<>();

    @SubscribeEvent
    public static void onServerStart(ServerStartingEvent event) {
        LOGGER.info("Server starting. Checking for registry conflicts...");

        // Check for conflicts in structures, POIs, and biomes
        detectConflicts(NeoForgeRegistries.STRUCTURES.getKeys(), structureRegistry, "Structure");
        detectConflicts(NeoForgeRegistries.POI_TYPES.getKeys(), poiRegistry, "POI");
        detectConflicts(NeoForgeRegistries.BIOMES.getKeys(), biomeRegistry, "Biome");
    }

    /**
     * Detects conflicts in a specific registry and logs them.
     *
     * @param keys            The keys from the registry (ResourceLocation set).
     * @param trackedRegistry A map tracking registered items and their sources.
     * @param type            The type of registry (e.g., "Structure", "POI", "Biome").
     */
    private static void detectConflicts(Iterable<ResourceLocation> keys, Map<ResourceLocation, String> trackedRegistry, String type) {
        for (ResourceLocation key : keys) {
            String modSource = key.getNamespace();
            if (trackedRegistry.containsKey(key)) {
                String originalMod = trackedRegistry.get(key);

                // Log the conflict if the ID is already registered
                if (!originalMod.equals(modSource)) {
                    LOGGER.error("Conflict detected: {} '{}' was originally registered by '{}' but has been overwritten by '{}'.",
                            type, key, originalMod, modSource);
                }
            } else {
                // Record the source of the registration
                trackedRegistry.put(key, modSource);
                LOGGER.info("{} '{}' registered by '{}'.", type, key, modSource);
            }
        }
    }
}
