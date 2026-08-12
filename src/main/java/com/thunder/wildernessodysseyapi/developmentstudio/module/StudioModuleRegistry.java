package com.thunder.wildernessodysseyapi.developmentstudio.module;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Internal extension registry for Development Studio categories. */
public final class StudioModuleRegistry {
    private static final String SCREEN_PREFIX = "screen.wildernessodysseyapi.studio.";
    private static final Map<ResourceLocation, StudioModule> MODULES = new LinkedHashMap<>();
    private static boolean bootstrapped;

    private StudioModuleRegistry() {
    }

    /** Registers Phase 1 modules and visible integration points for later phases. */
    public static synchronized void bootstrapDefaults() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;

        register(module("world", StudioModuleStatus.AVAILABLE, "module.world"));
        register(module("locations", StudioModuleStatus.AVAILABLE, "module.locations"));
        register(module("structures", StudioModuleStatus.DEFERRED, "module.deferred"));
        register(module("entities", StudioModuleStatus.DEFERRED, "module.deferred"));
        register(module("ecosystem", StudioModuleStatus.DEFERRED, "module.deferred"));
        register(module("weather", StudioModuleStatus.DEFERRED, "module.deferred"));
        register(module("water", StudioModuleStatus.DEFERRED, "module.deferred"));
        register(module("worldgen", StudioModuleStatus.DEFERRED, "module.deferred"));
        register(module("lighting", StudioModuleStatus.DEFERRED, "module.deferred"));
        register(module("power", StudioModuleStatus.DEFERRED, "module.deferred"));
        register(module("security", StudioModuleStatus.DEFERRED, "module.deferred"));
        register(module("aether", StudioModuleStatus.DEFERRED, "module.deferred"));
        register(module("performance", StudioModuleStatus.DEFERRED, "module.deferred"));
        register(module("scenarios", StudioModuleStatus.DEFERRED, "module.deferred"));
        register(module("inspector", StudioModuleStatus.AVAILABLE, "module.inspector"));
        register(module("debug", StudioModuleStatus.FOUNDATION, "module.debug"));
    }

    /** Registers one unique module definition. */
    public static synchronized void register(StudioModule module) {
        StudioModule previous = MODULES.putIfAbsent(module.id(), module);
        if (previous != null) {
            throw new IllegalStateException("Duplicate Studio module id: " + module.id());
        }
    }

    public static Optional<StudioModule> get(ResourceLocation id) {
        bootstrapDefaults();
        return Optional.ofNullable(MODULES.get(id));
    }

    public static Optional<StudioModule> get(String path) {
        return get(ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, path));
    }

    public static Collection<StudioModule> values() {
        bootstrapDefaults();
        return List.copyOf(MODULES.values());
    }

    private static StudioModule module(String path, StudioModuleStatus status, String descriptionSuffix) {
        return new StudioModule(
                ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, path),
                SCREEN_PREFIX + path,
                SCREEN_PREFIX + descriptionSuffix,
                status
        );
    }
}
