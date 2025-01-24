package com.thunder.wildernessodysseyapi.ModConflictChecker;

import net.minecraft.resources.ResourceLocation;

public class Utils {
    public static boolean isValidResourceLocation(String name) {
        ResourceLocation key = ResourceLocation.tryParse(name);
        if (key == null) {
            ModConflictChecker.LOGGER.error("Invalid ResourceLocation: '{}'", name);
            return false;
        }
        return true;
    }
}