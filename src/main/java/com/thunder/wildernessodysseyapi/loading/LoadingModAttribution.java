package com.thunder.wildernessodysseyapi.loading;

import java.util.Map;
import java.util.function.Function;

/** Resolves stack metadata against an immutable loaded-module index without loading classes. */
public final class LoadingModAttribution implements Function<StackTraceElement, String> {
    private final Map<String, String> moduleOwners;

    public LoadingModAttribution(Map<String, String> moduleOwners) {
        this.moduleOwners = Map.copyOf(moduleOwners);
    }

    @Override
    public String apply(StackTraceElement frame) {
        String moduleOwner = moduleOwners.get(frame.getModuleName() == null ? "" : frame.getModuleName());
        if (moduleOwner != null) {
            return moduleOwner;
        }
        String name = frame.getClassName();
        if (name.startsWith("com.thunder.wildernessodysseyapi.")) {
            return "wildernessodysseyapi";
        }
        if (name.startsWith("net.minecraft.") || name.startsWith("com.mojang.")) {
            return "minecraft";
        }
        if (name.startsWith("net.neoforged.")) {
            return "neoforge";
        }
        return "unresolved";
    }
}
