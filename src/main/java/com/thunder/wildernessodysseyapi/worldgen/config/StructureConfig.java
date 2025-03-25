package com.thunder.wildernessodysseyapi.worldgen.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashMap;

public class StructureConfig {
    public static final ModConfigSpec CONFIG_SPEC;
    private static final HashMap<String, ModConfigSpec.BooleanValue> STRUCTURES = new HashMap<>();
    private static final HashMap<String, ModConfigSpec.BooleanValue> POIS = new HashMap<>();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        // Structures
        builder.comment("Automatically registered structures").push("structures");
        BuiltInRegistries.STRUCTURE.keySet().forEach(structureID -> {
            String id = structureID.toString().replace(':', '_');
            STRUCTURES.put(id, builder.define(id, true));
        });
        builder.pop();

        // POIs
        builder.comment("Automatically registered POIs").push("pois");
        BuiltInRegistries.POINT_OF_INTEREST_TYPE.keySet().forEach(poiID -> {
            String id = poiID.toString().replace(':', '_');
            POIS.put(id, builder.define(id, true));
        });
        builder.pop();

        CONFIG_SPEC = builder.build();
    }

    private static final HashMap<String, ModConfigSpec.BooleanValue> POIS = new HashMap<>();
    public static final ModConfigSpec MOD_CONFIG_SPEC;

    public static boolean isStructureEnabled(ResourceLocation id) {
        return STRUCTURES.getOrDefault(id.toString().replace(':', '_'), () -> true).get();
    }

    public static boolean isPOIEnabled(ResourceLocation id) {
        return POIS.getOrDefault(id.toString().replace(':', '_'), () -> true).get();
    }
}