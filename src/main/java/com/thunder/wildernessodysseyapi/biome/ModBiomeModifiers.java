package com.thunder.wildernessodysseyapi.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

public class ModBiomeModifiers {
    public static final Codec<BiomeModifiers> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ConfiguredStructureFeature.CODEC.fieldOf("structure").forGetter(BiomeModifiers::getStructure)
            ).apply(instance, BiomeModifiers::new)
    );

    static {
        Registry.register(
                ForgeRegistries.BIOME_MODIFIER_SERIALIZERS,
                new ResourceLocation("wildernessodyssey", "structure_biome_modifier"),
                CODEC
        );
    }
}
