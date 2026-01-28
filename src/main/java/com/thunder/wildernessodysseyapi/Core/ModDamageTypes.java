package com.thunder.wildernessodysseyapi.Core;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

public final class ModDamageTypes {
    public static final ResourceKey<DamageType> NEURAL_FRAME_REMOVAL = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "neural_frame_removal")
    );

    private ModDamageTypes() {
    }
}
