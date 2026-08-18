package com.thunder.wildernessodysseyapi.vegetation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server configuration for the loaded-chunk reactive vegetation scheduler.
 *
 * <p>Every limit is applied before world access. The configuration therefore
 * controls sampling cost rather than allowing a rain event or biome density to
 * create an unbounded amount of work.</p>
 */
public final class VegetationConfig {

    public static final ModConfigSpec CONFIG_SPEC;

    public static final ModConfigSpec.BooleanValue VEGETATION_UPDATES_ENABLED;
    public static final ModConfigSpec.IntValue UPDATES_PER_CHUNK;
    public static final ModConfigSpec.IntValue UPDATE_INTERVAL;
    public static final ModConfigSpec.DoubleValue DROUGHT_SENSITIVITY;
    public static final ModConfigSpec.DoubleValue RAIN_RECOVERY_RATE;
    public static final ModConfigSpec.BooleanValue FLOWER_WEATHER_CLOSING;
    public static final ModConfigSpec.BooleanValue FLOWER_NIGHT_CLOSING;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment(
                "Server-authoritative regional vegetation climate and bounded plant sampling.",
                "Normal vegetation never receives block entities or per-plant server ticks."
        ).push("reactiveVegetation");

        VEGETATION_UPDATES_ENABLED = builder
                .comment("Master switch for regional climate updates and registered plant reactions.")
                .define("vegetationUpdatesEnabled", true);
        UPDATES_PER_CHUNK = builder
                .comment("Maximum surface columns sampled whenever one loaded chunk reaches its scheduled turn.")
                .defineInRange("updatesPerChunk", 4, 1, 64);
        UPDATE_INTERVAL = builder
                .comment(
                        "Target ticks between updates of the same loaded chunk.",
                        "Chunks are spread across intervening ticks rather than updated simultaneously."
                )
                .defineInRange("updateInterval", 200, 20, 72_000);
        DROUGHT_SENSITIVITY = builder
                .comment("Multiplier for regional drying and accumulated drought stress.")
                .defineInRange("droughtSensitivity", 1.0, 0.0, 4.0);
        RAIN_RECOVERY_RATE = builder
                .comment("Moisture and drought recovery applied on each due update during localized rain.")
                .defineInRange("rainRecoveryRate", 0.06, 0.0, 1.0);
        FLOWER_WEATHER_CLOSING = builder
                .comment("Allow registered flowers to close during severe localized storms.")
                .define("flowerWeatherClosing", true);
        FLOWER_NIGHT_CLOSING = builder
                .comment("Allow registered flowers to close without suitable daylight.")
                .define("flowerNightClosing", true);

        builder.pop();
        CONFIG_SPEC = builder.build();
    }

    private VegetationConfig() {
    }
}
