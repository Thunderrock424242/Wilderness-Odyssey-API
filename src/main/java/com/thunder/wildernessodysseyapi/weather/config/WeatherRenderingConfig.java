package com.thunder.wildernessodysseyapi.weather.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Defines client-only quality and motion limits for localized weather rendering.
 *
 * <p>The server remains authoritative for atmospheric state. These options
 * change only how much of that synchronized field the client visualizes.</p>
 */
public final class WeatherRenderingConfig {

    public static final ModConfigSpec CONFIG_SPEC;
    public static final ModConfigSpec.BooleanValue ENABLE_LOCALIZED_CLOUDS;
    public static final ModConfigSpec.BooleanValue ENABLE_VOLUMETRIC_CLOUDS;
    public static final ModConfigSpec.BooleanValue ENABLE_RAYMARCHED_CLOUDS;
    public static final ModConfigSpec.IntValue RAYMARCH_STEPS;
    public static final ModConfigSpec.IntValue RENDER_DISTANCE_BLOCKS;
    public static final ModConfigSpec.IntValue REBUILD_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue WIND_DETAIL_SPEED_BLOCKS_PER_SECOND;
    public static final ModConfigSpec.IntValue MAXIMUM_CLOUD_TILES;
    public static final ModConfigSpec.DoubleValue OPACITY_MULTIPLIER;
    public static final ModConfigSpec.IntValue VOLUMETRIC_LAYER_COUNT;
    public static final ModConfigSpec.DoubleValue VOLUMETRIC_DETAIL_STRENGTH;
    public static final ModConfigSpec.BooleanValue ENABLE_DISTANT_RAIN_SHAFTS;
    public static final ModConfigSpec.BooleanValue ENABLE_WIND_DRIVEN_PRECIPITATION;
    public static final ModConfigSpec.DoubleValue PRECIPITATION_WIND_SLANT_BLOCKS;
    public static final ModConfigSpec.IntValue DISTANT_RAIN_DISTANCE_BLOCKS;
    public static final ModConfigSpec.IntValue DISTANT_RAIN_SPACING_BLOCKS;
    public static final ModConfigSpec.IntValue MAXIMUM_DISTANT_RAIN_SHAFTS;
    public static final ModConfigSpec.DoubleValue PRECIPITATION_STREAK_DENSITY;
    public static final ModConfigSpec.DoubleValue PRECIPITATION_OPACITY;
    public static final ModConfigSpec.DoubleValue PRECIPITATION_IMPACT_DENSITY;
    public static final ModConfigSpec.IntValue MAXIMUM_PRECIPITATION_IMPACTS;
    public static final ModConfigSpec.BooleanValue ENABLE_DISTANT_CLOUD_LAYER;
    public static final ModConfigSpec.IntValue DISTANT_CLOUD_DISTANCE_BLOCKS;
    public static final ModConfigSpec.IntValue DISTANT_CLOUD_SPACING_BLOCKS;
    public static final ModConfigSpec.IntValue MAXIMUM_DISTANT_CLOUD_TILES;
    public static final ModConfigSpec.DoubleValue CLOUD_SHADOW_STRENGTH;
    public static final ModConfigSpec.BooleanValue ENABLE_SURFACE_OVERLAYS;
    public static final ModConfigSpec.IntValue SURFACE_OVERLAY_RADIUS_BLOCKS;
    public static final ModConfigSpec.IntValue MAXIMUM_SURFACE_PATCHES;

    private static final Settings DEFAULTS = new Settings(
            true,
            true,
            true,
            24,
            384,
            5,
            6.0,
            4096,
            1.0,
            8,
            0.65,
            true,
            true,
            10.0,
            96,
            6,
            768,
            0.82,
            0.78,
            0.32,
            256,
            true,
            1_024,
            48,
            512,
            0.55,
            true,
            24,
            256
    );
    private static volatile Settings activeSettings = DEFAULTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Client-side rendering options for server-authored localized weather.")
                .push("localized_clouds");
        ENABLE_LOCALIZED_CLOUDS = builder
                .comment("Replace vanilla's global cloud sheet with weather-cell cloud masses.")
                .define("enabled", true);
        ENABLE_VOLUMETRIC_CLOUDS = builder
                .comment("Render soft multi-layer 3D cloud columns when Fancy clouds and the custom shader are available.")
                .define("volumetricClouds", true);
        ENABLE_RAYMARCHED_CLOUDS = builder
                .comment("Use the high-quality density-raymarch cloud tier. Falls back safely when unavailable or a shader pack is active.")
                .define("raymarchedClouds", true);
        RAYMARCH_STEPS = builder
                .comment("Maximum samples through each raymarched cloud volume. Higher values improve shape quality at a significant GPU cost.")
                .defineInRange("raymarchSteps", 24, 16, 64);
        RENDER_DISTANCE_BLOCKS = builder
                .comment("Horizontal radius of localized cloud geometry in blocks.")
                .defineInRange("renderDistanceBlocks", 384, 96, 512);
        REBUILD_INTERVAL_TICKS = builder
                .comment("Minimum ticks between cloud mesh rebuilds while weather blends or wind moves detail.")
                .defineInRange("rebuildIntervalTicks", 5, 2, 40);
        WIND_DETAIL_SPEED_BLOCKS_PER_SECOND = builder
                .comment("Maximum visual detail drift at a normalized wind component of one. The rainy cloud envelope remains world anchored.")
                .defineInRange("windDetailSpeedBlocksPerSecond", 6.0, 0.0, 24.0);
        MAXIMUM_CLOUD_TILES = builder
                .comment("Hard cap on sampled 12 by 12 cloud tiles in one mesh rebuild.")
                .defineInRange("maximumCloudTiles", 4096, 256, 8192);
        OPACITY_MULTIPLIER = builder
                .comment("Scales localized cloud opacity without changing authoritative coverage.")
                .defineInRange("opacityMultiplier", 1.0, 0.25, 1.25);
        VOLUMETRIC_LAYER_COUNT = builder
                .comment("Number of translucent slices in a volumetric cloud column. Higher values look smoother but cost more fill rate.")
                .defineInRange("volumetricLayerCount", 8, 4, 20);
        VOLUMETRIC_DETAIL_STRENGTH = builder
                .comment("Strength of procedural small-scale erosion applied by the volumetric cloud shader.")
                .defineInRange("volumetricDetailStrength", 0.65, 0.0, 1.0);
        ENABLE_DISTANT_CLOUD_LAYER = builder
                .comment("Render a sparse horizon cloud deck with darker storm-front silhouettes.")
                .define("distantCloudLayer", true);
        DISTANT_CLOUD_DISTANCE_BLOCKS = builder
                .comment("Maximum radius of the low-detail horizon cloud deck.")
                .defineInRange("distantCloudDistanceBlocks", 1_024, 384, 2_048);
        DISTANT_CLOUD_SPACING_BLOCKS = builder
                .comment("World spacing of low-detail horizon cloud samples.")
                .defineInRange("distantCloudSpacingBlocks", 48, 24, 96);
        MAXIMUM_DISTANT_CLOUD_TILES = builder
                .comment("Hard cap for low-detail cloud patches in a mesh rebuild.")
                .defineInRange("maximumDistantCloudTiles", 512, 64, 2_048);
        CLOUD_SHADOW_STRENGTH = builder
                .comment("Strength of approximate sunlight darkening beneath broad cloud masses.")
                .defineInRange("cloudShadowStrength", 0.55, 0.0, 1.0);
        builder.pop();

        builder.comment("Client-side presentation of localized precipitation.")
                .push("localized_precipitation");
        ENABLE_DISTANT_RAIN_SHAFTS = builder
                .comment("Render sparse vanilla-style rain curtains beyond Minecraft's near weather radius.")
                .define("distantRainShafts", true);
        ENABLE_WIND_DRIVEN_PRECIPITATION = builder
                .comment("Lean rain and snow columns downwind using the synchronized surface wind.")
                .define("windDrivenPrecipitation", true);
        PRECIPITATION_WIND_SLANT_BLOCKS = builder
                .comment("Maximum horizontal displacement between the bottom and top of a precipitation column.")
                .defineInRange("precipitationWindSlantBlocks", 10.0, 0.0, 24.0);
        DISTANT_RAIN_DISTANCE_BLOCKS = builder
                .comment("Maximum horizontal distance of distant rain curtains.")
                .defineInRange("distantRainDistanceBlocks", 96, 32, 192);
        DISTANT_RAIN_SPACING_BLOCKS = builder
                .comment("World-space spacing between distant rain curtains. Larger values improve performance.")
                .defineInRange("distantRainSpacingBlocks", 6, 4, 16);
        MAXIMUM_DISTANT_RAIN_SHAFTS = builder
                .comment("Hard cap on loaded distant rain columns sampled during one cache rebuild.")
                .defineInRange("maximumDistantRainShafts", 768, 64, 2_048);
        PRECIPITATION_STREAK_DENSITY = builder
                .comment("Fraction of nearby rain and snow columns drawn. Lower values create finer, less curtain-like precipitation.")
                .defineInRange("streakDensity", 0.82, 0.10, 1.0);
        PRECIPITATION_OPACITY = builder
                .comment("Opacity multiplier for localized rain, snow, hail, and distant precipitation.")
                .defineInRange("opacity", 0.78, 0.15, 1.25);
        PRECIPITATION_IMPACT_DENSITY = builder
                .comment("Frequency of subtle procedural water rings and hard-surface impacts. Set to zero to disable them.")
                .defineInRange("impactDensity", 0.32, 0.0, 1.0);
        MAXIMUM_PRECIPITATION_IMPACTS = builder
                .comment("Hard cap on simultaneously animated precipitation impacts.")
                .defineInRange("maximumImpacts", 256, 32, 1_024);
        builder.pop();

        builder.comment("Cosmetic wet-ground and puddle overlays.")
                .push("surface_overlays");
        ENABLE_SURFACE_OVERLAYS = builder
                .comment("Draw bounded translucent wetness and puddle patches from synchronized surface state.")
                .define("enabled", true);
        SURFACE_OVERLAY_RADIUS_BLOCKS = builder
                .comment("Horizontal radius sampled around the camera for wet-ground overlays.")
                .defineInRange("radiusBlocks", 24, 8, 64);
        MAXIMUM_SURFACE_PATCHES = builder
                .comment("Hard cap on rendered wet or puddled ground patches per frame.")
                .defineInRange("maximumPatches", 256, 32, 1_024);
        builder.pop();
        CONFIG_SPEC = builder.build();
    }

    private WeatherRenderingConfig() {
    }

    /** Returns the immutable settings snapshot used by the render thread. */
    public static Settings settings() {
        return activeSettings;
    }

    /** Refreshes the render-thread snapshot after the client config loads or reloads. */
    public static void reload() {
        activeSettings = new Settings(
                ENABLE_LOCALIZED_CLOUDS.get(),
                ENABLE_VOLUMETRIC_CLOUDS.get(),
                ENABLE_RAYMARCHED_CLOUDS.get(),
                RAYMARCH_STEPS.get(),
                RENDER_DISTANCE_BLOCKS.get(),
                REBUILD_INTERVAL_TICKS.get(),
                WIND_DETAIL_SPEED_BLOCKS_PER_SECOND.get(),
                MAXIMUM_CLOUD_TILES.get(),
                OPACITY_MULTIPLIER.get(),
                VOLUMETRIC_LAYER_COUNT.get(),
                VOLUMETRIC_DETAIL_STRENGTH.get(),
                ENABLE_DISTANT_RAIN_SHAFTS.get(),
                ENABLE_WIND_DRIVEN_PRECIPITATION.get(),
                PRECIPITATION_WIND_SLANT_BLOCKS.get(),
                DISTANT_RAIN_DISTANCE_BLOCKS.get(),
                DISTANT_RAIN_SPACING_BLOCKS.get(),
                MAXIMUM_DISTANT_RAIN_SHAFTS.get(),
                PRECIPITATION_STREAK_DENSITY.get(),
                PRECIPITATION_OPACITY.get(),
                PRECIPITATION_IMPACT_DENSITY.get(),
                MAXIMUM_PRECIPITATION_IMPACTS.get(),
                ENABLE_DISTANT_CLOUD_LAYER.get(),
                DISTANT_CLOUD_DISTANCE_BLOCKS.get(),
                DISTANT_CLOUD_SPACING_BLOCKS.get(),
                MAXIMUM_DISTANT_CLOUD_TILES.get(),
                CLOUD_SHADOW_STRENGTH.get(),
                ENABLE_SURFACE_OVERLAYS.get(),
                SURFACE_OVERLAY_RADIUS_BLOCKS.get(),
                MAXIMUM_SURFACE_PATCHES.get()
        );
    }

    /** Immutable and defensively bounded renderer settings. */
    public record Settings(
            boolean enabled,
            boolean volumetricClouds,
            boolean raymarchedClouds,
            int raymarchSteps,
            int renderDistanceBlocks,
            int rebuildIntervalTicks,
            double windDetailSpeedBlocksPerSecond,
            int maximumCloudTiles,
            double opacityMultiplier,
            int volumetricLayerCount,
            double volumetricDetailStrength,
            boolean distantRainShafts,
            boolean windDrivenPrecipitation,
            double precipitationWindSlantBlocks,
            int distantRainDistanceBlocks,
            int distantRainSpacingBlocks,
            int maximumDistantRainShafts,
            double precipitationStreakDensity,
            double precipitationOpacity,
            double precipitationImpactDensity,
            int maximumPrecipitationImpacts,
            boolean distantCloudLayer,
            int distantCloudDistanceBlocks,
            int distantCloudSpacingBlocks,
            int maximumDistantCloudTiles,
            double cloudShadowStrength,
            boolean surfaceOverlays,
            int surfaceOverlayRadiusBlocks,
            int maximumSurfacePatches
    ) {
        /** Preserves the weather-v2 settings shape for compatibility callers. */
        public Settings(
                boolean enabled,
                boolean volumetricClouds,
                int renderDistanceBlocks,
                int rebuildIntervalTicks,
                double windDetailSpeedBlocksPerSecond,
                int maximumCloudTiles,
                double opacityMultiplier,
                int volumetricLayerCount,
                double volumetricDetailStrength,
                boolean distantRainShafts,
                boolean windDrivenPrecipitation,
                double precipitationWindSlantBlocks,
                int distantRainDistanceBlocks,
                int distantRainSpacingBlocks,
                int maximumDistantRainShafts
        ) {
            this(enabled, volumetricClouds, false, 32, renderDistanceBlocks, rebuildIntervalTicks,
                    windDetailSpeedBlocksPerSecond, maximumCloudTiles, opacityMultiplier,
                    volumetricLayerCount, volumetricDetailStrength, distantRainShafts,
                    windDrivenPrecipitation, precipitationWindSlantBlocks,
                    distantRainDistanceBlocks, distantRainSpacingBlocks, maximumDistantRainShafts,
                    0.82, 0.78, 0.32, 256,
                    true, 1_024, 48, 512, 0.55, true, 24, 256);
        }

        /** Preserves the original settings shape for tests and compatibility callers. */
        public Settings(
                boolean enabled,
                int renderDistanceBlocks,
                int rebuildIntervalTicks,
                double windDetailSpeedBlocksPerSecond,
                int maximumCloudTiles,
                double opacityMultiplier,
                boolean distantRainShafts,
                int distantRainDistanceBlocks,
                int distantRainSpacingBlocks,
                int maximumDistantRainShafts
        ) {
            this(
                    enabled,
                    true,
                    false,
                    32,
                    renderDistanceBlocks,
                    rebuildIntervalTicks,
                    windDetailSpeedBlocksPerSecond,
                    maximumCloudTiles,
                    opacityMultiplier,
                    8,
                    0.65,
                    distantRainShafts,
                    true,
                    10.0,
                    distantRainDistanceBlocks,
                    distantRainSpacingBlocks,
                    maximumDistantRainShafts,
                    0.82,
                    0.78,
                    0.32,
                    256,
                    true,
                    1_024,
                    48,
                    512,
                    0.55,
                    true,
                    24,
                    256
            );
        }

        public Settings {
            renderDistanceBlocks = clamp(renderDistanceBlocks, 96, 512);
            raymarchSteps = clamp(raymarchSteps, 16, 64);
            rebuildIntervalTicks = clamp(rebuildIntervalTicks, 2, 40);
            windDetailSpeedBlocksPerSecond = clamp(windDetailSpeedBlocksPerSecond, 0.0, 24.0);
            maximumCloudTiles = clamp(maximumCloudTiles, 256, 8192);
            opacityMultiplier = clamp(opacityMultiplier, 0.25, 1.25);
            volumetricLayerCount = clamp(volumetricLayerCount, 4, 20);
            volumetricDetailStrength = clamp(volumetricDetailStrength, 0.0, 1.0);
            precipitationWindSlantBlocks = clamp(precipitationWindSlantBlocks, 0.0, 24.0);
            distantRainDistanceBlocks = clamp(distantRainDistanceBlocks, 32, 192);
            distantRainSpacingBlocks = clamp(distantRainSpacingBlocks, 4, 16);
            maximumDistantRainShafts = clamp(maximumDistantRainShafts, 64, 2_048);
            precipitationStreakDensity = clamp(precipitationStreakDensity, 0.10, 1.0);
            precipitationOpacity = clamp(precipitationOpacity, 0.15, 1.25);
            precipitationImpactDensity = clamp(precipitationImpactDensity, 0.0, 1.0);
            maximumPrecipitationImpacts = clamp(maximumPrecipitationImpacts, 32, 1_024);
            distantCloudDistanceBlocks = clamp(distantCloudDistanceBlocks, 384, 2_048);
            distantCloudSpacingBlocks = clamp(distantCloudSpacingBlocks, 24, 96);
            maximumDistantCloudTiles = clamp(maximumDistantCloudTiles, 64, 2_048);
            cloudShadowStrength = clamp(cloudShadowStrength, 0.0, 1.0);
            surfaceOverlayRadiusBlocks = clamp(surfaceOverlayRadiusBlocks, 8, 64);
            maximumSurfacePatches = clamp(maximumSurfacePatches, 32, 1_024);
        }

        private static int clamp(int value, int minimum, int maximum) {
            return Math.max(minimum, Math.min(maximum, value));
        }

        private static double clamp(double value, double minimum, double maximum) {
            double finite = Double.isFinite(value) ? value : minimum;
            return Math.max(minimum, Math.min(maximum, finite));
        }
    }
}
