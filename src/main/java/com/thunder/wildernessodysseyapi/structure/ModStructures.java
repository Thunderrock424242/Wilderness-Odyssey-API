package com.thunder.wildernessodysseyapi.structure;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class ModStructures {
    public static final StructureFeature<NoneFeatureConfiguration> CUSTOM_STRUCTURE =
            new CustomStructure(NoneFeatureConfiguration.CODEC);

    public static void registerStructures() {
        Registry.register(
                Registry.STRUCTURE_FEATURE,
                ResourceLocation.of("wildernessodyssey:custom_structure"),
                CUSTOM_STRUCTURE
        );
    }
}
