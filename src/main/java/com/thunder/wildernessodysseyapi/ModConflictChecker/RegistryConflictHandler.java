package com.thunder.wildernessodysseyapi.ModConflictChecker;

import com.thunder.wildernessodysseyapi.ModConflictChecker.Util.LoggerUtil;
import com.thunder.wildernessodysseyapi.ModConflictChecker.Util.LoggerUtil.ConflictSeverity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber
/****
 * RegistryConflictHandler for the Wilderness Odyssey API mod.
 */
public class RegistryConflictHandler {

    // Track the original sources of registered items
    private static final Map<ResourceLocation, String> structureRegistry = new HashMap<>();
    private static final Map<ResourceLocation, String> poiRegistry = new HashMap<>();
    private static final Map<ResourceLocation, String> biomeRegistry = new HashMap<>();
    private static final Map<ResourceLocation, String> recipeRegistry = new HashMap<>();

    @SubscribeEvent
    public static void onServerStart(ServerStartingEvent event) {
        LoggerUtil.log(ConflictSeverity.INFO, "Server starting. Checking for registry conflicts...");

        MinecraftServer server = event.getServer();

        // Fetch registry access
        var registryAccess = server.registryAccess();

        // Check for conflicts in structures, POIs, and biomes
        checkRegistryConflicts(registryAccess.registryOrThrow(Registries.STRUCTURE), structureRegistry, "Structure");
        checkRegistryConflicts(registryAccess.registryOrThrow(Registries.POINT_OF_INTEREST_TYPE), poiRegistry, "POI");
        checkRegistryConflicts(registryAccess.registryOrThrow(Registries.BIOME), biomeRegistry, "Biome");

        // Check for crafting recipe conflicts
        checkRecipeConflicts(server);

        ShaderConflictChecker.scanForConflicts();

    }

    /**
     * Detects and logs conflicts in a specific registry.
     *
     * @param registry        The registry to check.
     * @param trackedRegistry A map tracking registered items and their sources.
     * @param type            The type of registry (e.g., "Structure", "POI", "Biome").
     * @param <T>             The type of elements in the registry.
     */
    private static <T> void checkRegistryConflicts(Registry<T> registry, Map<ResourceLocation, String> trackedRegistry, String type) {
        registry.keySet().forEach(key -> {
            String modSource = key.getNamespace();
            if (trackedRegistry.containsKey(key)) {
                String originalMod = trackedRegistry.get(key);

                // Log conflict if detected
                if (!originalMod.equals(modSource)) {
                    LoggerUtil.log(ConflictSeverity.ERROR, String.format(
                            "Conflict detected: %s '%s' was originally registered by '%s' but has been overwritten by '%s'.",
                            type, key, originalMod, modSource));
                }
            } else {
                // Log successful registration
                trackedRegistry.put(key, modSource);
                LoggerUtil.log(ConflictSeverity.INFO, String.format(
                        "%s '%s' registered by '%s'.", type, key, modSource));
            }
        });
    }

    /**
     * Detects and logs conflicts in crafting recipes.
     *
     * @param server The Minecraft server instance.
     */
    private static void checkRecipeConflicts(MinecraftServer server) {
        LoggerUtil.log(ConflictSeverity.INFO, "Checking for crafting recipe conflicts...");

        server.getRecipeManager().getRecipes().forEach(recipeHolder -> {
            ResourceLocation recipeKey = recipeHolder.id(); // Use 'id()' instead of 'getId()'
            String modSource = recipeKey.getNamespace();

            if (recipeRegistry.containsKey(recipeKey)) {
                String originalMod = recipeRegistry.get(recipeKey);

                // Log conflict if detected
                if (!originalMod.equals(modSource)) {
                    LoggerUtil.log(ConflictSeverity.ERROR, String.format(
                            "Conflict detected: Recipe '%s' was originally registered by '%s' but has been overwritten by '%s'.",
                            recipeKey, originalMod, modSource));
                }
            } else {
                // Log successful registration
                recipeRegistry.put(recipeKey, modSource);
                LoggerUtil.log(ConflictSeverity.INFO, String.format(
                        "Recipe '%s' registered by '%s'.", recipeKey, modSource));
            }
        });
    }
}
