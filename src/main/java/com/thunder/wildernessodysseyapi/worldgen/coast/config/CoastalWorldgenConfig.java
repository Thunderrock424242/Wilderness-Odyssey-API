package com.thunder.wildernessodysseyapi.worldgen.coast.config;

import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Installation-wide controls for new-chunk coastal biome placement and terrain bands. */
public final class CoastalWorldgenConfig {

    public static ModConfigSpec COMMON_SPEC;

    public static ModConfigSpec.BooleanValue ENABLE_BEACH_BIOME_FAMILY;
    public static ModConfigSpec.BooleanValue ENABLE_TEMPERATE_BEACH;
    public static ModConfigSpec.BooleanValue ENABLE_DUNE_BEACH;
    public static ModConfigSpec.BooleanValue ENABLE_ROCKY_COAST;
    public static ModConfigSpec.BooleanValue ENABLE_COLD_BEACH;
    public static ModConfigSpec.BooleanValue ENABLE_GLACIAL_BEACH;
    public static ModConfigSpec.BooleanValue ENABLE_TROPICAL_BEACH;
    public static ModConfigSpec.BooleanValue ENABLE_COASTAL_TERRAIN_BANDS;
    public static ModConfigSpec.BooleanValue ENABLE_COASTAL_DETAILS;
    public static ModConfigSpec.BooleanValue ENABLE_COASTAL_VEGETATION;
    public static ModConfigSpec.BooleanValue ENABLE_DRIFTWOOD;
    public static ModConfigSpec.BooleanValue ENABLE_TIDE_POOLS;
    public static ModConfigSpec.BooleanValue ENABLE_ROCK_OUTCROPS;
    public static ModConfigSpec.BooleanValue ENABLE_ICE_FRAGMENTS;
    public static ModConfigSpec.IntValue MAX_DUNE_RISE_BLOCKS;
    public static ModConfigSpec.IntValue MAX_TROPICAL_BANK_CUT_BLOCKS;
    public static ModConfigSpec.DoubleValue COASTAL_DETAIL_DENSITY;

    static {
        WildernessConfigSpecs.initialize();
    }

    /** Defines generation choices that are read while the biome source is built. */
    public static void defineCommon(ModConfigSpec.Builder builder) {
        builder.comment(
                "Beach transition-biome placement and bounded coastal surface generation.",
                "Changes affect only newly generated chunks."
        ).push("coastalWorldgen");

        ENABLE_BEACH_BIOME_FAMILY = builder.comment(
                "Opt in to replacing eligible vanilla beach climate slots with the Wilderness coastal family.",
                "Natural-coast surf works independently of this world-generation option.")
                .define("enableBeachBiomeFamily", false);
        ENABLE_TEMPERATE_BEACH = toggle(
                builder,
                "enableTemperateBeach",
                "Generate temperate beach transitions in cool and mild beach climates."
        );
        ENABLE_DUNE_BEACH = toggle(
                builder, "enableDuneBeach", "Generate dry warm dune-beach transitions.");
        ENABLE_ROCKY_COAST = toggle(
                builder, "enableRockyCoast", "Replace eligible stony shores with rocky coasts.");
        ENABLE_COLD_BEACH = toggle(
                builder, "enableColdBeach", "Replace snowy beaches outside the polar region with cold beaches.");
        ENABLE_GLACIAL_BEACH = toggle(
                builder, "enableGlacialBeach", "Place glacial beaches inside the existing polar glacial region.");
        ENABLE_TROPICAL_BEACH = toggle(
                builder, "enableTropicalBeach", "Generate warm humid tropical beach transitions.");
        ENABLE_COASTAL_TERRAIN_BANDS = toggle(
                builder,
                "enableTerrainBands",
                "Apply bounded strandline, beach, dune or rock, and coastal-meadow surface bands."
        );
        ENABLE_COASTAL_DETAILS = toggle(
                builder,
                "enableCoastalDetails",
                "Place sparse biome-aware beach details during new-chunk generation."
        );
        ENABLE_COASTAL_VEGETATION = toggle(
                builder,
                "enableCoastalVegetation",
                "Place sparse grasses and warm-coast plants in eligible dune and meadow bands."
        );
        ENABLE_DRIFTWOOD = toggle(
                builder,
                "enableDriftwood",
                "Place rare terrain-aligned fallen logs on eligible beaches."
        );
        ENABLE_TIDE_POOLS = toggle(
                builder,
                "enableTidePools",
                "Place small contained generation-time tide pools near suitable strandlines."
        );
        ENABLE_ROCK_OUTCROPS = toggle(
                builder,
                "enableRockOutcrops",
                "Place shell-stone patches, coastal boulders, and rare rocky sea stacks."
        );
        ENABLE_ICE_FRAGMENTS = toggle(
                builder,
                "enableIceFragments",
                "Place sparse packed-ice fragments on cold and glacial strandlines."
        );
        MAX_DUNE_RISE_BLOCKS = builder
                .comment("Maximum deterministic sand rise added to dune beach columns during new-chunk generation.")
                .defineInRange("maximumDuneRiseBlocks", 2, 0, 4);
        MAX_TROPICAL_BANK_CUT_BLOCKS = builder
                .comment("Maximum natural bank lowering for a gentle tropical beach, in blocks. New chunks only; zero disables grading. Structure-bearing chunks are skipped.")
                .defineInRange("maximumTropicalBankCutBlocks", 4, 0, 6);
        COASTAL_DETAIL_DENSITY = builder
                .comment("Global density multiplier for sparse coastal details. Zero disables detail placement and one uses the authored maximum density.")
                .defineInRange("coastalDetailDensity", 0.55, 0.0, 1.0);
        builder.pop();
    }

    /** Attaches the unified common spec for diagnostics and config tooling. */
    public static void attachSpec(ModConfigSpec commonSpec) {
        COMMON_SPEC = commonSpec;
    }

    private static ModConfigSpec.BooleanValue toggle(
            ModConfigSpec.Builder builder,
            String name,
            String description
    ) {
        return builder.comment(description).define(name, true);
    }

    private CoastalWorldgenConfig() {
    }
}
