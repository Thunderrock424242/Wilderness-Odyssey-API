package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import net.minecraft.world.level.Level;
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
    public static final ModConfigSpec.EnumValue<WaterQuality> WATER_QUALITY;
    public static final ModConfigSpec.EnumValue<SphLocalEffectQuality> SPH_LOCAL_EFFECT_QUALITY;

    public static final ModConfigSpec.DoubleValue UNDERWATER_VISIBILITY_BLOCKS;
    public static final ModConfigSpec.DoubleValue UNDERWATER_TURBIDITY_STRENGTH;
    public static final ModConfigSpec.DoubleValue SURFACE_ABSORPTION_STRENGTH;
    public static final ModConfigSpec.DoubleValue SURFACE_OPACITY_STRENGTH;
    public static final ModConfigSpec.DoubleValue SHORELINE_OVERLAY_STRENGTH;
    public static final ModConfigSpec.IntValue MAX_OCEAN_SURFACE_DISTANCE_BLOCKS;
    public static final ModConfigSpec.IntValue DYNAMIC_OCEAN_CACHE_LIFETIME_TICKS;
    public static final ModConfigSpec.IntValue DYNAMIC_OCEAN_MAX_CELL_SIZE;
    public static final ModConfigSpec.DoubleValue DYNAMIC_OCEAN_TEXTURE_SCALE;
    public static final ModConfigSpec.DoubleValue DYNAMIC_OCEAN_LOD_TEXTURE_SOFTENING;

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
                .comment("Hide vanilla top faces wherever the validated replacement mesh owns water. Vanilla fluid remains tagged compatibility data while Wilderness owns the visible surface.")
                .define("replaceVanillaWaterTopFaces", true);
        SUPPRESS_VANILLA_WATER_TOPS = builder
                .comment("Legacy/debug override: also enables vanilla top hiding. Kept for older configs; replaceVanillaWaterTopFaces is the normal replacement-mode switch.")
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
        WATER_QUALITY = builder
                .comment("Overall water quality target. LOW is cheap waves/no SPH, MEDIUM adds basic foam/ripples, HIGH enables capped local SPH, and CINEMATIC raises client visuals while still keeping hard safety caps.")
                .defineEnum("waterQuality", WaterQuality.CINEMATIC);
        SPH_LOCAL_EFFECT_QUALITY = builder
                .comment("Quality for local SPH-only water effects. The overall waterQuality can still clamp this down; SPH never owns oceans, lakes, rivers, or permanent storage.")
                .defineEnum("sphLocalEffectQuality", SphLocalEffectQuality.HIGH);
        MAX_OCEAN_SURFACE_DISTANCE_BLOCKS = builder
                .comment("Safety cap for view-distance-matched ocean rendering. Higher values cover more distant vanilla water but cost more CPU and vertices.")
                .defineInRange("maxOceanSurfaceDistanceBlocks", 192, 64,
                        ABSOLUTE_OCEAN_SURFACE_DISTANCE_CAP_BLOCKS);
        DYNAMIC_OCEAN_CACHE_LIFETIME_TICKS = builder
                .comment("How long the client may reuse the dynamic ocean patch cache before rescanning nearby water. Higher values improve FPS while still rebuilding on movement and config changes.")
                .defineInRange("dynamicOceanCacheLifetimeTicks", 60, 10, 200);
        DYNAMIC_OCEAN_MAX_CELL_SIZE = builder
                .comment("Largest distant ocean LOD cell size in blocks. Higher values reduce far-ocean patch count while keeping nearby shorelines detailed.")
                .defineInRange("dynamicOceanMaxCellSize", 4, 4, 16);
        DYNAMIC_OCEAN_TEXTURE_SCALE = builder
                .comment("World-space water texture repeat scale for the replacement ocean mesh. Smaller values reduce obvious tiling on large surfaces.")
                .defineInRange("dynamicOceanTextureScale", 0.24, 0.05, 0.50);
        DYNAMIC_OCEAN_LOD_TEXTURE_SOFTENING = builder
                .comment("Reduces texture repetition on medium/far LOD quads so coarse water cells do not look like separate patch panes.")
                .defineInRange("dynamicOceanLodTextureSoftening", 0.75, 0.0, 2.0);
        UNDERWATER_VISIBILITY_BLOCKS = builder
                .comment("Maximum clear-water visibility used by the underwater optical model.")
                .defineInRange("underwaterVisibilityBlocks", 44.0, 8.0, 128.0);
        UNDERWATER_TURBIDITY_STRENGTH = builder
                .comment("Scales storm, shallow-sediment, and moving-water turbidity. Zero keeps water maximally clear.")
                .defineInRange("underwaterTurbidityStrength", 1.0, 0.0, 2.0);
        SURFACE_ABSORPTION_STRENGTH = builder
                .comment("Scales how quickly the replacement surface shifts toward deep-water color with depth. Higher values hide blocky seafloors sooner.")
                .defineInRange("surfaceAbsorptionStrength", 1.70, 0.25, 3.0);
        SURFACE_OPACITY_STRENGTH = builder
                .comment("Scales replacement-surface alpha after depth, foam, and shoreline fades. Higher values make the water medium less see-through.")
                .defineInRange("surfaceOpacityStrength", 1.50, 0.50, 2.0);
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
                .defineInRange("oceanRenderDistanceBlocks", 40, 12, 80);
        NORMAL_OCEAN_CELL_SIZE = builder
                .comment("Horizontal ocean mesh spacing. One gives block-resolution shore edges.")
                .defineInRange("oceanCellSize", 1, 1, 4);
        NORMAL_MAX_OCEAN_SURFACE_PATCHES = builder
                .comment("Maximum dynamic ocean patches kept in the client cache. This prevents view-distance water from tanking FPS.")
                .defineInRange("maxOceanSurfacePatches", 16000, 512, 24000);
        NORMAL_SHORELINE_RENDER_DISTANCE_BLOCKS = builder
                .comment("Radius for block-detail shoreline/local-water overlays.")
                .defineInRange("shorelineRenderDistanceBlocks", 32, 8, 48);
        NORMAL_MAX_SHORELINE_SURFACE_PATCHES = builder
                .comment("Maximum shoreline overlay patches kept near the camera. The nearest cells are prioritized first.")
                .defineInRange("maxShorelineSurfacePatches", 1200, 128, 4096);
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
        return (usesOptimizedProfile() ? "optimized" : "normal") + "/" + waterQuality().name().toLowerCase();
    }

    /** Returns the active top-level water quality target. */
    public static WaterQuality waterQuality() {
        return WATER_QUALITY.get();
    }

    /** Returns whether the replacement water renderer should draw in this world. */
    public static boolean replacementWaterRenderingEnabled(Level level) {
        return WildernessWaterRules.isEnabled(level)
                && ENABLE_GERSTNER_WAVES.get()
                && ENABLE_DYNAMIC_OCEAN_SURFACE.get();
    }

    /** Returns whether the shoreline/local overlay should draw in this world. */
    public static boolean shorelineWaterRenderingEnabled(Level level) {
        return replacementWaterRenderingEnabled(level) && ENABLE_SHORELINE_SURFACE.get();
    }

    /** Returns whether local visual SPH effects may be spawned on this client. */
    public static boolean localSphEffectsEnabled() {
        return ENABLE_SPH_WATER_RENDERING.get()
                && waterQuality().allowsLocalSph()
                && sphLocalEffectQuality() != SphLocalEffectQuality.OFF;
    }

    /** Returns the active local SPH quality level. */
    public static SphLocalEffectQuality sphLocalEffectQuality() {
        return waterQuality().clampSphQuality(SPH_LOCAL_EFFECT_QUALITY.get());
    }

    /** Scales an event-requested particle count to the active local SPH quality. */
    public static int localSphParticleCount(int requestedParticles) {
        if (!localSphEffectsEnabled() || requestedParticles <= 0) {
            return 0;
        }
        SphLocalEffectQuality quality = sphLocalEffectQuality();
        int scaled = Math.round(requestedParticles * quality.particleScale());
        return Math.max(quality.minimumParticles(), Math.min(quality.maximumParticles(), scaled));
    }

    /** Scales an event-requested lifetime to the active local SPH quality. */
    public static int localSphLifetimeTicks(int requestedLifetimeTicks) {
        if (!localSphEffectsEnabled() || requestedLifetimeTicks <= 0) {
            return 0;
        }
        SphLocalEffectQuality quality = sphLocalEffectQuality();
        int scaled = Math.round(requestedLifetimeTicks * quality.lifetimeScale());
        return Math.max(8, Math.min(quality.maximumLifetimeTicks(), scaled));
    }

    /** Returns the maximum number of client-owned visual SPH bodies. */
    public static int maxLocalSphEffects() {
        return localSphEffectsEnabled() ? sphLocalEffectQuality().maxEffects() : 0;
    }

    /** Returns the per-frame client SPH physics particle budget. */
    public static int localSphParticleTickBudget() {
        return localSphEffectsEnabled() ? sphLocalEffectQuality().particleTickBudget() : 0;
    }

    /** Returns the active SPH mesh render distance in blocks. */
    public static int sphRenderDistanceBlocks() {
        int configured = usesOptimizedProfile()
                ? OPTIMIZED_SPH_RENDER_DISTANCE_BLOCKS.get()
                : NORMAL_SPH_RENDER_DISTANCE_BLOCKS.get();
        return waterQuality().sphRenderDistance(configured);
    }

    /** Returns the maximum SPH simulations drawn in one frame. */
    public static int maxRenderedSphSimulations() {
        int configured = usesOptimizedProfile()
                ? OPTIMIZED_MAX_RENDERED_SPH_SIMULATIONS.get()
                : NORMAL_MAX_RENDERED_SPH_SIMULATIONS.get();
        return waterQuality().maxRenderedSphSimulations(configured);
    }

    /** Returns how many SPH revisions pass between mesh rebuilds. */
    public static int sphMeshRevisionInterval() {
        int configured = usesOptimizedProfile()
                ? OPTIMIZED_SPH_MESH_REVISION_INTERVAL.get()
                : NORMAL_SPH_MESH_REVISION_INTERVAL.get();
        return waterQuality().sphMeshRevisionInterval(configured);
    }

    /** Returns the active cosmetic ripple count cap. */
    public static int maxRipples() {
        int configured = usesOptimizedProfile()
                ? OPTIMIZED_MAX_RIPPLES.get()
                : NORMAL_MAX_RIPPLES.get();
        return waterQuality().maxRipples(configured);
    }

    /** Returns the active segment count for each ripple ring. */
    public static int rippleSegments() {
        int configured = usesOptimizedProfile()
                ? OPTIMIZED_RIPPLE_SEGMENTS.get()
                : NORMAL_RIPPLE_SEGMENTS.get();
        return waterQuality().rippleSegments(configured);
    }

    /** Returns the active splash-particle burst count. */
    public static int splashParticles() {
        int configured = usesOptimizedProfile()
                ? OPTIMIZED_SPLASH_PARTICLES.get()
                : NORMAL_SPLASH_PARTICLES.get();
        return waterQuality().splashParticles(configured);
    }

    /** Returns the wave-component limit for a classified water body. */
    public static int waveTrainLimit(WaterBodyClassifier.WaterType type) {
        int requested = usesOptimizedProfile()
                ? OPTIMIZED_WAVE_TRAINS.get()
                : NORMAL_WAVE_TRAINS.get();
        requested = Math.min(requested, waterQuality().maxWaveTrains());

        int profileMaximum = switch (type) {
            case OCEAN -> 4;
            case RIVER -> 3;
            case POND -> 2;
        };
        return Math.max(1, Math.min(requested, profileMaximum));
    }

    /** Returns the active per-frame ocean radius in blocks. */
    public static int oceanRenderDistanceBlocks() {
        int configured = usesOptimizedProfile()
                ? OPTIMIZED_OCEAN_RENDER_DISTANCE_BLOCKS.get()
                : NORMAL_OCEAN_RENDER_DISTANCE_BLOCKS.get();
        return waterQuality().oceanRenderDistance(configured);
    }

    /** Returns the active per-frame ocean mesh spacing in blocks. */
    public static int oceanCellSize() {
        int configured = usesOptimizedProfile()
                ? OPTIMIZED_OCEAN_CELL_SIZE.get()
                : NORMAL_OCEAN_CELL_SIZE.get();
        return waterQuality().oceanCellSize(configured);
    }

    /** Returns whether the replacement renderer may hide baked vanilla water tops. */
    public static boolean suppressVanillaWaterTopFaces() {
        return REPLACE_VANILLA_WATER_TOPS.get() || SUPPRESS_VANILLA_WATER_TOPS.get();
    }

    /** Returns whether the replacement renderer may hide baked vanilla water tops in this world. */
    public static boolean suppressVanillaWaterTopFaces(Level level) {
        return WildernessWaterRules.isEnabled(level) && suppressVanillaWaterTopFaces();
    }

    /** Returns the active cache budget for dynamic ocean surface patches. */
    public static int maxOceanSurfacePatches() {
        int configured = usesOptimizedProfile()
                ? OPTIMIZED_MAX_OCEAN_SURFACE_PATCHES.get()
                : NORMAL_MAX_OCEAN_SURFACE_PATCHES.get();
        return waterQuality().maxOceanSurfacePatches(configured);
    }

    /** Returns how long a matching dynamic-ocean patch cache can be reused. */
    public static int dynamicOceanCacheLifetimeTicks() {
        return DYNAMIC_OCEAN_CACHE_LIFETIME_TICKS.get();
    }

    /** Returns the largest cell size used by far-distance dynamic ocean LOD. */
    public static int dynamicOceanMaxCellSize() {
        return DYNAMIC_OCEAN_MAX_CELL_SIZE.get();
    }

    /** Returns an atlas-safe water texture scale for a replacement ocean patch. */
    public static float dynamicOceanTextureScale(int patchSize) {
        float configured = DYNAMIC_OCEAN_TEXTURE_SCALE.get().floatValue();
        float softening = DYNAMIC_OCEAN_LOD_TEXTURE_SOFTENING.get().floatValue();
        int boundedPatchSize = Math.max(1, patchSize);
        return configured / (1.0f + Math.max(0, boundedPatchSize - 1) * Math.max(0.0f, softening));
    }

    /** Returns the active shoreline/local-water overlay radius in blocks. */
    public static int shorelineRenderDistanceBlocks() {
        int configured = usesOptimizedProfile()
                ? OPTIMIZED_SHORELINE_RENDER_DISTANCE_BLOCKS.get()
                : NORMAL_SHORELINE_RENDER_DISTANCE_BLOCKS.get();
        return waterQuality().shorelineRenderDistance(configured);
    }

    /** Returns the active cache budget for shoreline/local-water overlay patches. */
    public static int maxShorelineSurfacePatches() {
        int configured = usesOptimizedProfile()
                ? OPTIMIZED_MAX_SHORELINE_SURFACE_PATCHES.get()
                : NORMAL_MAX_SHORELINE_SURFACE_PATCHES.get();
        return waterQuality().maxShorelineSurfacePatches(configured);
    }

    /** Returns the user-controlled shoreline overlay strength multiplier. */
    public static float shorelineOverlayStrength() {
        return SHORELINE_OVERLAY_STRENGTH.get().floatValue() * waterQuality().shorelineStrengthScale();
    }

    /** Returns the depth absorption multiplier used by surface renderers. */
    public static float surfaceAbsorptionStrength() {
        float configured = SURFACE_ABSORPTION_STRENGTH.get().floatValue();
        return suppressVanillaWaterTopFaces() ? Math.max(configured, 1.45f) : configured;
    }

    /** Returns the alpha multiplier used by surface renderers. */
    public static float surfaceOpacityStrength() {
        float configured = SURFACE_OPACITY_STRENGTH.get().floatValue();
        return suppressVanillaWaterTopFaces() ? Math.max(configured, 1.28f) : configured;
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
        configuredCap = Math.min(configuredCap, waterQuality().maxDynamicOceanDistanceBlocks());
        return Math.max(highDetailRadius,
                Math.min(configuredCap, viewDistanceBlocks));
    }

    /**
     * Top-level water quality target for the hybrid renderer.
     *
     * <p>This profile clamps the detailed knobs below it so players can choose
     * a broad Unreal-inspired visual target without accidentally enabling
     * full-ocean SPH, unbounded patch rebuilds, or heavy distant water meshes.</p>
     */
    public enum WaterQuality {
        LOW(false, SphLocalEffectQuality.OFF, 1, 0, 8, 0, 0, 0, 3, 48, 64, 2500, 12, 160, 0.55f),
        MEDIUM(false, SphLocalEffectQuality.OFF, 2, 16, 12, 4, 0, 0, 2, 80, 112, 6000, 18, 450, 0.80f),
        HIGH(true, SphLocalEffectQuality.HIGH, 3, 48, 24, 8, 12, 128, 1, 128, 160, 12000, 32, 900, 1.00f),
        CINEMATIC(true, SphLocalEffectQuality.CINEMATIC, 4, 96, 32, 12, 18, 192, 1, 192, 224, 18000, 48, 1400, 1.15f);

        private final boolean localSph;
        private final SphLocalEffectQuality maxSphQuality;
        private final int maxWaveTrains;
        private final int maxRipples;
        private final int maxRippleSegments;
        private final int maxSplashParticles;
        private final int maxRenderedSphSimulations;
        private final int maxSphRenderDistanceBlocks;
        private final int minOceanCellSize;
        private final int maxOceanRenderDistanceBlocks;
        private final int maxDynamicOceanDistanceBlocks;
        private final int maxOceanSurfacePatches;
        private final int maxShorelineRenderDistanceBlocks;
        private final int maxShorelineSurfacePatches;
        private final float shorelineStrengthScale;

        WaterQuality(
                boolean localSph,
                SphLocalEffectQuality maxSphQuality,
                int maxWaveTrains,
                int maxRipples,
                int maxRippleSegments,
                int maxSplashParticles,
                int maxRenderedSphSimulations,
                int maxSphRenderDistanceBlocks,
                int minOceanCellSize,
                int maxOceanRenderDistanceBlocks,
                int maxDynamicOceanDistanceBlocks,
                int maxOceanSurfacePatches,
                int maxShorelineRenderDistanceBlocks,
                int maxShorelineSurfacePatches,
                float shorelineStrengthScale
        ) {
            this.localSph = localSph;
            this.maxSphQuality = maxSphQuality;
            this.maxWaveTrains = maxWaveTrains;
            this.maxRipples = maxRipples;
            this.maxRippleSegments = maxRippleSegments;
            this.maxSplashParticles = maxSplashParticles;
            this.maxRenderedSphSimulations = maxRenderedSphSimulations;
            this.maxSphRenderDistanceBlocks = maxSphRenderDistanceBlocks;
            this.minOceanCellSize = minOceanCellSize;
            this.maxOceanRenderDistanceBlocks = maxOceanRenderDistanceBlocks;
            this.maxDynamicOceanDistanceBlocks = maxDynamicOceanDistanceBlocks;
            this.maxOceanSurfacePatches = maxOceanSurfacePatches;
            this.maxShorelineRenderDistanceBlocks = maxShorelineRenderDistanceBlocks;
            this.maxShorelineSurfacePatches = maxShorelineSurfacePatches;
            this.shorelineStrengthScale = shorelineStrengthScale;
        }

        private boolean allowsLocalSph() {
            return localSph;
        }

        private SphLocalEffectQuality clampSphQuality(SphLocalEffectQuality configured) {
            if (!localSph || configured == SphLocalEffectQuality.OFF) {
                return SphLocalEffectQuality.OFF;
            }
            return configured.ordinal() > maxSphQuality.ordinal() ? maxSphQuality : configured;
        }

        private int maxWaveTrains() {
            return maxWaveTrains;
        }

        private int maxRipples(int configured) {
            return Math.max(0, Math.min(configured, maxRipples));
        }

        private int rippleSegments(int configured) {
            return Math.max(6, Math.min(configured, maxRippleSegments));
        }

        private int splashParticles(int configured) {
            return Math.max(0, Math.min(configured, maxSplashParticles));
        }

        private int maxRenderedSphSimulations(int configured) {
            return localSph ? Math.max(0, Math.min(configured, maxRenderedSphSimulations)) : 0;
        }

        private int sphRenderDistance(int configured) {
            return localSph ? Math.max(16, Math.min(configured, maxSphRenderDistanceBlocks)) : 0;
        }

        private int sphMeshRevisionInterval(int configured) {
            int qualityMinimum = switch (this) {
                case LOW, MEDIUM -> 4;
                case HIGH -> 1;
                case CINEMATIC -> 1;
            };
            return Math.max(qualityMinimum, configured);
        }

        private int oceanCellSize(int configured) {
            return Math.max(minOceanCellSize, configured);
        }

        private int oceanRenderDistance(int configured) {
            return Math.max(12, Math.min(configured, maxOceanRenderDistanceBlocks));
        }

        private int maxDynamicOceanDistanceBlocks() {
            return maxDynamicOceanDistanceBlocks;
        }

        private int maxOceanSurfacePatches(int configured) {
            return Math.max(512, Math.min(configured, maxOceanSurfacePatches));
        }

        private int shorelineRenderDistance(int configured) {
            return Math.max(8, Math.min(configured, maxShorelineRenderDistanceBlocks));
        }

        private int maxShorelineSurfacePatches(int configured) {
            return Math.max(128, Math.min(configured, maxShorelineSurfacePatches));
        }

        private float shorelineStrengthScale() {
            return shorelineStrengthScale;
        }
    }

    /**
     * Client-side quality levels for optional SPH detail.
     *
     * <p>These settings intentionally affect only temporary local effects.
     * Oceans, lakes, rivers, and canonical storage remain controlled by the
     * Water Authority and large-body renderers.</p>
     */
    public enum SphLocalEffectQuality {
        OFF(0.0f, 0.0f, 0, 0, 0, 0, 0),
        LOW(0.35f, 0.55f, 8, 48, 28, 4, 320),
        MEDIUM(0.65f, 0.80f, 12, 96, 48, 8, 760),
        HIGH(1.0f, 1.0f, 16, 160, 80, 12, 1400),
        CINEMATIC(1.35f, 1.20f, 20, 240, 100, 18, 2200);

        private final float particleScale;
        private final float lifetimeScale;
        private final int minimumParticles;
        private final int maximumParticles;
        private final int maximumLifetimeTicks;
        private final int maxEffects;
        private final int particleTickBudget;

        SphLocalEffectQuality(
                float particleScale,
                float lifetimeScale,
                int minimumParticles,
                int maximumParticles,
                int maximumLifetimeTicks,
                int maxEffects,
                int particleTickBudget
        ) {
            this.particleScale = particleScale;
            this.lifetimeScale = lifetimeScale;
            this.minimumParticles = minimumParticles;
            this.maximumParticles = maximumParticles;
            this.maximumLifetimeTicks = maximumLifetimeTicks;
            this.maxEffects = maxEffects;
            this.particleTickBudget = particleTickBudget;
        }

        private float particleScale() {
            return particleScale;
        }

        private float lifetimeScale() {
            return lifetimeScale;
        }

        private int minimumParticles() {
            return minimumParticles;
        }

        private int maximumParticles() {
            return maximumParticles;
        }

        private int maximumLifetimeTicks() {
            return maximumLifetimeTicks;
        }

        private int maxEffects() {
            return maxEffects;
        }

        private int particleTickBudget() {
            return particleTickBudget;
        }
    }
}
