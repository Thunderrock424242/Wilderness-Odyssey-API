package com.thunder.wildernessodysseyapi.biome;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBiomeModifiers {
    // Create the DeferredRegister for Biome Modifiers
    public static final DeferredRegister<BiomeModifier> BIOME_MODIFIERS = DeferredRegister.create(
            ForgeRegistries.Keys.BIOME_MODIFIERS,
            "wildernessodyssey"
    );

    static {
        BIOME_MODIFIERS.register("custom_modifier", () ->
                new CustomBiomeModifier(
                        ResourceKey.create(
                                ForgeRegistries.Keys.PLACED_FEATURES,
                                ResourceLocation.tryParse("wildernessodyssey:custom_structure")
                        ),
                        Biomes.PLAINS,
                        500 // Minimum Plains biome size in blocks not chunks.
                )
        );
    }
}
