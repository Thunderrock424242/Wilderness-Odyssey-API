package com.thunder.wildernessodysseyapi.WorldGen.worldgen.features;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

/****
 * ModFeatures for the Wilderness Odyssey API mod.
 */
public class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MOD_ID);

    public static final DeferredHolder<Feature<?>, MeteorCraterFeature> METEOR_CRATER =
            FEATURES.register("meteor_crater", MeteorCraterFeature::new);
}