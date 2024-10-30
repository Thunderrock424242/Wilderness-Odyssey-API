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

/**
 * The Client Configuration class.
 */
public class ClientConfig {

    // Configuration fields
    public static final Path CONFIG_PATH;
    public static final ClientConfig CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    // Config values
    public final ModConfigSpec.ConfigValue<String> worldTypeName;
    public final ModConfigSpec.ConfigValue<String> flatMapSettings;

    static {
        // Define the config path
        CONFIG_PATH = Paths.get(FMLPaths.CONFIGDIR.get().toAbsolutePath().toString(), DefaultWorldType.MOD_ID);

        // Build the configuration
        Pair<ClientConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT = specPair.getLeft();
        CLIENT_SPEC = specPair.getRight();
    }

    /**
     * Constructor for the ClientConfig.
     *
     * @param builder the ModConfigSpec builder
     */
    ClientConfig(ModConfigSpec.Builder builder) {
        builder.comment("Client-side world preset configuration").push("world-preset");

        // Define worldTypeName
        worldTypeName = builder
                .comment("Type in the name from the world type which should be selected by default.")
                .define("worldTypeName", "minecraft:large_biomes");

        // Define flatMapSettings
        flatMapSettings = builder
                .comment("Type in a valid generation setting for flat world type.",
                        "Only works if world-type is 'minecraft:flat'.")
                .define("flatMapSettings", "minecraft:bedrock,2*minecraft:dirt,minecraft:grass_block;minecraft:plains");

        builder.pop();
    }

    /**
     * Gets the ResourceKey for the world preset.
     *
     * @return the ResourceKey of WorldPreset
     */
    public static ResourceKey<WorldPreset> getKey() {
        ResourceLocation location = ResourceLocation.tryParse(CLIENT.worldTypeName.get());
        return location == null ?
                ResourceKey.create(Registries.WORLD_PRESET, new ResourceLocation("minecraft:normal")) :
                ResourceKey.create(Registries.WORLD_PRESET, location);
    }
}
