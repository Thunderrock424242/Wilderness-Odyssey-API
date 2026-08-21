package com.thunder.wildernessodysseyapi.cloak.item.module;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;

/**
 * Extension-only registry for integrations that supply their own mask modules and install UI.
 *
 * <p>The base mod currently registers no built-in modules and does not expose a player-facing
 * install/remove interaction. Registering a module here alone never mutates a mask.</p>
 */
public final class EchoMaskModuleRegistry {
    private static final Map<ResourceLocation, EchoMaskModule> MODULES = new LinkedHashMap<>();

    private EchoMaskModuleRegistry() {
    }

    /** Registers one integration-owned module definition. */
    public static EchoMaskModule register(EchoMaskModule module) {
        EchoMaskModule previous = MODULES.putIfAbsent(module.id(), module);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate Echo Breathing Mask module id: " + module.id());
        }
        return module;
    }

    /** Resolves a registered extension module by ID. */
    public static Optional<EchoMaskModule> get(ResourceLocation id) {
        return Optional.ofNullable(MODULES.get(id));
    }

    /** Returns an immutable snapshot of registered extension modules. */
    public static Collection<EchoMaskModule> all() {
        return List.copyOf(MODULES.values());
    }

    /** Returns whether an extension registered the supplied module ID. */
    public static boolean isRegistered(ResourceLocation id) {
        return MODULES.containsKey(id);
    }
}
