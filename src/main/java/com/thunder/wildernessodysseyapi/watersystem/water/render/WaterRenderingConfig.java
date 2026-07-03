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
    private static final int ABSOLUTE_OCEAN_SURFACE_DISTANCE_CAP_BLOCKS = 256;

    public static final ModConfigSpec CONFIG_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_GERSTNER_WAVES;
    public static final ModConfigSpec.BooleanValue ENABLE_DYNAMIC_OCEAN_SURFACE;
    public static final ModConfigSpec.BooleanValue ENABLE_SHORELINE_SURFACE;
    public static final ModConfigSpec.BooleanValue REPLACE_VANILLA_WATER_TOPS;
    public static final ModConfigSpec.BooleanValue SUPPRESS_VANILLA_WATER_TOPS;
    public static final ModConfigSpec.BooleanValue ENABLE_WATER_CORE_SHADER;
    public static final ModConfigSpec.BooleanValue ENABLE_UNDERWATER_OPTICS;
    public static final ModConfigSpec.BooleanValue ENABLE_UNDERWATER_CAUSTICS;
    public static final ModConfigSpec.BooleanValue ENABLE_SPH_WATER_RENDERING;
    public static final ModConfigSpec.BooleanValue ENABLE_RIPPLES;
    public static final ModConfigSpec.BooleanValue AUTO_OPTIMIZE_WITH_RENDERER_MODS;
    public static final ModConfigSpec.BooleanValue MATCH_OCEAN_SURFACE_TO_VIEW_DISTANCE;

    public static final ModConfigSpec.DoubleValue UNDERWATER_VISIBILITY_BLOCKS;
    public static final ModConfigSpec.DoubleValue UNDERWATER_TURBIDITY_STRENGTH;
    public static final ModConfigSpec.DoubleValue SURFACE_ABSORPTION_STRENGTH;
    public static final ModConfigSpec.DoubleValue SURFACE_OPACITY_STRENGTH;
    public static final ModConfigSpec.DoubleValue SHORELINE_OVERLAY_STRENGTH;
    public static final ModConfigSpec.IntValue MAX_OCEAN_SURFACE_DISTANCE_BLOCKS;

    public static final ModConfigSpec.IntValue NORMAL_SPH_RENDER_DISTANCE_BLOCKS;
    public static final ModConfigSpec.IntValue NORMAL_MAX_RENDERED_SPH_SIMULATIONS;
    public static final ModConfigSpec.IntValue NORMAL_SPH_MESH_REVISION_INTERVAL;
    public static final ModConfigSpec.IntValue NORMAL_MAX_RIPPLES;
    public static final ModConfigSpec.IntValue NORMAL_RIPPLE_SEGMENTS;
    public static final ModConfigSpec.IntValue NORMAL_SPLASH_PARTICLES;
    public static final ModConfigSpec.IntValue NORMAL_WAVE_TRAINS;
    public static final ModConfigSpec.IntValue NORMAL_OCEAN_RENDER_DISTANCE_BLOCKS;
    public static final ModConfigSpec.IntValue NORMAL_OCEAN_CELL_SIZE;
    public static final ModConfigSpec.IntValue NORMAL_MAX_OCEAN_SURFACE_PATCHES;
    public static final ModConfigSpec.IntValue NORMAL_SHORELINE_RENDER_DISTANCE_BLOCKS;
    public static final ModConfigSpec.IntValue NORMAL_MAX_SHORELINE_SURFACE_PATCHES;

    public static final ModConfigSpec.IntValue OPTIMIZED_SPH_RENDER_DISTANCE_BLOCKS;
    public static final ModConfigSpec.IntValue OPTIMIZED_MAX_RENDERED_SPH_SIMULATIONS;
    public static final ModConfigSpec.IntValue OPTIMIZED_SPH_MESH_REVISION_INTERVAL;
    public static final ModConfigSpec.IntValue OPTIMIZED_MAX_RIPPLES;
    public static final ModConfigSpec.IntValue OPTIMIZED_RIPPLE_SEGMENTS;
    public static final ModConfigSpec.IntValue OPTIMIZED_SPLASH_PARTICLES;
    public static final ModConfigSpec.IntValue OPTIMIZED_WAVE_TRAINS;
    public static final ModConfigSpec.IntValue OPTIMIZED_OCEAN_RENDER_DISTANCE_BLOCKS;
    public static final ModConfigSpec.IntValue OPTIMIZED_OCEAN_CELL_SIZE;
    public static final ModConfigSpec.IntValue OPTIMIZED_MAX_OCEAN_SURFACE_PATCHES;
    public static final ModConfigSpec.IntValue OPTIMIZED_SHORELINE_RENDER_DISTANCE_BLOCKS;
    public static final ModConfigSpec.IntValue OPTIMIZED_MAX_SHORELINE_SURFACE_PATCHES;

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
        ENABLE_SHORELINE_SURFACE = builder
                .comment("Render the block-detail shoreline/local-water overlay for cells the open-ocean mesh does not own.")
                .define("enableShorelineSurface", true);
        REPLACE_VANILLA_WATER_TOPS = builder
                .comment("Hide vanilla top faces wherever the replacement water mesh owns the same safe surface cell. This removes the double-water patchwork look while preserving vanilla water states for compatibility.")
                .define("replaceVanillaWaterTopFaces", true);
        SUPPRESS_VANILLA_WATER_TOPS = builder
                .comment("Legacy/debug override: also hide vanilla top faces covered by the replacement mesh. Kept for older configs; replaceVanillaWaterTopFaces is the normal replacement-mode switch.")
                .define("suppressVanillaWaterTopFaces", false);
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
        MATCH_OCEAN_SURFACE_TO_VIEW_DISTANCE = builder
                .comment("Extend the replacement surface toward the client view distance using coarser distant LODs.")
                .define("matchOceanSurfaceToViewDistance", true);
        MAX_OCEAN_SURFACE_DISTANCE_BLOCKS = builder
                .comment("Safety cap for view-distance-matched ocean rendering. Higher values cover more distant vanilla water but cost more CPU and vertices.")
                .defineInRange("maxOceanSurfaceDistanceBlocks", 160, 64,
                        ABSOLUTE_OCEAN_SURFACE_DISTANCE_CAP_BLOCKS);
        UNDERWATER_VISIBILITY_BLOCKS = builder
                .comment("Maximum clear-water visibility used by the underwater optical model.")
                .defineInRange("underwaterVisibilityBlocks", 44.0, 8.0, 128.0);
        UNDERWATER_TURBIDITY_STRENGTH = builder
                .comment("Scales storm, shallow-sediment, and moving-water turbidity. Zero keeps water maximally clear.")
                .defineInRange("underwaterTurbidityStrength", 1.0, 0.0, 2.0);
        SURFACE_ABSORPTION_STRENGTH = builder
                .comment("Scales how quickly the replacement surface shifts toward deep-water color with depth. Higher values hide blocky seafloors sooner.")
                .defineInRange("surfaceAbsorptionStrength", 1.35, 0.25, 3.0);
        SURFACE_OPACITY_STRENGTH = builder
                .comment("Scales replacement-surface alpha after depth, foam, and shoreline fades. Higher values make the water medium less see-through.")
                .defineInRange("surfaceOpacityStrength", 1.16, 0.50, 2.0);
        SHORELINE_OVERLAY_STRENGTH = builder
                .comment("Scales shoreline overlay alpha, foam, and local vertical motion.")
                .defineInRange("shorelineOverlayStrength", 1.0, 0.0, 2.0);

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
                .defineInRange("oceanRenderDistanceBlocks", 32, 12, 80);
        NORMAL_OCEAN_CELL_SIZE = builder
                .comment("Horizontal ocean mesh spacing. One gives block-resolution shore edges.")
                .defineInRange("oceanCellSize", 1, 1, 4);
        NORMAL_MAX_OCEAN_SURFACE_PATCHES = builder
                .comment("Maximum dynamic ocean patches kept in the client cache. This prevents view-distance water from tanking FPS.")
                .defineInRange("maxOceanSurfacePatches", 12000, 512, 24000);
        NORMAL_SHORELINE_RENDER_DISTANCE_BLOCKS = builder
                .comment("Radius for block-detail shoreline/local-water overlays.")
                .defineInRange("shorelineRenderDistanceBlocks", 24, 8, 48);
        NORMAL_MAX_SHORELINE_SURFACE_PATCHES = builder
                .comment("Maximum shoreline overlay patches kept near the camera. The nearest cells are prioritized first.")
                .defineInRange("maxShorelineSurfacePatches", 900, 128, 4096);
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
                .defineInRange("oceanRenderDistanceBlocks", 24, 12, 80);
        OPTIMIZED_OCEAN_CELL_SIZE = builder
                .comment("Horizontal ocean mesh spacing used with renderer optimization mods.")
                .defineInRange("oceanCellSize", 2, 1, 4);
        OPTIMIZED_MAX_OCEAN_SURFACE_PATCHES = builder
                .comment("Maximum dynamic ocean patches kept when renderer optimization mods are active.")
                .defineInRange("maxOceanSurfacePatches", 7000, 512, 24000);
        OPTIMIZED_SHORELINE_RENDER_DISTANCE_BLOCKS = builder
                .comment("Radius for block-detail shoreline/local-water overlays with renderer optimization mods.")
                .defineInRange("shorelineRenderDistanceBlocks", 16, 8, 48);
        OPTIMIZED_MAX_SHORELINE_SURFACE_PATCHES = builder
                .comment("Maximum shoreline overlay patches kept when renderer optimization mods are active.")
                .defineInRange("maxShorelineSurfacePatches", 500, 128, 4096);
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

    /** Returns whether the replacement renderer may hide baked vanilla water tops. */
    public static boolean suppressVanillaWaterTopFaces() {
        return REPLACE_VANILLA_WATER_TOPS.get() || SUPPRESS_VANILLA_WATER_TOPS.get();
    }

    /** Returns the active cache budget for dynamic ocean surface patches. */
    public static int maxOceanSurfacePatches() {
        return usesOptimizedProfile()
                ? OPTIMIZED_MAX_OCEAN_SURFACE_PATCHES.get()
                : NORMAL_MAX_OCEAN_SURFACE_PATCHES.get();
    }

    /** Returns the active shoreline/local-water overlay radius in blocks. */
    public static int shorelineRenderDistanceBlocks() {
        return usesOptimizedProfile()
                ? OPTIMIZED_SHORELINE_RENDER_DISTANCE_BLOCKS.get()
                : NORMAL_SHORELINE_RENDER_DISTANCE_BLOCKS.get();
    }

    /** Returns the active cache budget for shoreline/local-water overlay patches. */
    public static int maxShorelineSurfacePatches() {
        return usesOptimizedProfile()
                ? OPTIMIZED_MAX_SHORELINE_SURFACE_PATCHES.get()
                : NORMAL_MAX_SHORELINE_SURFACE_PATCHES.get();
    }

    /** Returns the user-controlled shoreline overlay strength multiplier. */
    public static float shorelineOverlayStrength() {
        return SHORELINE_OVERLAY_STRENGTH.get().floatValue();
    }

    /** Returns the depth absorption multiplier used by surface renderers. */
    public static float surfaceAbsorptionStrength() {
        return SURFACE_ABSORPTION_STRENGTH.get().floatValue();
    }

    /** Returns the alpha multiplier used by surface renderers. */
    public static float surfaceOpacityStrength() {
        return SURFACE_OPACITY_STRENGTH.get().floatValue();
    }

    /**
     * Returns the full replacement-surface radius for a client view distance.
     * The quality-profile radius remains the high-detail inner LOD.
     */
    public static int dynamicOceanRenderDistanceBlocks(int viewDistanceChunks) {
        int highDetailRadius = oceanRenderDistanceBlocks();
        if (!MATCH_OCEAN_SURFACE_TO_VIEW_DISTANCE.get()) {
            return highDetailRadius;
        }
        int viewDistanceBlocks = Math.max(1, viewDistanceChunks) * 16 + 16;
        int configuredCap = Math.min(
                MAX_OCEAN_SURFACE_DISTANCE_BLOCKS.get(),
                ABSOLUTE_OCEAN_SURFACE_DISTANCE_CAP_BLOCKS
        );
        return Math.max(highDetailRadius,
                Math.min(configuredCap, viewDistanceBlocks));
    }
}
