package com.thunder.wildernessodysseyapi.ModConflictChecker.Util;

import net.minecraft.resources.ResourceLocation;

import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.LOGGER;

public class Utils {
    public static boolean isValidResourceLocation(String name) {
        ResourceLocation key = ResourceLocation.tryParse(name);
        if (key == null) {
            LOGGER.error("Invalid ResourceLocation: '{}'", name);
            return false;
        }
        return true;
    }
}