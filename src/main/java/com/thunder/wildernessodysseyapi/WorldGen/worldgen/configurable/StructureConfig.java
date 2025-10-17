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

    /** Minimum chunk distance between bunker spawns */
    public static final ModConfigSpec.IntValue BUNKER_MIN_DISTANCE;
    /** Maximum bunkers allowed in a world */
    public static final ModConfigSpec.IntValue BUNKER_MAX_COUNT;
    /** Debug toggle to bypass cryo tube spawning */
    public static final ModConfigSpec.BooleanValue DEBUG_IGNORE_CRYO_TUBE;

    private static final HashMap<String, BooleanValue> STRUCTURES = new HashMap<>();
    private static final HashMap<String, BooleanValue> POIS = new HashMap<>();
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("bunker");
        BUNKER_MIN_DISTANCE = BUILDER.comment("Minimum distance in chunks between bunkers")
                .defineInRange("spawnDistanceChunks", 32, 1, Integer.MAX_VALUE);
        BUNKER_MAX_COUNT = BUILDER.comment("Maximum number of bunkers generated per world")
                .defineInRange("maxSpawnCount", 1, 0, Integer.MAX_VALUE);
        DEBUG_IGNORE_CRYO_TUBE = BUILDER.comment(
                        "If true, players spawn at a random position inside the bunker instead of inside cryo tubes."
                )
                .define("debugIgnoreCryoTubeSpawns", false);
        BUILDER.pop();

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
