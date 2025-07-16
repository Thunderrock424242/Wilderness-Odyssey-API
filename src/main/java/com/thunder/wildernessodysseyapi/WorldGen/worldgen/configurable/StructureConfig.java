package com.thunder.wildernessodysseyapi.WorldGen.worldgen.configurable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;

import java.util.HashMap;

/****
 * StructureConfig for the Wilderness Odyssey API mod.
 */
public class StructureConfig {
    public static final ModConfigSpec CONFIG_SPEC;
    private static final HashMap<String, BooleanValue> STRUCTURES = new HashMap<>();
    private static final HashMap<String, BooleanValue> POIS = new HashMap<>();
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        registerAll();
        CONFIG_SPEC = BUILDER.build();
    }

    private static void registerAll() {
        // Structures
        BuiltInRegistries.STRUCTURE_TYPE.entrySet().forEach(entry -> {
            ResourceLocation id = entry.getKey().location(); // Fixed: use .location()
            String modId = id.getNamespace();
            String structureName = id.getPath();

            BUILDER.push(modId);
            BUILDER.push("structures");
            STRUCTURES.put(id.toString(), BUILDER.define(structureName, true));
            BUILDER.pop(2);
        });

        // POIs
        BuiltInRegistries.POINT_OF_INTEREST_TYPE.entrySet().forEach(entry -> {
            ResourceLocation id = entry.getKey().location(); // Fixed: use .location()
            String modId = id.getNamespace();
            String poiName = id.getPath();

            BUILDER.push(modId);
            BUILDER.push("pois");
            POIS.put(id.toString(), BUILDER.define(poiName, true));
            BUILDER.pop(2);
        });
    }

    public static boolean isStructureEnabled(ResourceLocation id) {
        BooleanValue value = STRUCTURES.get(id.toString());
        return value == null ? true : value.get(); // Fixed: No lambda, simple null-check
    }

    public static boolean isPOIEnabled(ResourceLocation id) {
        BooleanValue value = POIS.get(id.toString());
        return value == null ? true : value.get(); // Fixed: No lambda, simple null-check
    }
}
