package com.thunder.wildernessodysseyapi.biome;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biomes;

public class ModBiomeModifiers {
    public static final DeferredRegister<BiomeModifier> BIOME_MODIFIERS = DeferredRegister.create(
            ForgeRegistries.Keys.BIOME_MODIFIERS,
            "wildernessodyssey"
    );

    static {
        BIOME_MODIFIERS.register("custom_modifier", () ->
                new CustomBiomeModifier(
                        ResourceKey.create(ForgeRegistries.Keys.PLACED_FEATURES, new ResourceLocation("wildernessodyssey", "custom_structure")),
                        Biomes.PLAINS,
                        500 // Minimum Plains biome size in blocks
                )
        );
    }
}
