package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Defines client water-quality limits and renderer-mod compatibility defaults.
 *
 * <p>The normal and optimized profiles keep visual choices centralized instead
 * of scattering Sodium/Embeddium checks through render and simulation code.</p>
 */
public final class WaterRenderingConfig {
    public static final ModConfigSpec CONFIG_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_GERSTNER_WAVES;
    public static final ModConfigSpec.BooleanValue ENABLE_DYNAMIC_OCEAN_SURFACE;
    public static final ModConfigSpec.BooleanValue ENABLE_WATER_CORE_SHADER;
    public static final ModConfigSpec.BooleanValue ENABLE_UNDERWATER_OPTICS;
    public static final ModConfigSpec.BooleanValue ENABLE_UNDERWATER_CAUSTICS;
    public static final ModConfigSpec.BooleanValue ENABLE_SPH_WATER_RENDERING;
    public static final ModConfigSpec.BooleanValue ENABLE_RIPPLES;
    public static final ModConfigSpec.BooleanValue AUTO_OPTIMIZE_WITH_RENDERER_MODS;

    public static final ModConfigSpec.DoubleValue UNDERWATER_VISIBILITY_BLOCKS;
    public static final ModConfigSpec.DoubleValue UNDERWATER_TURBIDITY_STRENGTH;

    public static final ModConfigSpec.IntValue NORMAL_SPH_RENDER_DISTANCE_BLOCKS;
    public static final ModConfigSpec.IntValue NORMAL_MAX_RENDERED_SPH_SIMULATIONS;
    public static final ModConfigSpec.IntValue NORMAL_SPH_MESH_REVISION_INTERVAL;
    public static final ModConfigSpec.IntValue NORMAL_MAX_RIPPLES;
    public static final ModConfigSpec.IntValue NORMAL_RIPPLE_SEGMENTS;
    public static final ModConfigSpec.IntValue NORMAL_SPLASH_PARTICLES;
    public static final ModConfigSpec.IntValue NORMAL_WAVE_TRAINS;
    public static final ModConfigSpec.IntValue NORMAL_OCEAN_RENDER_DISTANCE_BLOCKS;
    public static final ModConfigSpec.IntValue NORMAL_OCEAN_CELL_SIZE;

    public static final ModConfigSpec.IntValue OPTIMIZED_SPH_RENDER_DISTANCE_BLOCKS;
    public static final ModConfigSpec.IntValue OPTIMIZED_MAX_RENDERED_SPH_SIMULATIONS;
    public static final ModConfigSpec.IntValue OPTIMIZED_SPH_MESH_REVISION_INTERVAL;
    public static final ModConfigSpec.IntValue OPTIMIZED_MAX_RIPPLES;
    public static final ModConfigSpec.IntValue OPTIMIZED_RIPPLE_SEGMENTS;
    public static final ModConfigSpec.IntValue OPTIMIZED_SPLASH_PARTICLES;
    public static final ModConfigSpec.IntValue OPTIMIZED_WAVE_TRAINS;
    public static final ModConfigSpec.IntValue OPTIMIZED_OCEAN_RENDER_DISTANCE_BLOCKS;
    public static final ModConfigSpec.IntValue OPTIMIZED_OCEAN_CELL_SIZE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Client-side water rendering options.")
                .push("water_rendering");

        ENABLE_GERSTNER_WAVES = builder
                .comment("Enable physically based Gerstner displacement for water surfaces.")
                .define("enableGerstnerWaves", true);
        ENABLE_DYNAMIC_OCEAN_SURFACE = builder
                .comment("Replace visible open-water tops near the camera with the per-frame water surface.")
                .define("enableDynamicOceanSurface", true);
        ENABLE_WATER_CORE_SHADER = builder
                .comment("Use the built-in Fresnel/absorption water shader when no external shader pack owns water rendering.")
                .define("enableWaterCoreShader", true);
        ENABLE_UNDERWATER_OPTICS = builder
                .comment("Use canonical volume and the animated surface for underwater fog and camera immersion.")
                .define("enableUnderwaterOptics", true);
        ENABLE_UNDERWATER_CAUSTICS = builder
                .comment("Render the built-in underwater distortion and caustic overlay when no external shader pack owns it.")
                .define("enableUnderwaterCaustics", true);
        ENABLE_SPH_WATER_RENDERING = builder
                .comment("Render synchronized SPH water meshes from persistent pours and transient shore wash.")
                .define("enableSphWaterRendering", true);
        ENABLE_RIPPLES = builder
                .comment("Render cosmetic ripple rings and splash particles when entities enter water.")
                .define("enableRipples", true);
        AUTO_OPTIMIZE_WITH_RENDERER_MODS = builder
                .comment("Use the optimized profile automatically when Sodium or Embeddium is loaded.")
                .define("autoOptimizeWithRendererMods", true);
        UNDERWATER_VISIBILITY_BLOCKS = builder
                .comment("Maximum clear-water visibility used by the underwater optical model.")
                .defineInRange("underwaterVisibilityBlocks", 44.0, 8.0, 128.0);
        UNDERWATER_TURBIDITY_STRENGTH = builder
                .comment("Scales storm, shallow-sediment, and moving-water turbidity. Zero keeps water maximally clear.")
                .defineInRange("underwaterTurbidityStrength", 1.0, 0.0, 2.0);

        builder.comment("Normal quality profile.")
                .push("normal_profile");
        NORMAL_SPH_RENDER_DISTANCE_BLOCKS = builder
                .comment("Maximum distance for rendering SPH water meshes.")
                .defineInRange("sphRenderDistanceBlocks", 128, 16, 256);
        NORMAL_MAX_RENDERED_SPH_SIMULATIONS = builder
                .comment("Maximum SPH simulations drawn per frame.")
                .defineInRange("maxRenderedSphSimulations", 28, 1, 64);
        NORMAL_SPH_MESH_REVISION_INTERVAL = builder
                .comment("Render mesh rebuild interval in SPH simulation revisions. 1 means every updated snapshot.")
                .defineInRange("sphMeshRevisionInterval", 1, 1, 8);
        NORMAL_MAX_RIPPLES = builder
                .comment("Maximum active cosmetic ripple rings.")
                .defineInRange("maxRipples", 48, 0, 256);
        NORMAL_RIPPLE_SEGMENTS = builder
                .comment("Segments per ripple ring.")
                .defineInRange("rippleSegments", 24, 6, 64);
        NORMAL_SPLASH_PARTICLES = builder
                .comment("Splash particles spawned when an entity enters water.")
                .defineInRange("splashParticles", 8, 0, 64);
        NORMAL_WAVE_TRAINS = builder
                .comment("Maximum Gerstner wave trains evaluated per water vertex.")
                .defineInRange("waveTrains", 4, 1, 4);
        NORMAL_OCEAN_RENDER_DISTANCE_BLOCKS = builder
                .comment("Radius of the per-frame ocean surface around the camera.")
                .defineInRange("oceanRenderDistanceBlocks", 40, 12, 96);
        NORMAL_OCEAN_CELL_SIZE = builder
                .comment("Horizontal ocean mesh spacing. One gives block-resolution shore edges.")
                .defineInRange("oceanCellSize", 1, 1, 4);
        builder.pop();

        builder.comment("Optimized profile used when Sodium or Embeddium is loaded.")
                .push("optimized_profile");
        OPTIMIZED_SPH_RENDER_DISTANCE_BLOCKS = builder
                .comment("Maximum distance for rendering SPH water meshes.")
                .defineInRange("sphRenderDistanceBlocks", 96, 16, 256);
        OPTIMIZED_MAX_RENDERED_SPH_SIMULATIONS = builder
                .comment("Maximum SPH simulations drawn per frame.")
                .defineInRange("maxRenderedSphSimulations", 10, 1, 64);
        OPTIMIZED_SPH_MESH_REVISION_INTERVAL = builder
                .comment("Render mesh rebuild interval in SPH simulation revisions. 2 halves SPH mesh rebuild frequency.")
                .defineInRange("sphMeshRevisionInterval", 2, 1, 8);
        OPTIMIZED_MAX_RIPPLES = builder
                .comment("Maximum active cosmetic ripple rings.")
                .defineInRange("maxRipples", 16, 0, 256);
        OPTIMIZED_RIPPLE_SEGMENTS = builder
                .comment("Segments per ripple ring.")
                .defineInRange("rippleSegments", 12, 6, 64);
        OPTIMIZED_SPLASH_PARTICLES = builder
                .comment("Splash particles spawned when an entity enters water.")
                .defineInRange("splashParticles", 4, 0, 64);
        OPTIMIZED_WAVE_TRAINS = builder
                .comment("Maximum Gerstner wave trains evaluated per water vertex.")
                .defineInRange("waveTrains", 2, 1, 4);
        OPTIMIZED_OCEAN_RENDER_DISTANCE_BLOCKS = builder
                .comment("Radius of the per-frame ocean surface around the camera.")
                .defineInRange("oceanRenderDistanceBlocks", 28, 12, 96);
        OPTIMIZED_OCEAN_CELL_SIZE = builder
                .comment("Horizontal ocean mesh spacing used with renderer optimization mods.")
                .defineInRange("oceanCellSize", 2, 1, 4);
        builder.pop();

        builder.pop();
        CONFIG_SPEC = builder.build();
    }

    private WaterRenderingConfig() {
    }

    /** Returns whether renderer-mod-aware quality limits are currently active. */
    public static boolean usesOptimizedProfile() {
        return AUTO_OPTIMIZE_WITH_RENDERER_MODS.get() && isRendererOptimizationModLoaded();
    }

    /** Returns whether Sodium or Embeddium is present at runtime. */
    public static boolean isRendererOptimizationModLoaded() {
        ModList modList = ModList.get();
        return modList.isLoaded("sodium") || modList.isLoaded("embeddium");
    }

    /** Returns the human-readable active quality profile name. */
    public static String profileName() {
        return usesOptimizedProfile() ? "optimized" : "normal";
    }

    /** Returns the active SPH mesh render distance in blocks. */
    public static int sphRenderDistanceBlocks() {
        return usesOptimizedProfile()
                ? OPTIMIZED_SPH_RENDER_DISTANCE_BLOCKS.get()
                : NORMAL_SPH_RENDER_DISTANCE_BLOCKS.get();
    }

    /** Returns the maximum SPH simulations drawn in one frame. */
    public static int maxRenderedSphSimulations() {
        return usesOptimizedProfile()
                ? OPTIMIZED_MAX_RENDERED_SPH_SIMULATIONS.get()
                : NORMAL_MAX_RENDERED_SPH_SIMULATIONS.get();
    }

    /** Returns how many SPH revisions pass between mesh rebuilds. */
    public static int sphMeshRevisionInterval() {
        return usesOptimizedProfile()
                ? OPTIMIZED_SPH_MESH_REVISION_INTERVAL.get()
                : NORMAL_SPH_MESH_REVISION_INTERVAL.get();
    }

    /** Returns the active cosmetic ripple count cap. */
    public static int maxRipples() {
        return usesOptimizedProfile()
                ? OPTIMIZED_MAX_RIPPLES.get()
                : NORMAL_MAX_RIPPLES.get();
    }

    /** Returns the active segment count for each ripple ring. */
    public static int rippleSegments() {
        return usesOptimizedProfile()
                ? OPTIMIZED_RIPPLE_SEGMENTS.get()
                : NORMAL_RIPPLE_SEGMENTS.get();
    }

    /** Returns the active splash-particle burst count. */
    public static int splashParticles() {
        return usesOptimizedProfile()
                ? OPTIMIZED_SPLASH_PARTICLES.get()
                : NORMAL_SPLASH_PARTICLES.get();
    }

    /** Returns the wave-component limit for a classified water body. */
    public static int waveTrainLimit(WaterBodyClassifier.WaterType type) {
        int requested = usesOptimizedProfile()
                ? OPTIMIZED_WAVE_TRAINS.get()
                : NORMAL_WAVE_TRAINS.get();

        int profileMaximum = switch (type) {
            case OCEAN -> 4;
            case RIVER -> 3;
            case POND -> 2;
        };
        return Math.max(1, Math.min(requested, profileMaximum));
    }

    /** Returns the active per-frame ocean radius in blocks. */
    public static int oceanRenderDistanceBlocks() {
        return usesOptimizedProfile()
                ? OPTIMIZED_OCEAN_RENDER_DISTANCE_BLOCKS.get()
                : NORMAL_OCEAN_RENDER_DISTANCE_BLOCKS.get();
    }

    /** Returns the active per-frame ocean mesh spacing in blocks. */
    public static int oceanCellSize() {
        return usesOptimizedProfile()
                ? OPTIMIZED_OCEAN_CELL_SIZE.get()
                : NORMAL_OCEAN_CELL_SIZE.get();
    }
}
