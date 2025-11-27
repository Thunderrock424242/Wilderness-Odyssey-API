package com.thunder.wildernessodysseyapi.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Validates mod config registrations to avoid conflicting file names or reused specs.
 */
public final class ConfigRegistrationValidator {

    private static final Map<String, ModConfig.Type> FILE_USAGE = new HashMap<>();
    private static final Map<ModConfigSpec, String> SPEC_USAGE = new IdentityHashMap<>();

    private ConfigRegistrationValidator() {
    }

    /**
     * Registers a config spec while checking for duplicate file usage or spec reuse.
     *
     * @param container the owning mod container
     * @param type      the config type (client, common, server)
     * @param spec      the config specification to register
     * @param fileName  the target config filename
     */
    public static void register(ModContainer container, ModConfig.Type type, ModConfigSpec spec, String fileName) {
        Objects.requireNonNull(container, "ModContainer cannot be null");
        Objects.requireNonNull(type, "ModConfig.Type cannot be null");
        Objects.requireNonNull(spec, "ModConfigSpec cannot be null");

        String normalized = normalizeFileName(fileName);
        ModConfig.Type existingType = FILE_USAGE.putIfAbsent(normalized, type);
        if (existingType != null) {
            throw new IllegalStateException("Config file '" + normalized + "' already registered for type " + existingType);
        }

        String existingFile = SPEC_USAGE.putIfAbsent(spec, normalized);
        if (existingFile != null) {
            throw new IllegalStateException("Config spec already registered for file '" + existingFile + "'");
        }

        container.registerConfig(type, spec, normalized);
    }

    private static String normalizeFileName(String fileName) {
        Objects.requireNonNull(fileName, "Config filename cannot be null");
        String normalized = fileName.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Config filename cannot be blank");
        }
        return normalized;
    }
}
