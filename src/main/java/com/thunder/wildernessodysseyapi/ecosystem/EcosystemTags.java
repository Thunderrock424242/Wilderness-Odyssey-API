package com.thunder.wildernessodysseyapi.ecosystem;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

/** Data-pack tag keys used by the built-in ecosystem profiles and services. */
public final class EcosystemTags {

    /** Resource ID for the configurable predator entity-type tag. */
    public static final ResourceLocation PREDATORS_ID = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID, "ecosystem/predators");
    /** Resource ID for the generic prey population tag. */
    public static final ResourceLocation PREY_ID = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID, "ecosystem/prey");
    /** Resource ID for the default wolf prey population tag. */
    public static final ResourceLocation WOLF_PREY_ID = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID, "ecosystem/wolf_prey");

    /** Blocks that indicate locally available forage for the initial herbivore model. */
    public static final TagKey<Block> FORAGE_BLOCKS = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "ecosystem/forage_blocks")
    );

    /** Built-in threat selector that pack authors may extend with modded predators. */
    public static final TagKey<EntityType<?>> PREDATORS = TagKey.create(
            Registries.ENTITY_TYPE,
            PREDATORS_ID
    );

    /** Generic prey selector used by configured predator archetypes. */
    public static final TagKey<EntityType<?>> PREY = TagKey.create(
            Registries.ENTITY_TYPE,
            PREY_ID
    );

    /** Built-in wolf prey population that pack authors may extend or replace. */
    public static final TagKey<EntityType<?>> WOLF_PREY = TagKey.create(
            Registries.ENTITY_TYPE,
            WOLF_PREY_ID
    );

    private EcosystemTags() {
    }
}
