package com.thunder.wildernessodysseyapi.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;

public class ToolDamageConfig {

    // Define config values
    public static final ModConfigSpec CONFIG_SPEC;
    public static final ToolDamageConfig CONFIG;

    // Configuration entries
    public final ConfigValue<Boolean> loadDefaults;
    public final ConfigValue<Map<String, ModSettings>> modTools;
    public final ConfigValue<Map<String, Float>> globalBlockDamage;
    public final ConfigValue<Map<String, Float>> globalEntityDamage;

    static {
        Pair<ToolDamageConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(ToolDamageConfig::new);
        CONFIG = specPair.getLeft();
        CONFIG_SPEC = specPair.getRight();
    }

    private ToolDamageConfig(ModConfigSpec.Builder builder) {
        // General settings
        builder.push("general");
        loadDefaults = builder
                .comment("Load default damage values for vanilla and popular modded tools")
                .translation("mytoolmod.config.load_defaults")
                .define("load_defaults", false);
        builder.pop();

        // Tool-specific configurations
        builder.push("mod_tools");
        modTools = builder
                .comment("Defines tool damage for each mod and checks compatibility versions")
                .translation("mytoolmod.config.mod_tools")
                .define("mod_tools", Map.of());
        builder.pop();

        // Global block damage settings
        builder.push("global_block_damage");
        globalBlockDamage = builder
                .comment("Defines global block damage for tools, by registry name")
                .translation("mytoolmod.config.global_block_damage")
                .define("global_block_damage", Map.of());
        builder.pop();

        // Global entity damage settings
        builder.push("global_entity_damage");
        globalEntityDamage = builder
                .comment("Defines global entity damage for tools, by registry name")
                .translation("mytoolmod.config.global_entity_damage")
                .define("global_entity_damage", Map.of());
        builder.pop();
    }

    public float getBlockDamage(String registryName) {
        return globalBlockDamage.get().getOrDefault(registryName, -1f);
    }

    public float getEntityDamage(String registryName) {
        return globalEntityDamage.get().getOrDefault(registryName, -1f);
    }

    public void checkCompatibility(String modId, String loadedVersion) {
        ModSettings settings = modTools.get().get(modId);
        if (settings != null && !settings.version.equals(loadedVersion)) {
            throw new IllegalStateException(
                    String.format("Incompatible version detected for mod '%s'. Loaded: '%s', Expected: '%s'",
                            modId, loadedVersion, settings.version)
            );
        }
    }

    // Inner class for mod settings
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