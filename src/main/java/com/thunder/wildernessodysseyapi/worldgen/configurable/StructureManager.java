package com.thunder.wildernessodysseyapi.worldgen.configurable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public class StructureManager {

    public static void autoRegisterStructures() {
        for (ResourceLocation structureID : BuiltInRegistries.STRUCTURE.keySet()) {
            // Register each structure found in the game's built-in registry
            String id = structureID.toString().replace(':', '_');
            StructureConfig.registerStructure(id, true);
        }
    }

    public static void autoRegisterPOIs() {
        for (ResourceLocation poiID : BuiltInRegistries.POINT_OF_INTEREST_TYPE.keySet()) {
            // Register each POI found in the built-in registry
            String id = poiID.toString().replace(':', '_');
            StructureConfig.registerPOI(id, true);
        }
    }
}