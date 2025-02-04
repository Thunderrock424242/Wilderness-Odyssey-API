package com.thunder.wildernessodysseyapi.BunkerStructure.biome;

import com.thunder.wildernessodysseyapi.BunkerStructure.Features.ModFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Objects;

/**
 * The type Mod biome modifiers.
 */
public class ModBiomeModifiers {
    /**
     * The constant BIOME_MODIFIERS.
     */
// Create the DeferredRegister for Biome Modifiers
    public static final DeferredRegister<BiomeModifier> BIOME_MODIFIERS = DeferredRegister.create(
            ResourceKey.createRegistryKey(
                    Objects.requireNonNull(ResourceLocation.tryParse("neoforge:biome_modifier"))
            ),
            "wildernessodyssey"
    );

    // Register your custom BiomeModifier
    static {
        BIOME_MODIFIERS.register(
                "custom_modifier",
                () -> new CustomBiomeModifier(
                        ModFeatures.CUSTOM_STRUCTURE_PLACED_KEY, // Reference PlacedFeature key
                        Biomes.PLAINS,                           // Target biome
                        500                                      // Minimum Plains biome size in blocks
                )
        );
    }
}
