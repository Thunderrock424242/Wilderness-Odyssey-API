package com.thunder.wildernessodysseyapi.environment.glacial.config;

import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Side-owned configuration for polar biome placement, simulation, and client ambience. */
public final class GlacialConfig {

    public static ModConfigSpec COMMON_SPEC;
    public static ModConfigSpec CLIENT_SPEC;
    public static ModConfigSpec SERVER_SPEC;

    public static ModConfigSpec.BooleanValue ENABLE_POLAR_BIOME_SYSTEM;
    public static ModConfigSpec.BooleanValue ENABLE_POLAR_ICE_SHEET;
    public static ModConfigSpec.BooleanValue ENABLE_GLACIAL_HIGHLANDS;
    public static ModConfigSpec.BooleanValue ENABLE_GLACIAL_BASIN;
    public static ModConfigSpec.BooleanValue ENABLE_GLACIAL_MELTWATER_VALLEY;
    public static ModConfigSpec.BooleanValue ENABLE_ICEBERG_COAST;
    public static ModConfigSpec.BooleanValue ENABLE_GLACIER_CREVASSES;
    public static ModConfigSpec.BooleanValue ENABLE_GLACIER_ICE_CAVES;
    public static ModConfigSpec.BooleanValue ENABLE_GLACIAL_RIVERS;
    public static ModConfigSpec.BooleanValue ENABLE_GLACIAL_WATERFALLS;

    public static ModConfigSpec.BooleanValue ENABLE_SEASONAL_GLACIAL_EFFECTS;
    public static ModConfigSpec.BooleanValue ENABLE_SEASONAL_RIVER_FREEZING;
    public static ModConfigSpec.BooleanValue ENABLE_SEASONAL_MELTWATER;
    public static ModConfigSpec.IntValue GLACIAL_SEASON_UPDATE_BUDGET;
    public static ModConfigSpec.IntValue GLACIAL_SEASON_UPDATE_INTERVAL;
    public static ModConfigSpec.IntValue GLACIAL_SEASON_UPDATE_RADIUS;

    public static ModConfigSpec.BooleanValue ENABLE_GLACIER_AMBIENT_SOUNDS;
    public static ModConfigSpec.BooleanValue ENABLE_BLOWING_SNOW_EFFECTS;

    static {
        WildernessConfigSpecs.initialize();
    }

    /** Defines installation-wide generation choices that must be stable while biome sources are built. */
    public static void defineCommon(ModConfigSpec.Builder builder) {
        builder.comment(
                "Polar glacial biome placement and generation controls.",
                "Biome changes affect only newly generated terrain."
        ).push("polarGlacialBiomes");

        ENABLE_POLAR_BIOME_SYSTEM = builder
                .comment("Master switch for polar biome placement and glacial generation features.")
                .define("enablePolarBiomeSystem", true);
        ENABLE_POLAR_ICE_SHEET = toggle(builder, "enablePolarIceSheet", "Generate inland polar ice sheets.");
        ENABLE_GLACIAL_HIGHLANDS = toggle(builder, "enableGlacialHighlands", "Generate glacial mountain highlands.");
        ENABLE_GLACIAL_BASIN = toggle(builder, "enableGlacialBasin", "Generate polar glacial basins.");
        ENABLE_GLACIAL_MELTWATER_VALLEY = toggle(
                builder,
                "enableGlacialMeltwaterValley",
                "Generate glacial meltwater valleys."
        );
        ENABLE_ICEBERG_COAST = toggle(builder, "enableIcebergCoast", "Generate iceberg coasts.");
        ENABLE_GLACIER_CREVASSES = toggle(builder, "enableGlacierCrevasses", "Carve bounded glacier crevasses.");
        ENABLE_GLACIER_ICE_CAVES = toggle(builder, "enableGlacierIceCaves", "Carve structure-aware ice caves.");
        ENABLE_GLACIAL_RIVERS = toggle(builder, "enableGlacialRivers", "Generate terrain-following glacial channels.");
        ENABLE_GLACIAL_WATERFALLS = toggle(
                builder,
                "enableGlacialWaterfalls",
                "Generate frozen cliff curtains that can thaw seasonally."
        );
        builder.pop();
    }

    /** Defines server-authoritative, bounded seasonal block-update controls. */
    public static void defineServer(ModConfigSpec.Builder builder) {
        builder.comment(
                "Gradual loaded-chunk glacier thawing and refreezing.",
                "No setting causes an unloaded-chunk or full-world scan."
        ).push("seasonalGlaciers");

        ENABLE_SEASONAL_GLACIAL_EFFECTS = toggle(
                builder,
                "enableSeasonalGlacialEffects",
                "Allow an installed season calendar to drive glacial presentation and block changes."
        );
        ENABLE_SEASONAL_RIVER_FREEZING = toggle(
                builder,
                "enableSeasonalRiverFreezing",
                "Gradually freeze exposed water in loaded glacial chunks during winter."
        );
        ENABLE_SEASONAL_MELTWATER = toggle(
                builder,
                "enableSeasonalMeltwater",
                "Gradually thaw ordinary surface ice into meltwater during warmer seasons."
        );
        GLACIAL_SEASON_UPDATE_BUDGET = builder
                .comment("Maximum sampled block positions changed or inspected per level tick.")
                .defineInRange("glacialSeasonUpdateBudget", 16, 1, 256);
        GLACIAL_SEASON_UPDATE_INTERVAL = builder
                .comment("Target ticks between seasonal passes for the same loaded glacial chunk.")
                .defineInRange("glacialSeasonUpdateInterval", 80, 20, 72_000);
        GLACIAL_SEASON_UPDATE_RADIUS = builder
                .comment("Maximum player distance in blocks for seasonal block changes.")
                .defineInRange("glacialSeasonUpdateRadius", 160, 32, 512);
        builder.pop();
    }

    /** Defines client-local atmospheric effects; neither option changes world state. */
    public static void defineClient(ModConfigSpec.Builder builder) {
        builder.comment("Client-local polar sound and particle ambience.").push("glacialAmbience");
        ENABLE_GLACIER_AMBIENT_SOUNDS = toggle(
                builder,
                "enableGlacierAmbientSounds",
                "Play sparse wind, ice movement, cave drip, and meltwater ambience."
        );
        ENABLE_BLOWING_SNOW_EFFECTS = toggle(
                builder,
                "enableBlowingSnowEffects",
                "Render lightweight player-local blowing snow in glacial biomes."
        );
        builder.pop();
    }

    /** Attaches the three unified specs for compatibility with diagnostics and config tooling. */
    public static void attachSpecs(
            ModConfigSpec commonSpec,
            ModConfigSpec clientSpec,
            ModConfigSpec serverSpec
    ) {
        COMMON_SPEC = commonSpec;
        CLIENT_SPEC = clientSpec;
        SERVER_SPEC = serverSpec;
    }

    private static ModConfigSpec.BooleanValue toggle(
            ModConfigSpec.Builder builder,
            String name,
            String comment
    ) {
        return builder.comment(comment).define(name, true);
    }

    private GlacialConfig() {
    }
}
