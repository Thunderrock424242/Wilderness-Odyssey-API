package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/**
 * Data-driven structure registration helpers.
 * <p>
 * Minecraft 1.21+ loads structures entirely from data packs, so the bunker and impact zone
 * structures are registered via JSON in {@code data/wildernessodysseyapi/worldgen/structure}
 * and {@code data/wildernessodysseyapi/worldgen/structure_set}. No {@code DeferredRegister}
 * or code-side registration is required when using the built-in {@code minecraft:jigsaw}
 * structure type.
 */
public final class ModStructures {
    /** Mod id used by the structure and structure set JSON files. */
    public static final String MOD_ID = "wildernessodysseyapi";

    /** Resource key for the bunker structure (data defined). */
    public static final ResourceKey<Structure> BUNKER = ResourceKey.create(
            Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "bunker")
    );

    /** Resource key for the bunker structure set (data defined). */
    public static final ResourceKey<StructureSet> BUNKER_SET = ResourceKey.create(
            Registries.STRUCTURE_SET,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "bunker")
    );

    /** Resource key for the impact zone structure (data defined). */
    public static final ResourceKey<Structure> IMPACT_ZONE = ResourceKey.create(
            Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "impact_zone")
    );

    /** Resource key for the impact zone structure set (data defined). */
    public static final ResourceKey<StructureSet> IMPACT_ZONE_SET = ResourceKey.create(
            Registries.STRUCTURE_SET,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "impact_zone")
    );

    private ModStructures() {
    }
}
