package com.thunder.wildernessodysseyapi.cloak.item.module;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public record EchoMaskModule(
        ResourceLocation id,
        Component displayName,
        int slotCost,
        EchoMaskModuleModifiers modifiers
) {
    public EchoMaskModule {
        if (slotCost < 1) {
            throw new IllegalArgumentException("Echo mask module slot cost must be at least 1");
        }
        if (modifiers == null) {
            modifiers = EchoMaskModuleModifiers.NONE;
        }
    }
}
