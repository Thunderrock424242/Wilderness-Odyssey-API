package com.thunder.wildernessodysseyapi.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Map;

public class ToolDamageConfig {

    public static final ModConfigSpec CONFIG_SPEC;
    public static final ToolDamageConfig CONFIG;

    private final ConfigValue<Boolean> loadDefaults;
    private final ConfigValue<Map<String, ModSettings>> modTools;
    private final ConfigValue<Map<String, Float>> globalBlockDamage;
    private final ConfigValue<Map<String, Float>> globalEntityDamage;

    private Map<String, ModSettings> modToolSettings = new HashMap<>();
    private Map<String, Float> blockDamageSettings = new HashMap<>();
    private Map<String, Float> entityDamageSettings = new HashMap<>();

    static {
        Pair<ToolDamageConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(ToolDamageConfig::new);
        CONFIG = specPair.getLeft();
        CONFIG_SPEC = specPair.getRight();
    }

    private ToolDamageConfig(ModConfigSpec.Builder builder) {
        builder.push("general");
        loadDefaults = builder.comment("Load default damage values for vanilla and popular modded tools")
                .define("load_defaults", false);
        builder.pop();

        builder.push("mod_tools");
        modTools = builder.comment("Defines tool damage for each mod and checks compatibility versions")
                .define("mod_tools", Map.of());
        builder.pop();

        builder.push("global_block_damage");
        globalBlockDamage = builder.comment("Defines global block damage for tools, by registry name")
                .define("global_block_damage", Map.of());
        builder.pop();

        builder.push("global_entity_damage");
        globalEntityDamage = builder.comment("Defines global entity damage for tools, by registry name")
                .define("global_entity_damage", Map.of());
        builder.pop();
    }

    public void loadConfig() {
        boolean defaultsEnabled = loadDefaults.get();
        modToolSettings = modTools.get();
        blockDamageSettings = globalBlockDamage.get();
        entityDamageSettings = globalEntityDamage.get();
    }

    public float getBlockDamage(String registryName) {
        return blockDamageSettings.getOrDefault(registryName, -1f);
    }

    public float getEntityDamage(String registryName) {
        return entityDamageSettings.getOrDefault(registryName, -1f);
    }

    public ModSettings getModSettings(String modId) {
        return modToolSettings.get(modId);
    }

    public void checkCompatibility(String modId, String loadedVersion) {
        ModSettings settings = modToolSettings.get(modId);
        if (settings != null && !settings.version.equals(loadedVersion)) {
            throw new IllegalStateException(
                    String.format("Incompatible version detected for mod '%s'. Loaded: '%s', Expected: '%s'",
                            modId, loadedVersion, settings.version)
            );
        }
    }

    public static class ModSettings {
        public String version;
        public Map<String, Float> blockDamage;
        public Map<String, Float> entityDamage;

        public ModSettings(String version, Map<String, Float> blockDamage, Map<String, Float> entityDamage) {
            this.version = version;
            this.blockDamage = blockDamage;
            this.entityDamage = entityDamage;
        }
    }
}
