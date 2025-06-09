package com.thunder.wildernessodysseyapi.WorldGenClasses_and_packages.worldgen.features;

import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

public class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(NeoForgeRegistries.FEATURES, MOD_ID);

    public static final DeferredHolder<Feature<?>, MeteorCraterFeature> METEOR_CRATER =
            FEATURES.register("meteor_crater", MeteorCraterFeature::new);
}