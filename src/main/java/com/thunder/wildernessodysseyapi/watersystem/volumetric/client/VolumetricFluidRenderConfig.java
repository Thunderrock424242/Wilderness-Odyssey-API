package com.thunder.wildernessodysseyapi.watersystem.volumetric.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only controls for volumetric surface preview rendering.
 */
public final class VolumetricFluidRenderConfig {
    public static final ModConfigSpec CONFIG_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_PREVIEW_RENDERER;
    public static final ModConfigSpec.IntValue MAX_TRIANGLES_PER_FLUID;
    public static final ModConfigSpec.IntValue MAX_RENDER_DISTANCE;
    public static final ModConfigSpec.IntValue MAX_STALE_AGE_TICKS;
    public static final ModConfigSpec.DoubleValue MAX_SURFACE_SLOPE_DELTA;
    public static final ModConfigSpec.DoubleValue WAVE_STRENGTH;
    public static final ModConfigSpec.DoubleValue FOAM_STRENGTH;
    public static final ModConfigSpec.IntValue WATER_ALPHA;
    public static final ModConfigSpec.IntValue LAVA_ALPHA;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("volumetricFluidRenderer");

        ENABLE_PREVIEW_RENDERER = BUILDER
                .comment("Enable first-pass volumetric mesh preview rendering for synced water/lava surfaces. Disabled by default because this renderer is still experimental.")
                .define("enablePreviewRenderer", false);

        MAX_TRIANGLES_PER_FLUID = BUILDER
                .comment("Hard cap for rendered triangles per fluid type.")
                .defineInRange("maxTrianglesPerFluid", 6000, 500, 40000);

        MAX_RENDER_DISTANCE = BUILDER
                .comment("Maximum camera distance (blocks) to render fluid triangles.")
                .defineInRange("maxRenderDistance", 96, 16, 256);

        MAX_STALE_AGE_TICKS = BUILDER
                .comment("Skip rendering if no fresh sync packet was received in this many ticks.")
                .defineInRange("maxStaleAgeTicks", 160, 20, 2000);

        MAX_SURFACE_SLOPE_DELTA = BUILDER
                .comment("Maximum allowed Y delta across a generated quad. Lower values reduce vertical wall artifacts from sparse samples.")
                .defineInRange("maxSurfaceSlopeDelta", 0.55D, 0.05D, 2.0D);

        WAVE_STRENGTH = BUILDER
                .comment("Simple sinusoidal wave amplitude applied to preview vertices.")
                .defineInRange("waveStrength", 0.03D, 0.0D, 0.25D);

        FOAM_STRENGTH = BUILDER
                .comment("How strongly steep slopes brighten towards foam color.")
                .defineInRange("foamStrength", 0.55D, 0.0D, 1.0D);

        WATER_ALPHA = BUILDER
                .comment("Alpha channel for water preview mesh.")
                .defineInRange("waterAlpha", 120, 10, 255);

        LAVA_ALPHA = BUILDER
                .comment("Alpha channel for lava preview mesh.")
                .defineInRange("lavaAlpha", 140, 10, 255);

        BUILDER.pop();
        CONFIG_SPEC = BUILDER.build();
    }

    private VolumetricFluidRenderConfig() {
    }
}
