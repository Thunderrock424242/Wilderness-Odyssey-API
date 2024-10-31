package com.thunder.wildernessodysseyapi.config;

import com.thunder.wildernessodysseyapi.DefaultWorldType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * The Client Configuration class for managing world preset options.
 */
public class ClientConfig {

    // Main Configuration Spec
    public static final ModConfigSpec CONFIG_SPEC;
    public static final ModConfigSpec CONFIG;  // Add CONFIG as a reference for compatibility
    public static final Path CONFIG_PATH;
    public static final ClientConfig CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    // Configurable values for world type and flat map settings
    public final ModConfigSpec.ConfigValue<String> worldTypeName;
    public final ModConfigSpec.ConfigValue<String> flatMapSettings;

    static {
        // Define the config path for this mod
        CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve(DefaultWorldType.MOD_ID);

        // Build the configuration and assign CLIENT and CLIENT_SPEC
        Pair<ClientConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT = specPair.getLeft();
        CLIENT_SPEC = specPair.getRight();

        // Link CONFIG_SPEC and CONFIG to CLIENT_SPEC for external use
        CONFIG_SPEC = CLIENT_SPEC;
        CONFIG = CLIENT_SPEC; // Link CONFIG to CLIENT_SPEC for backward compatibility
    }

    /**
     * Constructor for the ClientConfig.
     *
     * @param builder the ModConfigSpec builder for building configuration options
     */
    ClientConfig(ModConfigSpec.Builder builder) {
        builder.comment("Client-side configuration for world presets").push("world-preset");

        // Define the world type name setting
        worldTypeName = builder
                .comment("Specify the default world type name (e.g., minecraft:large_biomes).",
                        "Set to 'minecraft:flat' for a flat world type.")
                .define("worldTypeName", "minecraft:large_biomes");

        // Define the flat world map generation settings
        flatMapSettings = builder
                .comment("Enter a valid generation setting for flat worlds.",
                        "Only applicable if 'worldTypeName' is set to 'minecraft:flat'.",
                        "Example: 'minecraft:bedrock,2*minecraft:dirt,minecraft:grass_block;minecraft:plains'")
                .define("flatMapSettings", "minecraft:bedrock,2*minecraft:dirt,minecraft:grass_block;minecraft:plains");

        builder.pop();
    }

    /**
     * Gets the ResourceKey for the world preset based on the configuration.
     *
     * @return the ResourceKey of the WorldPreset based on user-defined 'worldTypeName'
     */
    public static ResourceKey<WorldPreset> getKey() {
        // Attempt to parse the world type from config or fallback to 'minecraft:normal'
        ResourceLocation location = ResourceLocation.tryParse(CLIENT.worldTypeName.get());
        return location != null ?
                ResourceKey.create(Registries.WORLD_PRESET, Objects.requireNonNull(ResourceLocation.tryParse("minecraft:normal"))) :

                ResourceKey.create(Registries.WORLD_PRESET, location);
    }
}
