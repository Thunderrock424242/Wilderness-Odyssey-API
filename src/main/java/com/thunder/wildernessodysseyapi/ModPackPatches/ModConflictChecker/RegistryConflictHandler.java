package com.thunder.wildernessodysseyapi.ModPackPatches.ModConflictChecker;

import com.thunder.wildernessodysseyapi.ModPackPatches.ModConflictChecker.Util.LoggerUtil;
import com.thunder.wildernessodysseyapi.ModPackPatches.ModConflictChecker.Util.LoggerUtil.ConflictSeverity;
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
    private static final Map<ResourceLocation, String> biomeRegistry = new HashMap<>();
    private static final Map<ResourceLocation, String> recipeRegistry = new HashMap<>();

    @SubscribeEvent
    public static void onServerStart(ServerStartingEvent event) {
        LoggerUtil.log(ConflictSeverity.INFO, "Server starting. Checking for registry conflicts...", false);

        MinecraftServer server = event.getServer();

        DedicatedConflictDetector.start();

        // Fetch registry access
        var registryAccess = server.registryAccess();

        // Check for conflicts in structures, POIs, and biomes
        StructureConflictChecker.checkStructureConflicts(registryAccess.registryOrThrow(Registries.STRUCTURE));
        StructureConflictChecker.checkPoiConflicts(registryAccess.registryOrThrow(Registries.POINT_OF_INTEREST_TYPE));
        checkRegistryConflicts(registryAccess.registryOrThrow(Registries.BIOME));

        // Check for crafting recipe conflicts
        checkRecipeConflicts(server);

        ShaderConflictChecker.scanForConflictsAsync();

    }

    /**
     * Detects and logs conflicts in a specific registry.
     *
     * @param <T>      The type of elements in the registry.
     * @param registry The registry to check.
     */
    private static <T> void checkRegistryConflicts(Registry<T> registry) {
        registry.keySet().forEach(key -> {
            String modSource = key.getNamespace();
            if (RegistryConflictHandler.biomeRegistry.containsKey(key)) {
                String originalMod = RegistryConflictHandler.biomeRegistry.get(key);

                // Log conflict if detected
                if (!originalMod.equals(modSource)) {
                    LoggerUtil.log(ConflictSeverity.ERROR, String.format(
                            "Conflict detected: %s '%s' was originally registered by '%s' but has been overwritten by '%s'.",
                            "Biome", key, originalMod, modSource), false);
                }
            } else {
                // Log successful registration
                RegistryConflictHandler.biomeRegistry.put(key, modSource);
                LoggerUtil.log(ConflictSeverity.INFO, String.format(
                        "%s '%s' registered by '%s'.", "Biome", key, modSource), false);
            }
        });
    }

    /**
     * Detects and logs conflicts in crafting recipes.
     *
     * @param server The Minecraft server instance.
     */
    private static void checkRecipeConflicts(MinecraftServer server) {
        LoggerUtil.log(ConflictSeverity.INFO, "Checking for crafting recipe conflicts...", false);

        server.getRecipeManager().getRecipes().forEach(recipeHolder -> {
            ResourceLocation recipeKey = recipeHolder.id(); // Use 'id()' instead of 'getId()'
            String modSource = recipeKey.getNamespace();

            if (recipeRegistry.containsKey(recipeKey)) {
                String originalMod = recipeRegistry.get(recipeKey);

                // Log conflict if detected
                if (!originalMod.equals(modSource)) {
                        LoggerUtil.log(ConflictSeverity.ERROR, String.format(
                                "Conflict detected: Recipe '%s' was originally registered by '%s' but has been overwritten by '%s'.",
                                recipeKey, originalMod, modSource), false);
                }
            } else {
                // Log successful registration
                recipeRegistry.put(recipeKey, modSource);
                LoggerUtil.log(ConflictSeverity.INFO, String.format(
                        "Recipe '%s' registered by '%s'.", recipeKey, modSource), false);
            }
        });
    }
}
