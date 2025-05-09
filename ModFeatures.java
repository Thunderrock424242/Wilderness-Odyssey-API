package com.thunder.wildernessodysseyapi.worldgen.features;

import com.thunder.wildernessodysseyapi.WildernessOdysseyAPIMainModClass;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, WildernessOdysseyAPIMainModClass.MOD_ID);

    public static final DeferredHolder<Feature<?>, MeteorCraterFeature> METEOR_CRATER =
            FEATURES.register("meteor_crater", MeteorCraterFeature::new);
}