package com.thunder.wildernessodysseyapi.ModConflictChecker.Util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;

/**
 * The type Utils.
 */
public class Utils {

    /**
     * Is valid resource location boolean.
     *
     * @param name the name
     * @return the boolean
     */
    public static boolean isValidResourceLocation(String name) {
        ResourceLocation key = ResourceLocation.tryParse(name);
        if (key == null) {
            LOGGER.error(Component.translatable("log.wildernessodysseyapi.invalid_resource_location", name).getString());
            return false;
        }
        return true;
    }

    /**
     * Gets mod namespace.
     *
     * @param key the key
     * @return the mod namespace
     */
    public static String getModNamespace(ResourceLocation key) {
        return key.getNamespace();
    }

    /**
     * Is conflict detected boolean.
     *
     * @param key             the key
     * @param modSource       the mod source
     * @param trackedRegistry the tracked registry
     * @return the boolean
     */
    public static boolean isConflictDetected(ResourceLocation key, String modSource, java.util.Map<ResourceLocation, String> trackedRegistry) {
        return trackedRegistry.containsKey(key) && !trackedRegistry.get(key).equals(modSource);
    }
}
