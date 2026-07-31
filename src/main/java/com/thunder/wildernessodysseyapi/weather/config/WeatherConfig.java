package com.thunder.wildernessodysseyapi.weather.config;

import com.thunder.wildernessodysseyapi.weather.simulation.SimulationSettings;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
    public static final ModConfigSpec.BooleanValue SEASON_INTEGRATION_ENABLED;
    public static final ModConfigSpec.DoubleValue SEASON_TEMPERATURE_AMPLITUDE_CELSIUS;
    public static final ModConfigSpec.DoubleValue SEASON_HUMIDITY_AMPLITUDE;
    public static final ModConfigSpec.DoubleValue SEASON_STORMINESS_AMPLITUDE;
    public static final ModConfigSpec.BooleanValue LOCALIZED_LIGHTNING_ENABLED;
    public static final ModConfigSpec.IntValue LIGHTNING_CHECK_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue LIGHTNING_DIMENSION_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue LIGHTNING_CELL_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue LIGHTNING_CANDIDATE_RADIUS_BLOCKS;
    public static final ModConfigSpec.IntValue LIGHTNING_MAX_CANDIDATE_ATTEMPTS;
    public static final ModConfigSpec.DoubleValue LIGHTNING_MAXIMUM_CHANCE_PER_CHECK;
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
    private static final int DEFAULT_LIGHTNING_CHECK_INTERVAL = 20;
    private static final int DEFAULT_LIGHTNING_DIMENSION_COOLDOWN = 120;
    private static final int DEFAULT_LIGHTNING_CELL_COOLDOWN = 600;
    private static final int DEFAULT_LIGHTNING_CANDIDATE_RADIUS = 96;
    private static final int DEFAULT_LIGHTNING_MAX_CANDIDATES = 4;
    private static final double DEFAULT_LIGHTNING_MAXIMUM_CHANCE = 0.20;
    private static volatile DimensionSelection cachedDimensionSelection = DimensionSelection.DEFAULT;

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

        builder.comment("Optional read-only influence from installed season mods.")
                .push("seasons");
        SEASON_INTEGRATION_ENABLED = builder
                .comment("Allow Ecliptic Seasons or Serene Seasons to shift atmospheric temperature, humidity, and storm potential.")
                .define("enabled", true);
        SEASON_TEMPERATURE_AMPLITUDE_CELSIUS = builder
                .comment("Maximum temperate seasonal temperature shift in degrees Celsius.")
                .defineInRange("temperatureAmplitudeCelsius", 8.0, 0.0, 20.0);
        SEASON_HUMIDITY_AMPLITUDE = builder
                .comment("Maximum seasonal relative-humidity shift.")
                .defineInRange("humidityAmplitude", 0.12, 0.0, 0.40);
        SEASON_STORMINESS_AMPLITUDE = builder
                .comment("Maximum seasonal influence on convective storm development.")
                .defineInRange("storminessAmplitude", 0.18, 0.0, 0.50);
        builder.pop();

        builder.comment("Server-owned localized lightning scheduling.")
                .push("lightning");
        LOCALIZED_LIGHTNING_ENABLED = builder
                .comment("Allow authoritative atmospheric storms to create natural lightning. Vanilla global natural strikes remain suppressed in controlled dimensions when false.")
                .define("enabled", true);
        LIGHTNING_CHECK_INTERVAL_TICKS = builder
                .comment("Server ticks between bounded localized-lightning candidate checks.")
                .defineInRange("checkIntervalTicks", DEFAULT_LIGHTNING_CHECK_INTERVAL, 5, 1_200);
        LIGHTNING_DIMENSION_COOLDOWN_TICKS = builder
                .comment("Minimum ticks between successful localized strikes in one dimension.")
                .defineInRange("dimensionCooldownTicks", DEFAULT_LIGHTNING_DIMENSION_COOLDOWN, 20, 72_000);
        LIGHTNING_CELL_COOLDOWN_TICKS = builder
                .comment("Minimum ticks before the same atmospheric cell may receive another strike.")
                .defineInRange("cellCooldownTicks", DEFAULT_LIGHTNING_CELL_COOLDOWN, 20, 72_000);
        LIGHTNING_CANDIDATE_RADIUS_BLOCKS = builder
                .comment("Horizontal radius around active players used for loaded strike candidates.")
                .defineInRange("candidateRadiusBlocks", DEFAULT_LIGHTNING_CANDIDATE_RADIUS, 16, 256);
        LIGHTNING_MAX_CANDIDATE_ATTEMPTS = builder
                .comment("Maximum loaded candidate columns sampled per dimension check. At most one bolt can spawn per check.")
                .defineInRange("maxCandidateAttempts", DEFAULT_LIGHTNING_MAX_CANDIDATES, 1, 16);
        LIGHTNING_MAXIMUM_CHANCE_PER_CHECK = builder
                .comment("Maximum probability that one eligible candidate produces a strike during a check.")
                .defineInRange("maximumChancePerCheck", DEFAULT_LIGHTNING_MAXIMUM_CHANCE, 0.0, 1.0);
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
                .comment("PRESERVE_GLOBAL retains rain/thunder for unmigrated and Riftfall consumers while localized adapters use atmospheric cells. SUPPRESS_GLOBAL disables that global fallback.")
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

    /** Returns captured localized-lightning cadence and safety controls. */
    public static LightningSettings lightning() {
        try {
            return new LightningSettings(
                    LOCALIZED_LIGHTNING_ENABLED.get(),
                    LIGHTNING_CHECK_INTERVAL_TICKS.get(),
                    LIGHTNING_DIMENSION_COOLDOWN_TICKS.get(),
                    LIGHTNING_CELL_COOLDOWN_TICKS.get(),
                    LIGHTNING_CANDIDATE_RADIUS_BLOCKS.get(),
                    LIGHTNING_MAX_CANDIDATE_ATTEMPTS.get(),
                    LIGHTNING_MAXIMUM_CHANCE_PER_CHECK.get()
            );
        } catch (IllegalStateException exception) {
            return LightningSettings.DEFAULT;
        }
    }

    /** Returns bounded controls for optional Ecliptic/Serene season adapters. */
    public static SeasonSettings seasons() {
        try {
            return new SeasonSettings(
                    SEASON_INTEGRATION_ENABLED.get(),
                    SEASON_TEMPERATURE_AMPLITUDE_CELSIUS.get(),
                    SEASON_HUMIDITY_AMPLITUDE.get(),
                    SEASON_STORMINESS_AMPLITUDE.get()
            );
        } catch (IllegalStateException exception) {
            return SeasonSettings.DEFAULT;
        }
    }

    /**
     * Refreshes allocation-free dimension selection used from chunk tick hooks.
     *
     * <p>NeoForge invokes this after the server config loads or reloads. Other
     * scheduling and simulation values remain captured at their normal
     * throttled call sites.</p>
     */
    public static void reload() {
        cachedDimensionSelection = DimensionSelection.capture(scheduling());
    }

    /** Returns whether localized weather is enabled using the cached dimension selection. */
    public static boolean dimensionEnabled(ResourceKey<Level> dimension) {
        return dimension != null && cachedDimensionSelection.dimensionEnabled(dimension.location());
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

    /** Parsed immutable dimension ids used by the per-ticking-chunk hook. */
    private record DimensionSelection(
            boolean enabled,
            Set<ResourceLocation> allowlist,
            Set<ResourceLocation> denylist
    ) {
        private static final DimensionSelection DEFAULT = new DimensionSelection(
                true,
                Set.of(),
                Set.of()
        );

        private static DimensionSelection capture(SchedulingSettings settings) {
            SchedulingSettings safe = settings == null ? SchedulingSettings.DEFAULT : settings;
            return new DimensionSelection(
                    safe.enabled(),
                    parseLocations(safe.dimensionAllowlist()),
                    parseLocations(safe.dimensionDenylist())
            );
        }

        private boolean dimensionEnabled(ResourceLocation dimension) {
            return enabled
                    && dimension != null
                    && (allowlist.isEmpty() || allowlist.contains(dimension))
                    && !denylist.contains(dimension);
        }

        private static Set<ResourceLocation> parseLocations(List<String> identifiers) {
            if (identifiers == null || identifiers.isEmpty()) {
                return Set.of();
            }
            Set<ResourceLocation> parsed = new HashSet<>(identifiers.size());
            for (String identifier : identifiers) {
                ResourceLocation location = ResourceLocation.tryParse(identifier);
                if (location != null) {
                    parsed.add(location);
                }
            }
            return Set.copyOf(parsed);
        }
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

    /** Immutable balance controls shared by every optional season adapter. */
    public record SeasonSettings(
            boolean enabled,
            double temperatureAmplitudeCelsius,
            double humidityAmplitude,
            double storminessAmplitude
    ) {
        public static final SeasonSettings DEFAULT = new SeasonSettings(true, 8.0, 0.12, 0.18);

        public SeasonSettings {
            temperatureAmplitudeCelsius = clamp(temperatureAmplitudeCelsius, 0.0, 20.0);
            humidityAmplitude = clamp(humidityAmplitude, 0.0, 0.40);
            storminessAmplitude = clamp(storminessAmplitude, 0.0, 0.50);
        }

        private static double clamp(double value, double minimum, double maximum) {
            double finite = Double.isFinite(value) ? value : minimum;
            return Math.max(minimum, Math.min(maximum, finite));
        }
    }

    /**
     * Immutable, clamp-safe localized-lightning controls.
     *
     * <p>The cell cooldown is never allowed below the dimension cooldown, so
     * a single atmospheric cell cannot bypass the global anti-spam bound.</p>
     */
    public record LightningSettings(
            boolean enabled,
            int checkIntervalTicks,
            int dimensionCooldownTicks,
            int cellCooldownTicks,
            int candidateRadiusBlocks,
            int maxCandidateAttempts,
            double maximumChancePerCheck
    ) {
        public static final LightningSettings DEFAULT = new LightningSettings(
                true,
                DEFAULT_LIGHTNING_CHECK_INTERVAL,
                DEFAULT_LIGHTNING_DIMENSION_COOLDOWN,
                DEFAULT_LIGHTNING_CELL_COOLDOWN,
                DEFAULT_LIGHTNING_CANDIDATE_RADIUS,
                DEFAULT_LIGHTNING_MAX_CANDIDATES,
                DEFAULT_LIGHTNING_MAXIMUM_CHANCE
        );

        public LightningSettings {
            checkIntervalTicks = clamp(checkIntervalTicks, 5, 1_200);
            dimensionCooldownTicks = clamp(dimensionCooldownTicks, 20, 72_000);
            cellCooldownTicks = Math.max(
                    dimensionCooldownTicks,
                    clamp(cellCooldownTicks, 20, 72_000)
            );
            candidateRadiusBlocks = clamp(candidateRadiusBlocks, 16, 256);
            maxCandidateAttempts = clamp(maxCandidateAttempts, 1, 16);
            maximumChancePerCheck = clamp(
                    Double.isFinite(maximumChancePerCheck)
                            ? maximumChancePerCheck
                            : DEFAULT_LIGHTNING_MAXIMUM_CHANCE,
                    0.0,
                    1.0
            );
        }

        private static int clamp(int value, int minimum, int maximum) {
            return Math.max(minimum, Math.min(maximum, value));
        }

        private static double clamp(double value, double minimum, double maximum) {
            return Math.max(minimum, Math.min(maximum, value));
        }
    }
}
