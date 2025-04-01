package com.thunder.wildernessodysseyapi.worldgen.features;

import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(NeoForgeRegistries.FEATURES, "yourmodid");

    public static final DeferredHolder<Feature<?>, MeteorCraterFeature> METEOR_CRATER =
            FEATURES.register("meteor_crater", MeteorCraterFeature::new);
}