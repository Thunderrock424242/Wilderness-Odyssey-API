package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.Features;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Central worldgen registry for bunker placement.
 */
public final class ModFeatures {
    /** Registry for bunker feature. */
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(
            Registries.FEATURE,
            ModConstants.MOD_ID
    );

    /** Registry for configured features. */
    public static final DeferredRegister<ConfiguredFeature<?, ?>> CONFIGURED_FEATURES = DeferredRegister.create(
            Registries.CONFIGURED_FEATURE,
            ModConstants.MOD_ID
    );

    /** Registry for placed features. */
    public static final DeferredRegister<PlacedFeature> PLACED_FEATURES = DeferredRegister.create(
            Registries.PLACED_FEATURE,
            ModConstants.MOD_ID
    );

    /** Custom bunker feature that handles spawn tracking and cryo setup. */
    public static final DeferredHolder<Feature<?>, BunkerFeature> BUNKER_FEATURE = FEATURES.register(
            "bunker",
            BunkerFeature::new
    );

    /** Configured bunker feature with no additional configuration. */
    public static final DeferredHolder<ConfiguredFeature<?, ?>, ConfiguredFeature<NoneFeatureConfiguration, ?>> BUNKER_CONFIGURED =
            CONFIGURED_FEATURES.register(
                    "bunker",
                    () -> new ConfiguredFeature<>(BUNKER_FEATURE.get(), NoneFeatureConfiguration.INSTANCE)
            );

    /** Bunker placed feature using surface heightmap placement and a light rarity gate. */
    public static final DeferredHolder<PlacedFeature, PlacedFeature> BUNKER_PLACED = PLACED_FEATURES.register(
            "bunker",
            () -> new PlacedFeature(
                    Holder.direct(BUNKER_CONFIGURED.get()),
                    List.<PlacementModifier>of(
                            RarityFilter.onAverageOnceEvery(48),
                            InSquarePlacement.spread(),
                            PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
                            BiomeFilter.biome()
                    )
            )
    );

    /** Resource key for the bunker placed feature. */
    public static final ResourceKey<PlacedFeature> BUNKER_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE,
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "bunker")
    );

    private ModFeatures() {
    }
}
