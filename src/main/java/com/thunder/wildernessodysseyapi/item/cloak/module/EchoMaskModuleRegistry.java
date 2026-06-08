package com.thunder.wildernessodysseyapi.item.cloak.module;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class EchoMaskModuleRegistry {
    private static final Map<ResourceLocation, EchoMaskModule> MODULES = new LinkedHashMap<>();

    private EchoMaskModuleRegistry() {
    }

    public static EchoMaskModule register(EchoMaskModule module) {
        EchoMaskModule previous = MODULES.putIfAbsent(module.id(), module);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate Echo Breathing Mask module id: " + module.id());
        }
        return module;
    }

    public static Optional<EchoMaskModule> get(ResourceLocation id) {
        return Optional.ofNullable(MODULES.get(id));
    }

    public static Collection<EchoMaskModule> all() {
        return MODULES.values();
    }

    public static boolean isRegistered(ResourceLocation id) {
        return MODULES.containsKey(id);
    }
}
