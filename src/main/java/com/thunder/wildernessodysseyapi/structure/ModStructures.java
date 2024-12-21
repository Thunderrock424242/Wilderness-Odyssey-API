package com.thunder.wildernessodysseyapi.structure;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class ModStructures {
    public static final StructureFeature<NoneFeatureConfiguration> CUSTOM_STRUCTURE =
            new CustomStructure(NoneFeatureConfiguration.CODEC);

    public static final RegistryObject<ConfiguredStructureFeature<?, ?>> CONFIGURED_CUSTOM_STRUCTURE =
            Registry.register(
                    ForgeRegistries.CONFIGURED_STRUCTURE_FEATURES,
                    new ResourceLocation("wildernessodyssey", "custom_structure"),
                    CUSTOM_STRUCTURE.configured(NoneFeatureConfiguration.INSTANCE)
            );

    public static void registerStructures() {
        Registry.register(
                ForgeRegistries.STRUCTURE_FEATURES,
                new ResourceLocation("wildernessodyssey", "custom_structure"),
                CUSTOM_STRUCTURE
        );
    }
}
