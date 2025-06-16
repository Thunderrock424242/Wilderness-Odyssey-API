package com.thunder.wildernessodysseyapi.WorldGen.worldgen.features;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.RarityFilter;

import java.util.List;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

public class anomolyzonePlacedFeature {
    public static final ResourceKey<PlacedFeature> METEOR_CRATER_PLACED =
            ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.tryBuild(MOD_ID, "meteor_crater"));

    /**
     * This is NEVER directly "new’d" by you. Instead, in data‐gen or a data (JSON) file you register
     * a ConfiguredFeature for METEOR_CRATER (pointing to ModFeatures.METEOR_CRATER),
     * then you register a PlacedFeature under the same key. At runtime, that PlacedFeature appears
     * in the registry, and you can look it up via context.lookup(Registries.PLACED_FEATURE).
     *
     * Below is a helper that *pretends* we already registered a ConfiguredFeature<NoneFeatureConfiguration>
     * for "yourmodid:meteor_crater" in a data file.
     * If you are not using data‐gen, you would need to register a ConfiguredFeature programmatically first.
     *
     * For the sake of compilation, we show how to create a PlacedFeature object in code:
     */
    public static final PlacedFeature METEOR_CRATER_FEATURE = new PlacedFeature(
            (Holder<ConfiguredFeature<?, ?>>) ModFeatures.METEOR_CRATER.get(),
            List.of(
                    // Rarity: on average once every 9,999 chunks (effectively “once per world” if world is < ~10000 chunks)
                    RarityFilter.onAverageOnceEvery(9999),
                    InSquarePlacement.spread(),
                    PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
                    BiomeFilter.biome()
            )
    );

    /**
     * When the game starts, you must register METEOR_CRATER_FEATURE into
     * BuiltinRegistries.PLACED_FEATURE under the key METEOR_CRATER_PLACED.
     *
     * If you’re using a data pack for placement, that data pack JSON should register a PlacedFeature
     * with key "yourmodid:meteor_crater" that points to the same underlying ConfiguredFeature.
     *
     * If you want to do it by code, you can put in your main mod class’s setup:
     *
     *     Registry.register(
     *         BuiltinRegistries.PLACED_FEATURE,
     *         METEOR_CRATER_PLACED.location(),
     *         METEOR_CRATER_FEATURE
     *     );
     *
     * …so that at runtime `context.lookup(Registries.PLACED_FEATURE).getOrThrow(METEOR_CRATER_PLACED)` works.
     */
}

