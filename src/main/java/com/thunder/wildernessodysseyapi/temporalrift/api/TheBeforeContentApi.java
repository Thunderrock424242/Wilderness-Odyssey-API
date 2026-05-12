package com.thunder.wildernessodysseyapi.temporalrift.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class TheBeforeContentApi {
    private static final Set<ResourceLocation> EXCLUDED_BIOMES = new LinkedHashSet<>();
    private static final Set<ResourceLocation> EXCLUDED_MOBS = new LinkedHashSet<>();
    private static final Set<ResourceLocation> ALLOWED_MOBS = new LinkedHashSet<>();
    private static final Set<ResourceLocation> EXCLUDED_STRUCTURES = new LinkedHashSet<>();
    private static final Set<ResourceLocation> ALLOWED_STRUCTURES = new LinkedHashSet<>();

    private TheBeforeContentApi() {
    }

    public static void excludeBiome(ResourceLocation biomeId) {
        if (biomeId != null) {
            EXCLUDED_BIOMES.add(biomeId);
        }
    }

    public static boolean isBiomeExcluded(ResourceLocation biomeId) {
        return biomeId != null && EXCLUDED_BIOMES.contains(biomeId);
    }

    public static Set<ResourceLocation> excludedBiomes() {
        return Collections.unmodifiableSet(EXCLUDED_BIOMES);
    }

    public static void excludeMob(ResourceLocation mobId) {
        if (mobId != null) {
            EXCLUDED_MOBS.add(mobId);
        }
    }

    public static void allowMob(ResourceLocation mobId) {
        if (mobId != null) {
            ALLOWED_MOBS.add(mobId);
        }
    }

    public static boolean isMobAllowed(ResourceLocation mobId) {
        return mobId != null && ALLOWED_MOBS.contains(mobId) && !EXCLUDED_MOBS.contains(mobId);
    }

    public static void excludeMob(EntityType<?> entityType) {
        excludeMob(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entityType));
    }

    public static void allowMob(EntityType<?> entityType) {
        allowMob(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entityType));
    }

    public static Set<ResourceLocation> excludedMobs() {
        return Collections.unmodifiableSet(EXCLUDED_MOBS);
    }

    public static Set<ResourceLocation> allowedMobs() {
        return Collections.unmodifiableSet(ALLOWED_MOBS);
    }

    public static void excludeStructure(ResourceLocation structureId) {
        if (structureId != null) {
            EXCLUDED_STRUCTURES.add(structureId);
        }
    }

    public static void allowStructure(ResourceLocation structureId) {
        if (structureId != null) {
            ALLOWED_STRUCTURES.add(structureId);
        }
    }

    public static boolean isStructureAllowed(ResourceLocation structureId) {
        return structureId != null && ALLOWED_STRUCTURES.contains(structureId) && !EXCLUDED_STRUCTURES.contains(structureId);
    }

    public static boolean isStructureAllowed(StructureSet structureSet) {
        return structureSet != null && ALLOWED_STRUCTURES.stream().anyMatch(id -> id.getPath().equals(structureSet.toString()));
    }

    public static Set<ResourceLocation> excludedStructures() {
        return Collections.unmodifiableSet(EXCLUDED_STRUCTURES);
    }

    public static Set<ResourceLocation> allowedStructures() {
        return Collections.unmodifiableSet(ALLOWED_STRUCTURES);
    }
}
