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

    private static final Settings DEFAULTS = new Settings(
            true,
            true,
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
            768
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
                MAXIMUM_DISTANT_RAIN_SHAFTS.get()
        );
    }

    /** Immutable and defensively bounded renderer settings. */
    public record Settings(
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
                    maximumDistantRainShafts
            );
        }

        public Settings {
            renderDistanceBlocks = clamp(renderDistanceBlocks, 96, 512);
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
