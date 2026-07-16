package com.thunder.wildernessodysseyapi.weather.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Defines client-only quality and motion limits for localized cloud rendering.
 *
 * <p>The server remains authoritative for atmospheric state. These options
 * change only how much of that synchronized field the client visualizes.</p>
 */
public final class WeatherRenderingConfig {

    public static final ModConfigSpec CONFIG_SPEC;
    public static final ModConfigSpec.BooleanValue ENABLE_LOCALIZED_CLOUDS;
    public static final ModConfigSpec.IntValue RENDER_DISTANCE_BLOCKS;
    public static final ModConfigSpec.IntValue REBUILD_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue WIND_DETAIL_SPEED_BLOCKS_PER_SECOND;
    public static final ModConfigSpec.IntValue MAXIMUM_CLOUD_TILES;
    public static final ModConfigSpec.DoubleValue OPACITY_MULTIPLIER;

    private static final Settings DEFAULTS = new Settings(true, 384, 5, 6.0, 4096, 1.0);
    private static volatile Settings activeSettings = DEFAULTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Client-side rendering options for server-authored localized clouds.")
                .push("localized_clouds");
        ENABLE_LOCALIZED_CLOUDS = builder
                .comment("Replace vanilla's global cloud sheet with weather-cell cloud masses.")
                .define("enabled", true);
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
                RENDER_DISTANCE_BLOCKS.get(),
                REBUILD_INTERVAL_TICKS.get(),
                WIND_DETAIL_SPEED_BLOCKS_PER_SECOND.get(),
                MAXIMUM_CLOUD_TILES.get(),
                OPACITY_MULTIPLIER.get()
        );
    }

    /** Immutable and defensively bounded renderer settings. */
    public record Settings(
            boolean enabled,
            int renderDistanceBlocks,
            int rebuildIntervalTicks,
            double windDetailSpeedBlocksPerSecond,
            int maximumCloudTiles,
            double opacityMultiplier
    ) {
        public Settings {
            renderDistanceBlocks = clamp(renderDistanceBlocks, 96, 512);
            rebuildIntervalTicks = clamp(rebuildIntervalTicks, 2, 40);
            windDetailSpeedBlocksPerSecond = clamp(windDetailSpeedBlocksPerSecond, 0.0, 24.0);
            maximumCloudTiles = clamp(maximumCloudTiles, 256, 8192);
            opacityMultiplier = clamp(opacityMultiplier, 0.25, 1.25);
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
