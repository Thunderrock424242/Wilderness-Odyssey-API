package com.thunder.wildernessodysseyapi.worldgen.anomaly.config;

import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Common configuration for bounded, generation-time anomaly details. Changes affect new chunks. */
public final class AnomalyWorldgenConfig {
    public static ModConfigSpec COMMON_SPEC;
    public static ModConfigSpec.BooleanValue ENABLE_SURFACE_DISTORTION;
    public static ModConfigSpec.BooleanValue ENABLE_DETAILS;
    public static ModConfigSpec.BooleanValue ENABLE_FLOATING_ISLANDS;
    public static ModConfigSpec.DoubleValue DISTORTION_DENSITY;
    public static ModConfigSpec.DoubleValue ISLAND_CHANCE;

    static { WildernessConfigSpecs.initialize(); }

    private AnomalyWorldgenConfig() { }

    public static void defineCommon(ModConfigSpec.Builder builder) {
        builder.comment("Natural anomaly wilderness details. Native noise terrain remains authoritative; new chunks only.")
                .push("anomalyWorldgen");
        ENABLE_SURFACE_DISTORTION = builder.comment("Replace sparse natural surface patches with distorted material.")
                .define("enableSurfaceDistortion", true);
        ENABLE_DETAILS = builder.comment("Generate sparse rocks, crystal growths, fractures and stationary suspended debris.")
                .define("enableDetails", true);
        ENABLE_FLOATING_ISLANDS = builder.comment("Generate small stationary templates only after complete empty-space preflight.")
                .define("enableFloatingIslands", true);
        DISTORTION_DENSITY = builder.comment("Surface/detail density multiplier from zero to one. Natural grass and stone remain dominant.")
                .defineInRange("distortionDensity", 0.30, 0.0, 1.0);
        ISLAND_CHANCE = builder.comment("Maximum per-chunk chance of a small island in fringe/core zones; lower-strength zones use a reduced chance.")
                .defineInRange("islandChance", 0.035, 0.0, 0.25);
        builder.pop();
    }

    public static void attachSpec(ModConfigSpec commonSpec) { COMMON_SPEC = commonSpec; }
}
