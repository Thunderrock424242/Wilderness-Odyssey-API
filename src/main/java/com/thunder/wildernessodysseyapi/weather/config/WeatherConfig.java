package com.thunder.wildernessodysseyapi.weather.config;

import com.thunder.wildernessodysseyapi.weather.simulation.SimulationSettings;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Server configuration for localized atmospheric weather.
 *
 * <p>Calculation values are copied into immutable {@link SimulationSettings}
 * and scheduling values into {@link SchedulingSettings}. That separation lets
 * asynchronous calculations use captured values without reading live config.</p>
 */
public final class WeatherConfig {
    public static final ModConfigSpec CONFIG_SPEC;

    public static final ModConfigSpec.BooleanValue WEATHER_SYSTEM_ENABLED;
    public static final ModConfigSpec.IntValue ATMOSPHERIC_CELL_SIZE;
    public static final ModConfigSpec.IntValue SIMULATION_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue ACTIVE_SIMULATION_RADIUS;
    public static final ModConfigSpec.IntValue INACTIVE_CELL_GRACE_PERIOD_TICKS;
    public static final ModConfigSpec.IntValue ENVIRONMENT_RESAMPLE_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue SNAPSHOT_SYNC_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue MAX_PERSISTED_CELLS;
    public static final ModConfigSpec.DoubleValue SIMULATION_SPEED;
    public static final ModConfigSpec.DoubleValue HUMIDITY_TRANSPORT_RATE;
    public static final ModConfigSpec.DoubleValue TEMPERATURE_TRANSPORT_RATE;
    public static final ModConfigSpec.DoubleValue PRESSURE_EQUALIZATION_RATE;
    public static final ModConfigSpec.DoubleValue EVAPORATION_STRENGTH;
    public static final ModConfigSpec.DoubleValue CLOUD_FORMATION_THRESHOLD;
    public static final ModConfigSpec.DoubleValue PRECIPITATION_THRESHOLD;
    public static final ModConfigSpec.DoubleValue STORM_FORMATION_THRESHOLD;
    public static final ModConfigSpec.DoubleValue MAXIMUM_PRECIPITATION_INTENSITY;
    public static final ModConfigSpec.DoubleValue RANDOM_VARIATION;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_ALLOWLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_DENYLIST;
    public static final ModConfigSpec.EnumValue<VanillaWeatherCompatibilityMode> VANILLA_WEATHER_COMPATIBILITY_MODE;
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;

    private static final int DEFAULT_CELL_SIZE = 256;
    private static final int DEFAULT_SIMULATION_INTERVAL = 60;
    private static final int DEFAULT_ACTIVE_RADIUS = 2;
    private static final int DEFAULT_INACTIVE_GRACE = 2_400;
    private static final int DEFAULT_ENVIRONMENT_RESAMPLE_INTERVAL = 400;
    private static final int DEFAULT_SNAPSHOT_SYNC_INTERVAL = 60;
    private static final int DEFAULT_MAX_PERSISTED_CELLS = 4_096;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Server-authoritative localized atmospheric weather.")
                .push("weather");

        WEATHER_SYSTEM_ENABLED = builder
                .comment("Master switch for Wilderness Odyssey localized weather.")
                .define("enabled", true);
        ATMOSPHERIC_CELL_SIZE = builder
                .comment("Horizontal atmospheric cell width in blocks. Changing this resets stored cells because coordinates no longer describe the same regions.")
                .defineInRange("atmosphericCellSize", DEFAULT_CELL_SIZE, 16, 4_096);
        SIMULATION_INTERVAL_TICKS = builder
                .comment("Server ticks between throttled atmospheric simulation passes.")
                .defineInRange("simulationIntervalTicks", DEFAULT_SIMULATION_INTERVAL, 10, 1_200);
        ACTIVE_SIMULATION_RADIUS = builder
                .comment("Atmospheric cell radius simulated around each active player, excluding no additional chunk loads.")
                .defineInRange("activeSimulationRadius", DEFAULT_ACTIVE_RADIUS, 0, 16);
        INACTIVE_CELL_GRACE_PERIOD_TICKS = builder
                .comment("Ticks recently active cells remain eligible for simulation after players leave.")
                .defineInRange("inactiveCellGracePeriodTicks", DEFAULT_INACTIVE_GRACE, 0, 1_728_000);
        ENVIRONMENT_RESAMPLE_INTERVAL_TICKS = builder
                .comment("Ticks between cached biome, terrain, daylight, and water-influence refreshes.")
                .defineInRange("environmentResampleIntervalTicks", DEFAULT_ENVIRONMENT_RESAMPLE_INTERVAL, 20, 72_000);
        SNAPSHOT_SYNC_INTERVAL_TICKS = builder
                .comment("Ticks between compact changed-cell synchronization passes.")
                .defineInRange("snapshotSyncIntervalTicks", DEFAULT_SNAPSHOT_SYNC_INTERVAL, 5, 1_200);
        MAX_PERSISTED_CELLS = builder
                .comment("Maximum meaningful atmospheric cells retained and persisted per dimension.")
                .defineInRange("maxPersistedCells", DEFAULT_MAX_PERSISTED_CELLS, 64, 65_536);

        builder.comment("Pure atmospheric calculation controls.")
                .push("simulation");
        SIMULATION_SPEED = builder
                .comment("Multiplier applied to one atmospheric update. Zero freezes evolution without deleting state.")
                .defineInRange("speed", 1.0, 0.0, 8.0);
        HUMIDITY_TRANSPORT_RATE = builder
                .comment("Fraction of vapor and cloud moisture transported downwind per nominal update.")
                .defineInRange("humidityTransportRate", 0.18, 0.0, 1.0);
        TEMPERATURE_TRANSPORT_RATE = builder
                .comment("Fraction of neighboring air temperature transported downwind per nominal update.")
                .defineInRange("temperatureTransportRate", 0.10, 0.0, 1.0);
        PRESSURE_EQUALIZATION_RATE = builder
                .comment("Rate at which pressure equalizes and pressure gradients accelerate wind.")
                .defineInRange("pressureEqualizationRate", 0.20, 0.0, 1.0);
        EVAPORATION_STRENGTH = builder
                .comment("Humidity gain from cached surface water and humid biomes.")
                .defineInRange("evaporationStrength", 0.12, 0.0, 1.0);
        CLOUD_FORMATION_THRESHOLD = builder
                .comment("Relative-humidity baseline above which vapor condenses into cloud water.")
                .defineInRange("cloudFormationThreshold", 0.72, 0.05, 0.99);
        PRECIPITATION_THRESHOLD = builder
                .comment("Cloud-water amount required before rain or snow begins.")
                .defineInRange("precipitationThreshold", 0.58, 0.05, 0.99);
        STORM_FORMATION_THRESHOLD = builder
                .comment("Moist instability potential required for storm-energy growth.")
                .defineInRange("stormFormationThreshold", 0.42, 0.0, 1.0);
        MAXIMUM_PRECIPITATION_INTENSITY = builder
                .comment("Upper bound for localized rain or snow intensity.")
                .defineInRange("maximumPrecipitationIntensity", 1.0, 0.0, 1.0);
        RANDOM_VARIATION = builder
                .comment("Maximum deterministic local atmospheric variation; this never uses per-tick random preset switching.")
                .defineInRange("randomVariation", 0.04, 0.0, 0.25);
        builder.pop();

        builder.comment("Dimension and vanilla-weather compatibility controls.")
                .push("compatibility");
        DIMENSION_ALLOWLIST = builder
                .comment("Dimension ids allowed to run localized weather. Empty allows every dimension not denied.")
                .defineListAllowEmpty("dimensionAllowlist", List.of(), WeatherConfig::isDimensionIdentifier);
        DIMENSION_DENYLIST = builder
                .comment("Dimension ids that must not run localized weather. Deny entries override the allowlist.")
                .defineListAllowEmpty("dimensionDenylist", List.of(), WeatherConfig::isDimensionIdentifier);
        VANILLA_WEATHER_COMPATIBILITY_MODE = builder
                .comment("PRESERVE_GLOBAL keeps vanilla/Riftfall gameplay state while clients use local visuals. SUPPRESS_GLOBAL disables global precipitation.")
                .defineEnum("vanillaWeatherCompatibilityMode", VanillaWeatherCompatibilityMode.PRESERVE_GLOBAL);
        builder.pop();

        DEBUG_LOGGING = builder
                .comment("Enable concise weather scheduling and persistence diagnostics. Disabled during normal play.")
                .define("debugLogging", false);

        builder.pop();
        CONFIG_SPEC = builder.build();
    }

    private WeatherConfig() {
    }

    /** Returns a captured, clamp-safe calculation contract. */
    public static SimulationSettings settings() {
        try {
            return new SimulationSettings(
                    SIMULATION_SPEED.get(),
                    HUMIDITY_TRANSPORT_RATE.get(),
                    TEMPERATURE_TRANSPORT_RATE.get(),
                    PRESSURE_EQUALIZATION_RATE.get(),
                    EVAPORATION_STRENGTH.get(),
                    CLOUD_FORMATION_THRESHOLD.get(),
                    PRECIPITATION_THRESHOLD.get(),
                    STORM_FORMATION_THRESHOLD.get(),
                    MAXIMUM_PRECIPITATION_INTENSITY.get(),
                    RANDOM_VARIATION.get()
            );
        } catch (IllegalStateException exception) {
            return SimulationSettings.DEFAULT;
        }
    }

    /** Returns captured scheduling, dimension, and compatibility controls. */
    public static SchedulingSettings scheduling() {
        try {
            return new SchedulingSettings(
                    WEATHER_SYSTEM_ENABLED.get(),
                    ATMOSPHERIC_CELL_SIZE.get(),
                    SIMULATION_INTERVAL_TICKS.get(),
                    ACTIVE_SIMULATION_RADIUS.get(),
                    INACTIVE_CELL_GRACE_PERIOD_TICKS.get(),
                    ENVIRONMENT_RESAMPLE_INTERVAL_TICKS.get(),
                    SNAPSHOT_SYNC_INTERVAL_TICKS.get(),
                    MAX_PERSISTED_CELLS.get(),
                    copyStrings(DIMENSION_ALLOWLIST.get()),
                    copyStrings(DIMENSION_DENYLIST.get()),
                    VANILLA_WEATHER_COMPATIBILITY_MODE.get(),
                    DEBUG_LOGGING.get()
            );
        } catch (IllegalStateException exception) {
            return SchedulingSettings.DEFAULT;
        }
    }

    /** Returns whether localized weather is enabled for a dimension. */
    public static boolean dimensionEnabled(ResourceKey<Level> dimension) {
        return dimension != null && scheduling().dimensionEnabled(dimension.location());
    }

    /** Returns the configured vanilla global-weather compatibility behavior. */
    public static VanillaWeatherCompatibilityMode compatibilityMode() {
        return scheduling().compatibilityMode();
    }

    /** Returns whether verbose weather diagnostics are enabled. */
    public static boolean debugLogging() {
        return scheduling().debugLogging();
    }

    private static boolean isDimensionIdentifier(Object value) {
        return value instanceof String string && ResourceLocation.tryParse(string) != null;
    }

    private static List<String> copyStrings(List<? extends String> source) {
        return source == null ? List.of() : List.copyOf(source);
    }

    /**
     * Immutable, clamp-safe server scheduling and persistence controls.
     */
    public record SchedulingSettings(
            boolean enabled,
            int cellSize,
            int simulationIntervalTicks,
            int activeSimulationRadius,
            int inactiveCellGracePeriodTicks,
            int environmentResampleIntervalTicks,
            int snapshotSyncIntervalTicks,
            int maxPersistedCells,
            List<String> dimensionAllowlist,
            List<String> dimensionDenylist,
            VanillaWeatherCompatibilityMode compatibilityMode,
            boolean debugLogging
    ) {
        public static final SchedulingSettings DEFAULT = new SchedulingSettings(
                true,
                DEFAULT_CELL_SIZE,
                DEFAULT_SIMULATION_INTERVAL,
                DEFAULT_ACTIVE_RADIUS,
                DEFAULT_INACTIVE_GRACE,
                DEFAULT_ENVIRONMENT_RESAMPLE_INTERVAL,
                DEFAULT_SNAPSHOT_SYNC_INTERVAL,
                DEFAULT_MAX_PERSISTED_CELLS,
                List.of(),
                List.of(),
                VanillaWeatherCompatibilityMode.PRESERVE_GLOBAL,
                false
        );

        public SchedulingSettings {
            cellSize = clamp(cellSize, 16, 4_096);
            simulationIntervalTicks = clamp(simulationIntervalTicks, 10, 1_200);
            activeSimulationRadius = clamp(activeSimulationRadius, 0, 16);
            inactiveCellGracePeriodTicks = clamp(inactiveCellGracePeriodTicks, 0, 1_728_000);
            environmentResampleIntervalTicks = clamp(environmentResampleIntervalTicks, 20, 72_000);
            snapshotSyncIntervalTicks = clamp(snapshotSyncIntervalTicks, 5, 1_200);
            maxPersistedCells = clamp(maxPersistedCells, 64, 65_536);
            dimensionAllowlist = sanitizeDimensions(dimensionAllowlist);
            dimensionDenylist = sanitizeDimensions(dimensionDenylist);
            compatibilityMode = compatibilityMode == null
                    ? VanillaWeatherCompatibilityMode.PRESERVE_GLOBAL
                    : compatibilityMode;
        }

        /** Applies allowlist-first and denylist-last dimension selection. */
        public boolean dimensionEnabled(ResourceLocation dimension) {
            if (!enabled || dimension == null) {
                return false;
            }
            String id = dimension.toString();
            boolean allowed = dimensionAllowlist.isEmpty() || dimensionAllowlist.contains(id);
            return allowed && !dimensionDenylist.contains(id);
        }

        private static List<String> sanitizeDimensions(List<String> dimensions) {
            if (dimensions == null || dimensions.isEmpty()) {
                return List.of();
            }
            List<String> sanitized = new ArrayList<>(dimensions.size());
            for (String candidate : dimensions) {
                if (candidate == null) {
                    continue;
                }
                String normalized = candidate.trim().toLowerCase(Locale.ROOT);
                if (ResourceLocation.tryParse(normalized) != null && !sanitized.contains(normalized)) {
                    sanitized.add(normalized);
                }
            }
            return List.copyOf(sanitized);
        }

        private static int clamp(int value, int minimum, int maximum) {
            return Math.max(minimum, Math.min(maximum, value));
        }
    }
}
