package com.thunder.wildernessodysseyapi.weather.config;

import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import com.thunder.wildernessodysseyapi.weather.api.WindSettings;
import com.thunder.wildernessodysseyapi.weather.simulation.SimulationSettings;
import com.thunder.wildernessodysseyapi.weather.integration.WeatherOwnershipCoordinator;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemTracker;
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
    public static ModConfigSpec CONFIG_SPEC;

    public static ModConfigSpec.BooleanValue WEATHER_SYSTEM_ENABLED;
    public static ModConfigSpec.IntValue ATMOSPHERIC_CELL_SIZE;
    public static ModConfigSpec.IntValue SIMULATION_INTERVAL_TICKS;
    public static ModConfigSpec.IntValue ACTIVE_SIMULATION_RADIUS;
    public static ModConfigSpec.IntValue INACTIVE_CELL_GRACE_PERIOD_TICKS;
    public static ModConfigSpec.IntValue ENVIRONMENT_RESAMPLE_INTERVAL_TICKS;
    public static ModConfigSpec.IntValue SNAPSHOT_SYNC_INTERVAL_TICKS;
    public static ModConfigSpec.IntValue MAX_PERSISTED_CELLS;
    public static ModConfigSpec.DoubleValue SIMULATION_SPEED;
    public static ModConfigSpec.DoubleValue HUMIDITY_TRANSPORT_RATE;
    public static ModConfigSpec.DoubleValue TEMPERATURE_TRANSPORT_RATE;
    public static ModConfigSpec.DoubleValue PRESSURE_EQUALIZATION_RATE;
    public static ModConfigSpec.DoubleValue WEATHER_FRONT_STRENGTH;
    public static ModConfigSpec.DoubleValue EVAPORATION_STRENGTH;
    public static ModConfigSpec.DoubleValue CLOUD_FORMATION_THRESHOLD;
    public static ModConfigSpec.DoubleValue PRECIPITATION_THRESHOLD;
    public static ModConfigSpec.DoubleValue STORM_FORMATION_THRESHOLD;
    public static ModConfigSpec.DoubleValue MAXIMUM_PRECIPITATION_INTENSITY;
    public static ModConfigSpec.DoubleValue RANDOM_VARIATION;
    public static ModConfigSpec.BooleanValue WIND_ENABLED;
    public static ModConfigSpec.DoubleValue BASE_WIND_STRENGTH;
    public static ModConfigSpec.DoubleValue GUST_FREQUENCY;
    public static ModConfigSpec.DoubleValue GUST_STRENGTH;
    public static ModConfigSpec.DoubleValue STORM_WIND_MULTIPLIER;
    public static ModConfigSpec.DoubleValue MAX_WIND_SPEED;
    public static ModConfigSpec.BooleanValue SEASON_INTEGRATION_ENABLED;
    public static ModConfigSpec.DoubleValue SEASON_TEMPERATURE_AMPLITUDE_CELSIUS;
    public static ModConfigSpec.DoubleValue SEASON_HUMIDITY_AMPLITUDE;
    public static ModConfigSpec.DoubleValue SEASON_STORMINESS_AMPLITUDE;
    public static ModConfigSpec.BooleanValue COLD_SWEAT_INTEGRATION_ENABLED;
    public static ModConfigSpec.DoubleValue COLD_SWEAT_MAXIMUM_OFFSET_CELSIUS;
    public static ModConfigSpec.BooleanValue THIRST_WAS_TAKEN_INTEGRATION_ENABLED;
    public static ModConfigSpec.IntValue THIRST_WEATHER_INTERVAL_TICKS;
    public static ModConfigSpec.DoubleValue THIRST_MAXIMUM_EXHAUSTION_PER_INTERVAL;
    public static ModConfigSpec.BooleanValue LOCALIZED_LIGHTNING_ENABLED;
    public static ModConfigSpec.IntValue LIGHTNING_CHECK_INTERVAL_TICKS;
    public static ModConfigSpec.IntValue LIGHTNING_DIMENSION_COOLDOWN_TICKS;
    public static ModConfigSpec.IntValue LIGHTNING_CELL_COOLDOWN_TICKS;
    public static ModConfigSpec.IntValue LIGHTNING_CANDIDATE_RADIUS_BLOCKS;
    public static ModConfigSpec.IntValue LIGHTNING_MAX_CANDIDATE_ATTEMPTS;
    public static ModConfigSpec.DoubleValue LIGHTNING_MAXIMUM_CHANCE_PER_CHECK;
    public static ModConfigSpec.BooleanValue WILDFIRES_ENABLED;
    public static ModConfigSpec.IntValue WILDFIRE_CHECK_INTERVAL_TICKS;
    public static ModConfigSpec.IntValue WILDFIRE_DIMENSION_COOLDOWN_TICKS;
    public static ModConfigSpec.IntValue WILDFIRE_CELL_COOLDOWN_TICKS;
    public static ModConfigSpec.IntValue WILDFIRE_CANDIDATE_CHUNK_RADIUS;
    public static ModConfigSpec.IntValue WILDFIRE_CANDIDATE_CHUNKS_PER_PLAYER;
    public static ModConfigSpec.IntValue WILDFIRE_EMBER_RANGE_BLOCKS;
    public static ModConfigSpec.IntValue WILDFIRE_TARGET_ATTEMPTS;
    public static ModConfigSpec.DoubleValue WILDFIRE_MAXIMUM_CHANCE_PER_CHECK;
    public static ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_ALLOWLIST;
    public static ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_DENYLIST;
    public static ModConfigSpec.EnumValue<VanillaWeatherCompatibilityMode> VANILLA_WEATHER_COMPATIBILITY_MODE;
    public static ModConfigSpec.EnumValue<WeatherOwnershipMode> WEATHER_OWNERSHIP_MODE;
    public static ModConfigSpec.ConfigValue<List<? extends String>> EXTERNAL_WEATHER_MOD_IDS;
    public static ModConfigSpec.BooleanValue PERSISTENT_SYSTEMS_ENABLED;
    public static ModConfigSpec.IntValue MAXIMUM_WEATHER_SYSTEMS;
    public static ModConfigSpec.DoubleValue WEATHER_SYSTEM_MOVEMENT_SPEED;
    public static ModConfigSpec.BooleanValue WEATHER_SYSTEM_SPLITTING_ENABLED;
    public static ModConfigSpec.BooleanValue SURFACE_WEATHERING_ENABLED;
    public static ModConfigSpec.IntValue SURFACE_WEATHERING_INTERVAL_TICKS;
    public static ModConfigSpec.IntValue SURFACE_WEATHERING_ATTEMPTS_PER_PLAYER;
    public static ModConfigSpec.IntValue MAXIMUM_SNOW_LAYERS;
    public static ModConfigSpec.BooleanValue SEVERE_WEATHER_ENABLED;
    public static ModConfigSpec.BooleanValue TORNADOES_ENABLED;
    public static ModConfigSpec.BooleanValue CYCLONES_ENABLED;
    public static ModConfigSpec.BooleanValue SEVERE_BLOCK_DAMAGE_ENABLED;
    public static ModConfigSpec.DoubleValue SEVERE_ENTITY_WIND_STRENGTH;
    public static ModConfigSpec.BooleanValue DEBUG_LOGGING;

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
    private static final int DEFAULT_WILDFIRE_CHECK_INTERVAL = 600;
    private static final int DEFAULT_WILDFIRE_DIMENSION_COOLDOWN = 48_000;
    private static final int DEFAULT_WILDFIRE_CELL_COOLDOWN = 168_000;
    private static final int DEFAULT_WILDFIRE_CANDIDATE_CHUNK_RADIUS = 2;
    private static final int DEFAULT_WILDFIRE_CANDIDATE_CHUNKS_PER_PLAYER = 4;
    private static final int DEFAULT_WILDFIRE_EMBER_RANGE = 10;
    private static final int DEFAULT_WILDFIRE_TARGET_ATTEMPTS = 12;
    private static final double DEFAULT_WILDFIRE_MAXIMUM_CHANCE = 0.01;
    private static volatile DimensionSelection cachedDimensionSelection = DimensionSelection.DEFAULT;

    static {
        WildernessConfigSpecs.initialize();
    }

    /** Defines weather categories in the unified server config. */
    public static void define(ModConfigSpec.Builder builder) {
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
        WEATHER_FRONT_STRENGTH = builder
                .comment("Strength of lift, gusts, and storm development where contrasting air masses form weather fronts.")
                .defineInRange("weatherFrontStrength", 0.75, 0.0, 1.0);
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

        builder.comment("Deterministic regional wind derived from localized atmosphere cells.")
                .push("wind");
        WIND_ENABLED = builder
                .comment("Expose derived regional wind to server gameplay and synchronized client effects.")
                .define("windEnabled", true);
        BASE_WIND_STRENGTH = builder
                .comment("Clear-weather ambient wind speed in blocks per second.")
                .defineInRange("baseWindStrength", 2.5, 0.0, 64.0);
        GUST_FREQUENCY = builder
                .comment("Average coherent regional gust cycles per Minecraft minute (1,200 ticks).")
                .defineInRange("gustFrequency", 2.0, 0.0, 60.0);
        GUST_STRENGTH = builder
                .comment("Maximum additive gust speed in blocks per second before the global speed cap.")
                .defineInRange("gustStrength", 5.0, 0.0, 64.0);
        STORM_WIND_MULTIPLIER = builder
                .comment("Wind amplification reached by maximum localized storm severity.")
                .defineInRange("stormWindMultiplier", 1.8, 0.0, 4.0);
        MAX_WIND_SPEED = builder
                .comment("Hard cap for sustained wind plus gusts in blocks per second.")
                .defineInRange("maxWindSpeed", 24.0, 0.0, 64.0);
        builder.pop();

        builder.comment("Optional read-only influence from installed season mods.")
                .push("seasons");
        SEASON_INTEGRATION_ENABLED = builder
                .comment("Allow Homeostatic Seasons or Serene Seasons to shift atmospheric temperature, humidity, and storm potential.")
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

        builder.comment("Optional survival-mod responses to authoritative local weather.")
                .push("survivalIntegrations");
        COLD_SWEAT_INTEGRATION_ENABLED = builder
                .comment("Allow localized air temperature, wind, humidity, and precipitation to influence Cold Sweat world temperature.")
                .define("coldSweatEnabled", true);
        COLD_SWEAT_MAXIMUM_OFFSET_CELSIUS = builder
                .comment("Maximum absolute weather offset applied to Cold Sweat in degrees Celsius.")
                .defineInRange("coldSweatMaximumOffsetCelsius", 12.0, 0.0, 30.0);
        THIRST_WAS_TAKEN_INTEGRATION_ENABLED = builder
                .comment("Allow outdoor heat, dry air, drought, and wind to add bounded Thirst Was Taken exhaustion.")
                .define("thirstWasTakenEnabled", true);
        THIRST_WEATHER_INTERVAL_TICKS = builder
                .comment("Ticks between bounded weather-exposure updates for Thirst Was Taken players.")
                .defineInRange("thirstIntervalTicks", 40, 20, 1_200);
        THIRST_MAXIMUM_EXHAUSTION_PER_INTERVAL = builder
                .comment("Maximum thirst exhaustion added per weather interval under extreme exposed conditions.")
                .defineInRange("thirstMaximumExhaustionPerInterval", 0.025, 0.0, 0.25);
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

        builder.comment("Rare campfire-ember wildfires during extreme summer or dry-season drought.")
                .push("wildfire");
        WILDFIRES_ENABLED = builder
                .comment("Allow exposed normal campfires to throw rare downwind embers into tagged natural fuel. Vanilla fire owns all later spread and damage.")
                .define("enabled", true);
        WILDFIRE_CHECK_INTERVAL_TICKS = builder
                .comment("Server ticks between bounded wildfire candidate checks.")
                .defineInRange("checkIntervalTicks", DEFAULT_WILDFIRE_CHECK_INTERVAL, 100, 72_000);
        WILDFIRE_DIMENSION_COOLDOWN_TICKS = builder
                .comment("Minimum ticks between successful campfire ignitions in one dimension.")
                .defineInRange("dimensionCooldownTicks", DEFAULT_WILDFIRE_DIMENSION_COOLDOWN, 1_200, 1_728_000);
        WILDFIRE_CELL_COOLDOWN_TICKS = builder
                .comment("Minimum ticks before the same atmospheric cell can start another campfire wildfire.")
                .defineInRange("cellCooldownTicks", DEFAULT_WILDFIRE_CELL_COOLDOWN, 1_200, 1_728_000);
        WILDFIRE_CANDIDATE_CHUNK_RADIUS = builder
                .comment("Loaded chunk radius around players rotated through by the wildfire scheduler.")
                .defineInRange("candidateChunkRadius", DEFAULT_WILDFIRE_CANDIDATE_CHUNK_RADIUS, 0, 8);
        WILDFIRE_CANDIDATE_CHUNKS_PER_PLAYER = builder
                .comment("Maximum loaded chunks inspected per player during one wildfire check.")
                .defineInRange("candidateChunksPerPlayer", DEFAULT_WILDFIRE_CANDIDATE_CHUNKS_PER_PLAYER, 1, 16);
        WILDFIRE_EMBER_RANGE_BLOCKS = builder
                .comment("Maximum downwind distance a successful campfire ember may travel.")
                .defineInRange("emberRangeBlocks", DEFAULT_WILDFIRE_EMBER_RANGE, 4, 24);
        WILDFIRE_TARGET_ATTEMPTS = builder
                .comment("Maximum exposed tagged-fuel columns sampled after the single ignition roll succeeds.")
                .defineInRange("targetAttempts", DEFAULT_WILDFIRE_TARGET_ATTEMPTS, 1, 32);
        WILDFIRE_MAXIMUM_CHANCE_PER_CHECK = builder
                .comment("Maximum probability for the strongest eligible campfire in one due check. Actual chance is reduced by local fire risk.")
                .defineInRange("maximumChancePerCheck", DEFAULT_WILDFIRE_MAXIMUM_CHANCE, 0.0, 1.0);
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
                .comment("SUPPRESS_GLOBAL prevents Minecraft's global rain/thunder scheduler from competing with localized weather. PRESERVE_GLOBAL is a legacy mod-compatibility fallback.")
                .defineEnum("vanillaWeatherCompatibilityMode", VanillaWeatherCompatibilityMode.SUPPRESS_GLOBAL);
        WEATHER_OWNERSHIP_MODE = builder
                .comment("AUTO yields to configured external weather mods, WILDERNESS forces this system, and EXTERNAL disables Wilderness weather ownership.")
                .defineEnum("weatherOwnershipMode", WeatherOwnershipMode.AUTO);
        EXTERNAL_WEATHER_MOD_IDS = builder
                .comment("Mod ids treated as full weather-system owners in AUTO mode. Season-only mods do not belong here.")
                .defineListAllowEmpty(
                        "externalWeatherModIds",
                        List.of("weather2", "simpleclouds", "betterweather"),
                        WeatherConfig::isModIdentifier
                );
        builder.pop();

        builder.comment("Persistent fronts, ground response, and opt-in severe-weather effects.")
                .push("systems");
        PERSISTENT_SYSTEMS_ENABLED = builder
                .comment("Track moving storm and front identities that can strengthen, merge, split, and dissipate.")
                .define("persistentSystemsEnabled", true);
        MAXIMUM_WEATHER_SYSTEMS = builder
                .comment("Maximum persistent storm and front identities retained per dimension.")
                .defineInRange("maximumWeatherSystems", 48, 1, 256);
        WEATHER_SYSTEM_MOVEMENT_SPEED = builder
                .comment("Maximum block movement per second for a normalized atmospheric-system wind.")
                .defineInRange("movementBlocksPerSecond", 3.0, 0.0, 16.0);
        WEATHER_SYSTEM_SPLITTING_ENABLED = builder
                .comment("Allow sufficiently organized storm cells to split into child cells.")
                .define("splittingEnabled", true);
        builder.pop();

        builder.comment("Bounded server-side snow and freezing response.")
                .push("surface");
        SURFACE_WEATHERING_ENABLED = builder
                .comment("Allow accumulated snow layers and temporary frosted ice in loaded player areas.")
                .define("enabled", true);
        SURFACE_WEATHERING_INTERVAL_TICKS = builder
                .comment("Ticks between bounded surface-weathering passes.")
                .defineInRange("intervalTicks", 40, 10, 1_200);
        SURFACE_WEATHERING_ATTEMPTS_PER_PLAYER = builder
                .comment("Maximum loaded columns sampled around each player per surface pass.")
                .defineInRange("attemptsPerPlayer", 4, 1, 32);
        MAXIMUM_SNOW_LAYERS = builder
                .comment("Maximum accumulated vanilla snow layers created by weathering.")
                .defineInRange("maximumSnowLayers", 5, 1, 8);
        builder.pop();

        builder.comment("Rare severe-weather effects. Block damage is deliberately disabled by default.")
                .push("severe");
        SEVERE_WEATHER_ENABLED = builder
                .comment("Allow persistent systems to develop tornado or cyclone identities.")
                .define("enabled", true);
        TORNADOES_ENABLED = builder
                .comment("Allow highly sheared supercells to develop tornado effects.")
                .define("tornadoesEnabled", true);
        CYCLONES_ENABLED = builder
                .comment("Allow warm ocean-fed low pressure systems to develop cyclone effects.")
                .define("cyclonesEnabled", true);
        SEVERE_BLOCK_DAMAGE_ENABLED = builder
                .comment("Allow severe systems to remove a very small number of exposed leaves and plants. Disabled by default.")
                .define("blockDamageEnabled", false);
        SEVERE_ENTITY_WIND_STRENGTH = builder
                .comment("Maximum bounded push applied to nearby entities by severe weather.")
                .defineInRange("entityWindStrength", 0.22, 0.0, 0.6);
        builder.pop();

        DEBUG_LOGGING = builder
                .comment("Enable concise weather scheduling and persistence diagnostics. Disabled during normal play.")
                .define("debugLogging", false);

        builder.pop();
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
                    RANDOM_VARIATION.get(),
                    WEATHER_FRONT_STRENGTH.get()
            );
        } catch (IllegalStateException exception) {
            return SimulationSettings.DEFAULT;
        }
    }

    /** Returns clamp-safe wind controls for server queries and client snapshots. */
    public static WindSettings windSettings() {
        try {
            return new WindSettings(
                    WIND_ENABLED.get(),
                    BASE_WIND_STRENGTH.get().floatValue(),
                    GUST_FREQUENCY.get().floatValue(),
                    GUST_STRENGTH.get().floatValue(),
                    STORM_WIND_MULTIPLIER.get().floatValue(),
                    MAX_WIND_SPEED.get().floatValue()
            );
        } catch (IllegalStateException exception) {
            return WindSettings.DEFAULT;
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
                    WEATHER_OWNERSHIP_MODE.get(),
                    copyStrings(EXTERNAL_WEATHER_MOD_IDS.get()),
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

    /** Returns bounded campfire wildfire cadence, rarity, and scan controls. */
    public static WildfireSettings wildfires() {
        try {
            return new WildfireSettings(
                    WILDFIRES_ENABLED.get(),
                    WILDFIRE_CHECK_INTERVAL_TICKS.get(),
                    WILDFIRE_DIMENSION_COOLDOWN_TICKS.get(),
                    WILDFIRE_CELL_COOLDOWN_TICKS.get(),
                    WILDFIRE_CANDIDATE_CHUNK_RADIUS.get(),
                    WILDFIRE_CANDIDATE_CHUNKS_PER_PLAYER.get(),
                    WILDFIRE_EMBER_RANGE_BLOCKS.get(),
                    WILDFIRE_TARGET_ATTEMPTS.get(),
                    WILDFIRE_MAXIMUM_CHANCE_PER_CHECK.get()
            );
        } catch (IllegalStateException exception) {
            return WildfireSettings.DEFAULT;
        }
    }

    /** Returns bounded controls for optional Homeostatic/Serene season adapters. */
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

    /** Returns bounded controls for optional Cold Sweat and thirst adapters. */
    public static SurvivalIntegrationSettings survivalIntegrations() {
        try {
            return new SurvivalIntegrationSettings(
                    COLD_SWEAT_INTEGRATION_ENABLED.get(),
                    COLD_SWEAT_MAXIMUM_OFFSET_CELSIUS.get(),
                    THIRST_WAS_TAKEN_INTEGRATION_ENABLED.get(),
                    THIRST_WEATHER_INTERVAL_TICKS.get(),
                    THIRST_MAXIMUM_EXHAUSTION_PER_INTERVAL.get()
            );
        } catch (IllegalStateException exception) {
            return SurvivalIntegrationSettings.DEFAULT;
        }
    }

    /** Returns bounded lifecycle, surface, and severe-weather feature controls. */
    public static FeatureSettings features() {
        try {
            return new FeatureSettings(
                    PERSISTENT_SYSTEMS_ENABLED.get(),
                    MAXIMUM_WEATHER_SYSTEMS.get(),
                    WEATHER_SYSTEM_MOVEMENT_SPEED.get(),
                    WEATHER_SYSTEM_SPLITTING_ENABLED.get(),
                    SURFACE_WEATHERING_ENABLED.get(),
                    SURFACE_WEATHERING_INTERVAL_TICKS.get(),
                    SURFACE_WEATHERING_ATTEMPTS_PER_PLAYER.get(),
                    MAXIMUM_SNOW_LAYERS.get(),
                    SEVERE_WEATHER_ENABLED.get(),
                    TORNADOES_ENABLED.get(),
                    CYCLONES_ENABLED.get(),
                    SEVERE_BLOCK_DAMAGE_ENABLED.get(),
                    SEVERE_ENTITY_WIND_STRENGTH.get()
            );
        } catch (IllegalStateException exception) {
            return FeatureSettings.DEFAULT;
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
        SchedulingSettings settings = scheduling();
        boolean ownsWeather = WeatherOwnershipCoordinator.resolve(settings).wildernessOwnsWeather();
        cachedDimensionSelection = DimensionSelection.capture(settings, ownsWeather);
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

    private static boolean isModIdentifier(Object value) {
        if (!(value instanceof String string)) {
            return false;
        }
        String normalized = string.trim().toLowerCase(Locale.ROOT);
        return !normalized.isEmpty() && normalized.matches("[a-z][a-z0-9_-]{1,63}");
    }

    private static List<String> copyStrings(List<? extends String> source) {
        return source == null ? List.of() : List.copyOf(source);
    }

    /** Parsed immutable dimension ids used by the per-ticking-chunk hook. */
    private record DimensionSelection(
            boolean enabled,
            boolean ownsWeather,
            Set<ResourceLocation> allowlist,
            Set<ResourceLocation> denylist
    ) {
        private static final DimensionSelection DEFAULT = new DimensionSelection(
                true,
                true,
                Set.of(),
                Set.of()
        );

        private static DimensionSelection capture(SchedulingSettings settings, boolean ownsWeather) {
            SchedulingSettings safe = settings == null ? SchedulingSettings.DEFAULT : settings;
            return new DimensionSelection(
                    safe.enabled(),
                    ownsWeather,
                    parseLocations(safe.dimensionAllowlist()),
                    parseLocations(safe.dimensionDenylist())
            );
        }

        private boolean dimensionEnabled(ResourceLocation dimension) {
            return enabled
                    && ownsWeather
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
            WeatherOwnershipMode ownershipMode,
            List<String> externalWeatherModIds,
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
                VanillaWeatherCompatibilityMode.SUPPRESS_GLOBAL,
                WeatherOwnershipMode.AUTO,
                List.of("weather2", "simpleclouds", "betterweather"),
                false
        );

        /** Retains the pre-ownership construction shape for integrations and tests. */
        public SchedulingSettings(
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
            this(
                    enabled,
                    cellSize,
                    simulationIntervalTicks,
                    activeSimulationRadius,
                    inactiveCellGracePeriodTicks,
                    environmentResampleIntervalTicks,
                    snapshotSyncIntervalTicks,
                    maxPersistedCells,
                    dimensionAllowlist,
                    dimensionDenylist,
                    compatibilityMode,
                    WeatherOwnershipMode.AUTO,
                    List.of("weather2", "simpleclouds", "betterweather"),
                    debugLogging
            );
        }

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
                    ? VanillaWeatherCompatibilityMode.SUPPRESS_GLOBAL
                    : compatibilityMode;
            ownershipMode = ownershipMode == null ? WeatherOwnershipMode.AUTO : ownershipMode;
            externalWeatherModIds = sanitizeModIds(externalWeatherModIds);
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

        private static List<String> sanitizeModIds(List<String> modIds) {
            if (modIds == null || modIds.isEmpty()) {
                return List.of();
            }
            List<String> sanitized = new ArrayList<>(modIds.size());
            for (String candidate : modIds) {
                if (candidate == null) {
                    continue;
                }
                String normalized = candidate.trim().toLowerCase(Locale.ROOT);
                if (isModIdentifier(normalized) && !sanitized.contains(normalized)) {
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

    /** Immutable balance and cadence controls for optional survival mods. */
    public record SurvivalIntegrationSettings(
            boolean coldSweatEnabled,
            double coldSweatMaximumOffsetCelsius,
            boolean thirstWasTakenEnabled,
            int thirstIntervalTicks,
            double thirstMaximumExhaustionPerInterval
    ) {
        public static final SurvivalIntegrationSettings DEFAULT =
                new SurvivalIntegrationSettings(true, 12.0, true, 40, 0.025);

        public SurvivalIntegrationSettings {
            coldSweatMaximumOffsetCelsius = clamp(coldSweatMaximumOffsetCelsius, 0.0, 30.0);
            thirstIntervalTicks = Math.max(20, Math.min(1_200, thirstIntervalTicks));
            thirstMaximumExhaustionPerInterval = clamp(
                    thirstMaximumExhaustionPerInterval,
                    0.0,
                    0.25
            );
        }

        private static double clamp(double value, double minimum, double maximum) {
            double finite = Double.isFinite(value) ? value : minimum;
            return Math.max(minimum, Math.min(maximum, finite));
        }
    }

    /** Immutable safety and lifecycle settings for the weather-v3 additions. */
    public record FeatureSettings(
            boolean persistentSystemsEnabled,
            int maximumWeatherSystems,
            double movementBlocksPerSecond,
            boolean splittingEnabled,
            boolean surfaceWeatheringEnabled,
            int surfaceWeatheringIntervalTicks,
            int surfaceWeatheringAttemptsPerPlayer,
            int maximumSnowLayers,
            boolean severeWeatherEnabled,
            boolean tornadoesEnabled,
            boolean cyclonesEnabled,
            boolean severeBlockDamageEnabled,
            double severeEntityWindStrength
    ) {
        public static final FeatureSettings DEFAULT = new FeatureSettings(
                true, 48, 3.0, true, true, 40, 4, 5,
                true, true, true, false, 0.22
        );

        public FeatureSettings {
            maximumWeatherSystems = clamp(maximumWeatherSystems, 1, 256);
            movementBlocksPerSecond = clamp(movementBlocksPerSecond, 0.0, 16.0);
            surfaceWeatheringIntervalTicks = clamp(surfaceWeatheringIntervalTicks, 10, 1_200);
            surfaceWeatheringAttemptsPerPlayer = clamp(surfaceWeatheringAttemptsPerPlayer, 1, 32);
            maximumSnowLayers = clamp(maximumSnowLayers, 1, 8);
            severeEntityWindStrength = clamp(severeEntityWindStrength, 0.0, 0.6);
        }

        /** Maps public config values to the pure persistent-identity tracker. */
        public WeatherSystemTracker.TrackingSettings trackingSettings(int nominalIntervalTicks) {
            WeatherSystemTracker.TrackingSettings defaults = WeatherSystemTracker.TrackingSettings.DEFAULT;
            return new WeatherSystemTracker.TrackingSettings(
                    persistentSystemsEnabled,
                    maximumWeatherSystems,
                    nominalIntervalTicks,
                    movementBlocksPerSecond,
                    defaults.observationSeparationBlocks(),
                    defaults.matchDistanceBlocks(),
                    defaults.spawnIntensity(),
                    defaults.minimumRetainedIntensity(),
                    defaults.dissipationPerUpdate(),
                    defaults.mergeRadiusMultiplier(),
                    splittingEnabled,
                    defaults.splitIntensity(),
                    defaults.splitOrganization(),
                    defaults.splitCooldownTicks()
            );
        }

        private static int clamp(int value, int minimum, int maximum) {
            return Math.max(minimum, Math.min(maximum, value));
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

    /** Immutable rarity and world-access limits for campfire-started wildfires. */
    public record WildfireSettings(
            boolean enabled,
            int checkIntervalTicks,
            int dimensionCooldownTicks,
            int cellCooldownTicks,
            int candidateChunkRadius,
            int candidateChunksPerPlayer,
            int emberRangeBlocks,
            int targetAttempts,
            double maximumChancePerCheck
    ) {
        public static final WildfireSettings DEFAULT = new WildfireSettings(
                true,
                DEFAULT_WILDFIRE_CHECK_INTERVAL,
                DEFAULT_WILDFIRE_DIMENSION_COOLDOWN,
                DEFAULT_WILDFIRE_CELL_COOLDOWN,
                DEFAULT_WILDFIRE_CANDIDATE_CHUNK_RADIUS,
                DEFAULT_WILDFIRE_CANDIDATE_CHUNKS_PER_PLAYER,
                DEFAULT_WILDFIRE_EMBER_RANGE,
                DEFAULT_WILDFIRE_TARGET_ATTEMPTS,
                DEFAULT_WILDFIRE_MAXIMUM_CHANCE
        );

        public WildfireSettings {
            checkIntervalTicks = clamp(checkIntervalTicks, 100, 72_000);
            dimensionCooldownTicks = clamp(dimensionCooldownTicks, 1_200, 1_728_000);
            cellCooldownTicks = Math.max(
                    dimensionCooldownTicks,
                    clamp(cellCooldownTicks, 1_200, 1_728_000)
            );
            candidateChunkRadius = clamp(candidateChunkRadius, 0, 8);
            candidateChunksPerPlayer = clamp(candidateChunksPerPlayer, 1, 16);
            emberRangeBlocks = clamp(emberRangeBlocks, 4, 24);
            targetAttempts = clamp(targetAttempts, 1, 32);
            maximumChancePerCheck = clamp(
                    Double.isFinite(maximumChancePerCheck)
                            ? maximumChancePerCheck
                            : DEFAULT_WILDFIRE_MAXIMUM_CHANCE,
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
