package com.thunder.wildernessodysseyapi.watersystem.water.config;

import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Defines server-side water simulation and compatibility controls.
 *
 * <p>These values affect persistent canonical water data, so they live in a
 * server config instead of the client rendering config. Renderers should react
 * to synchronized canonical state rather than owning these simulation choices.</p>
 */
public final class WaterSimulationConfig {

    public static ModConfigSpec CONFIG_SPEC;

    public static ModConfigSpec.BooleanValue ENABLE_WILDERNESS_ODYSSEY_WATER;
    public static ModConfigSpec.BooleanValue ENABLE_VANILLA_BUCKET_COMPAT;
    public static ModConfigSpec.BooleanValue ENABLE_VANILLA_BOAT_COMPAT;
    public static ModConfigSpec.BooleanValue ENABLE_ENTITY_WATER_COMPAT;
    public static ModConfigSpec.BooleanValue ENABLE_ENTITY_HYDRODYNAMICS;
    public static ModConfigSpec.DoubleValue ENTITY_BUOYANCY_SCALE;
    public static ModConfigSpec.DoubleValue ENTITY_DRAG_SCALE;
    public static ModConfigSpec.DoubleValue ENTITY_MAX_ADDED_VELOCITY_SCALE;
    public static ModConfigSpec.DoubleValue ENTITY_PLANING_SCALE;
    public static ModConfigSpec.DoubleValue ENTITY_SLAMMING_SCALE;
    public static ModConfigSpec.DoubleValue ENTITY_ANGULAR_RESPONSE_SCALE;
    public static ModConfigSpec.BooleanValue ENABLE_FISHING_COMPAT;
    public static ModConfigSpec.BooleanValue ENABLE_STRUCTURE_WATER_MARKERS;
    public static ModConfigSpec.BooleanValue ENABLE_FLUID_HANDLER_COMPAT;
    public static ModConfigSpec.BooleanValue ENABLE_CREATE_WATER_COMPAT;
    public static ModConfigSpec.IntValue LOCAL_FLOW_CELLS_PER_TICK;
    public static ModConfigSpec.DoubleValue LOCAL_FLOW_SLEEP_SPEED;
    public static ModConfigSpec.IntValue LARGE_BODY_CACHE_MAX_COLUMNS;
    public static ModConfigSpec.IntValue WATER_BODY_UPDATES_PER_TICK;
    public static ModConfigSpec.IntValue LOCAL_WATER_NETWORK_EVENTS_PER_TICK;
    public static ModConfigSpec.BooleanValue ENABLE_SERVER_SPH_LOCAL_SIMULATION;
    public static ModConfigSpec.IntValue SERVER_SPH_MAX_ACTIVE_BODIES;
    public static ModConfigSpec.IntValue SERVER_SPH_MAX_PARTICLES_PER_BODY;
    public static ModConfigSpec.IntValue SERVER_SPH_PARTICLE_TICK_BUDGET;
    public static ModConfigSpec.BooleanValue ENABLE_WEATHER_WATER_COUPLING;
    public static ModConfigSpec.IntValue SEA_STATE_CELL_SIZE;
    public static ModConfigSpec.IntValue SEA_STATE_SYNC_RADIUS_CELLS;
    public static ModConfigSpec.IntValue SEA_STATE_UPDATE_INTERVAL_TICKS;
    public static ModConfigSpec.DoubleValue SEA_STATE_BUILD_TIME_SECONDS;
    public static ModConfigSpec.DoubleValue SEA_STATE_DECAY_TIME_SECONDS;
    public static ModConfigSpec.IntValue SEA_STATE_MAX_CELLS;
    public static ModConfigSpec.BooleanValue ENABLE_WEATHER_HYDROLOGY;
    public static ModConfigSpec.IntValue HYDROLOGY_INTERVAL_TICKS;
    public static ModConfigSpec.IntValue HYDROLOGY_PROBES_PER_PLAYER;
    public static ModConfigSpec.IntValue HYDROLOGY_MAX_TRANSFERS_PER_TICK;
    public static ModConfigSpec.IntValue HYDROLOGY_RAIN_UNITS_PER_PROBE;
    public static ModConfigSpec.IntValue HYDROLOGY_EVAPORATION_UNITS_PER_PROBE;
    public static ModConfigSpec.IntValue HYDROLOGY_MIN_TRANSFER_UNITS;
    public static ModConfigSpec.IntValue HYDROLOGY_MAX_LEDGER_ENTRIES;
    public static ModConfigSpec.BooleanValue ENABLE_WATERSHED_SIMULATION;
    public static ModConfigSpec.DoubleValue WATERSHED_RAINFALL_ACCUMULATION_RATE;
    public static ModConfigSpec.DoubleValue WATERSHED_SNOWMELT_RATE;
    public static ModConfigSpec.BooleanValue ENABLE_WATERSHED_GROUNDWATER;
    public static ModConfigSpec.DoubleValue WATERSHED_GROUNDWATER_RECHARGE_RATE;
    public static ModConfigSpec.DoubleValue WATERSHED_GROUNDWATER_SEEPAGE_RATE;
    public static ModConfigSpec.DoubleValue WATERSHED_SPRING_THRESHOLD;
    public static ModConfigSpec.DoubleValue WATERSHED_DRAINAGE_RATE;
    public static ModConfigSpec.DoubleValue WATERSHED_MAX_WATER_LEVEL_OFFSET;
    public static ModConfigSpec.BooleanValue ENABLE_LOCALIZED_FLOODING;
    public static ModConfigSpec.DoubleValue WATERSHED_FLOOD_THRESHOLD;
    public static ModConfigSpec.IntValue FLOOD_MAX_PLACEMENTS_PER_TICK;
    public static ModConfigSpec.IntValue FLOOD_MAX_REMOVALS_PER_TICK;
    public static ModConfigSpec.IntValue WATERSHED_SIMULATION_DISTANCE_CHUNKS;
    public static ModConfigSpec.IntValue WATERSHED_UPDATE_INTERVAL_TICKS;
    public static ModConfigSpec.IntValue WATERSHED_CHUNKS_PER_TICK;
    public static ModConfigSpec.IntValue WATERSHED_MAX_SAVED_CHUNKS;
    public static ModConfigSpec.IntValue WATERSHED_MAX_TEMPORARY_FLOOD_CELLS;
    public static ModConfigSpec.BooleanValue ENABLE_RAIN_FED_SURFACE_WATER;
    public static ModConfigSpec.DoubleValue WATERSHED_POND_FORMATION_THRESHOLD;
    public static ModConfigSpec.DoubleValue WATERSHED_WETLAND_FORMATION_THRESHOLD;
    public static ModConfigSpec.IntValue SURFACE_WATER_MAX_PLACEMENTS_PER_TICK;
    public static ModConfigSpec.IntValue SURFACE_WATER_MINIMUM_LIFETIME_TICKS;
    public static ModConfigSpec.BooleanValue ENABLE_WATERSHED_SEDIMENT_EFFECTS;
    public static ModConfigSpec.BooleanValue ENABLE_WATERSHED_DEBRIS_EFFECTS;
    public static ModConfigSpec.BooleanValue WATERSHED_DEBUG_LOGGING;
    public static ModConfigSpec.IntValue DEBUG_COMMAND_MAX_RADIUS;

    static {
        WildernessConfigSpecs.initialize();
    }

    /** Defines water simulation categories in the unified server config. */
    public static void define(ModConfigSpec.Builder builder) {

        builder.comment("Server-side water simulation and compatibility options.")
                .push("water_simulation");

        com.thunder.wildernessodysseyapi.watersystem.water.erosion.ErosionConfig.define(builder);

        ENABLE_WILDERNESS_ODYSSEY_WATER = builder
                .comment("Initial server choice for Wilderness Odyssey water authority, local flow, SPH gameplay water, and replacement rendering. The first world start persists this together with enableWildernessOdysseyWater; live changes cannot suspend established authority.")
                .define("enableWildernessOdysseyWater", true);
        ENABLE_VANILLA_BUCKET_COMPAT = builder
                .comment("Allow vanilla and Wilderness water buckets to translate successful placement and pickup through canonical authority. When disabled, authority-owned projections reject pickup and volume-overwriting placement instead of falling through to unsafe vanilla behavior.")
                .define("enableVanillaBucketCompat", true);
        ENABLE_VANILLA_BOAT_COMPAT = builder
                .comment("Allow vanilla boats to consume custom surface and current data. This does not change core water simulation when disabled.")
                .define("enableVanillaBoatCompat", true);
        ENABLE_ENTITY_WATER_COMPAT = builder
                .comment("Maintain centralized custom-water contact, submersion, eye, depth, current, and transition state for entities.")
                .define("enableEntityWaterCompat", true);
        ENABLE_ENTITY_HYDRODYNAMICS = builder
                .comment("Apply server-authoritative multi-point buoyancy, fluid-relative drag, currents, and bounded SPH/shoreline forces to boats, items, and living entities.")
                .define("enableEntityHydrodynamics", true);
        ENTITY_BUOYANCY_SCALE = builder
                .comment("Global scale for added displacement buoyancy. Vanilla movement remains responsible for each entity's baseline gravity and swimming behavior.")
                .defineInRange("entityBuoyancyScale", 1.0, 0.0, 2.0);
        ENTITY_DRAG_SCALE = builder
                .comment("Global scale for fluid-relative horizontal and vertical drag from authoritative currents.")
                .defineInRange("entityDragScale", 1.0, 0.0, 2.0);
        ENTITY_MAX_ADDED_VELOCITY_SCALE = builder
                .comment("Scales the per-tick safety cap on velocity added by custom hydrodynamics.")
                .defineInRange("entityMaxAddedVelocityScale", 1.0, 0.25, 2.0);
        ENTITY_PLANING_SCALE = builder
                .comment("Scales hull-oriented dynamic lift for fast, partially submerged watercraft.")
                .defineInRange("entityPlaningScale", 1.0, 0.0, 2.0);
        ENTITY_SLAMMING_SCALE = builder
                .comment("Scales the bounded surface-entry impulse when a hull strikes water while descending.")
                .defineInRange("entitySlammingScale", 1.0, 0.0, 2.0);
        ENTITY_ANGULAR_RESPONSE_SCALE = builder
                .comment("Scales empty-watercraft yaw stability from lateral current and hull slip.")
                .defineInRange("entityAngularResponseScale", 1.0, 0.0, 2.0);
        ENABLE_FISHING_COMPAT = builder
                .comment("Let fishing approach and splash effects recognize standalone Wilderness water. Open-water loot validation remains vanilla and tag-based.")
                .define("enableFishingCompat", true);
        ENABLE_STRUCTURE_WATER_MARKERS = builder
                .comment("Convert DATA structure blocks with metadata wildernessodysseyapi:water into Wilderness source water once during placement.")
                .define("enableStructureWaterMarkers", true);
        ENABLE_FLUID_HANDLER_COMPAT = builder
                .comment("Expose canonical water through a transactional NeoForge block-fluid capability and reconcile guarded world-fluid writes. Disable this to make all machine bridges read-only/inert.")
                .define("enableFluidHandlerCompat", true);
        ENABLE_CREATE_WATER_COMPAT = builder
                .comment("Let Create recognize Wilderness source/flowing fluid as water and route open-pipe world transfers through the guarded fluid bridge. Requires enableFluidHandlerCompat.")
                .define("enableCreateWaterCompat", true);
        LOCAL_FLOW_CELLS_PER_TICK = builder
                .comment("Maximum active detailed water cells processed per server tick. Sleeping cells are skipped until nearby water changes.")
                .defineInRange("localFlowCellsPerTick", 128, 16, 1024);
        LOCAL_FLOW_SLEEP_SPEED = builder
                .comment("Velocity below this value lets a detailed local water cell sleep when it cannot flow.")
                .defineInRange("localFlowSleepSpeed", 0.025, 0.001, 0.20);
        LARGE_BODY_CACHE_MAX_COLUMNS = builder
                .comment("Maximum derived large-water-body columns persisted per dimension. This is a safe per-world cache, not a second authority.")
                .defineInRange("largeBodyCacheMaxColumns", 16384, 1024, 262144);
        WATER_BODY_UPDATES_PER_TICK = builder
                .comment("Maximum high-level water-body or shoreline-grid regions updated per dimension tick. Large bodies stay as cached metadata; this cap prevents player oceans from becoming full-cell simulation work.")
                .defineInRange("waterBodyUpdatesPerTick", 6, 1, 64);
        LOCAL_WATER_NETWORK_EVENTS_PER_TICK = builder
                .comment("Maximum compact local water visual events, such as SPH splash or shore-wash events, sent per dimension tick. Events are synced instead of individual particles.")
                .defineInRange("localWaterNetworkEventsPerTick", 32, 0, 512);
        ENABLE_SERVER_SPH_LOCAL_SIMULATION = builder
                .comment("Allow tiny gameplay-critical SPH bodies for active local water such as waterfalls. Oceans, lakes, rivers, and stored world water never use SPH.")
                .define("enableServerSphLocalSimulation", true);
        SERVER_SPH_MAX_ACTIVE_BODIES = builder
                .comment("Maximum server-owned gameplay SPH bodies per dimension. Keep this small; visual-only splashes are client events.")
                .defineInRange("serverSphMaxActiveBodies", 8, 0, 24);
        SERVER_SPH_MAX_PARTICLES_PER_BODY = builder
                .comment("Maximum particles in one server-owned gameplay SPH body. Larger values are more expensive and should not be used for permanent water.")
                .defineInRange("serverSphMaxParticlesPerBody", 192, 16, 720);
        SERVER_SPH_PARTICLE_TICK_BUDGET = builder
                .comment("Maximum server-owned SPH particles advanced per dimension tick. Bodies past the budget wait for a later tick.")
                .defineInRange("serverSphParticleTickBudget", 900, 0, 4096);

        // Weather coupling is kept behind its own server controls because it
        // changes authoritative waves and long-timescale canonical volume.
        builder.comment("Localized weather response and bounded water-cycle coupling.")
                .push("weather_coupling");
        ENABLE_WEATHER_WATER_COUPLING = builder
                .comment("Let localized Wilderness weather drive regional waves, swell, shoreline breaking, water physics, optics, and ambient effects. External/vanilla weather remains the fallback when Wilderness does not own weather.")
                .define("enabled", true);
        SEA_STATE_CELL_SIZE = builder
                .comment("Width in blocks of one regional sea-state cell. Smaller cells follow storm edges more closely but increase server and network work.")
                .defineInRange("seaStateCellSize", 128, 64, 512);
        SEA_STATE_SYNC_RADIUS_CELLS = builder
                .comment("Sea-state cells synchronized around each player. The maximum value sends at most a 9 by 9 lattice.")
                .defineInRange("seaStateSyncRadiusCells", 2, 1, 4);
        SEA_STATE_UPDATE_INTERVAL_TICKS = builder
                .comment("Ticks between regional sea-state target updates. Wave animation still advances every frame.")
                .defineInRange("seaStateUpdateIntervalTicks", 10, 1, 100);
        SEA_STATE_BUILD_TIME_SECONDS = builder
                .comment("Approximate response time for wind waves and swell to build toward stronger weather.")
                .defineInRange("seaStateBuildTimeSeconds", 35.0, 1.0, 600.0);
        SEA_STATE_DECAY_TIME_SECONDS = builder
                .comment("Approximate response time for swell to decay after a storm leaves. This should normally exceed build time.")
                .defineInRange("seaStateDecayTimeSeconds", 180.0, 1.0, 1800.0);
        SEA_STATE_MAX_CELLS = builder
                .comment("Maximum ephemeral regional sea-state cells retained per dimension.")
                .defineInRange("seaStateMaxCells", 2048, 64, 16384);
        ENABLE_WEATHER_HYDROLOGY = builder
                .comment("Allow sustained localized rain, thaw, and evaporation to exchange bounded fixed-point volume with loaded non-ocean Wilderness water.")
                .define("enableHydrology", true);
        HYDROLOGY_INTERVAL_TICKS = builder
                .comment("Ticks between loaded-only hydrology sampling passes.")
                .defineInRange("hydrologyIntervalTicks", 40, 20, 1200);
        HYDROLOGY_PROBES_PER_PLAYER = builder
                .comment("Deterministic surface-water probes attempted around each player per hydrology pass.")
                .defineInRange("hydrologyProbesPerPlayer", 4, 1, 32);
        HYDROLOGY_MAX_TRANSFERS_PER_TICK = builder
                .comment("Maximum canonical add/remove operations committed by hydrology in one dimension pass.")
                .defineInRange("hydrologyMaxTransfersPerTick", 4, 1, 64);
        HYDROLOGY_RAIN_UNITS_PER_PROBE = builder
                .comment("Maximum fixed-point water units credited by one fully intense rain/thaw probe. One full block is 4096 units.")
                .defineInRange("hydrologyRainUnitsPerProbe", 48, 0, 4096);
        HYDROLOGY_EVAPORATION_UNITS_PER_PROBE = builder
                .comment("Maximum fixed-point water units debited by one hot, dry, windy evaporation probe.")
                .defineInRange("hydrologyEvaporationUnitsPerProbe", 18, 0, 4096);
        HYDROLOGY_MIN_TRANSFER_UNITS = builder
                .comment("Minimum accumulated absolute balance before hydrology mutates canonical water, avoiding tiny noisy writes.")
                .defineInRange("hydrologyMinTransferUnits", 64, 1, 4096);
        HYDROLOGY_MAX_LEDGER_ENTRIES = builder
                .comment("Maximum persisted chunk-scale fractional hydrology balances per dimension.")
                .defineInRange("hydrologyMaxLedgerEntries", 4096, 128, 65536);
        builder.pop();

        // Watersheds retain only chunk-scale packed conditions. They extend
        // the authority's surface/current metadata and never replace canonical
        // volume or start a second fluid-tick simulation.
        builder.comment("Chunk-scale watersheds, dynamic river conditions, and conservative temporary flooding.")
                .push("watersheds");
        ENABLE_WATERSHED_SIMULATION = builder
                .comment("Enable deterministic loaded-only watershed metadata and river-condition updates.")
                .define("enabled", true);
        WATERSHED_RAINFALL_ACCUMULATION_RATE = builder
                .comment("Normalized rainfall memory added by one fully intense precipitation pass.")
                .defineInRange("rainfallAccumulationRate", 0.045, 0.0, 0.5);
        WATERSHED_SNOWMELT_RATE = builder
                .comment("Normalized stored snowpack routed as delayed runoff during one warm watershed pass.")
                .defineInRange("snowmeltRate", 0.035, 0.0, 0.5);
        ENABLE_WATERSHED_GROUNDWATER = builder
                .comment("Enable persistent chunk-scale aquifer recharge, storage, seepage, springs, and dry-weather river baseflow.")
                .define("groundwaterEnabled", true);
        WATERSHED_GROUNDWATER_RECHARGE_RATE = builder
                .comment("Fraction of infiltrating rain and snowmelt retained as delayed aquifer recharge per watershed pass.")
                .defineInRange("groundwaterRechargeRate", 0.32, 0.0, 1.0);
        WATERSHED_GROUNDWATER_SEEPAGE_RATE = builder
                .comment("Slow normalized aquifer seepage and baseflow rate per watershed pass.")
                .defineInRange("groundwaterSeepageRate", 0.018, 0.001, 0.20);
        WATERSHED_SPRING_THRESHOLD = builder
                .comment("Normalized aquifer storage at which a high water table may feed a spring in safe loaded terrain.")
                .defineInRange("springThreshold", 0.78, 0.45, 1.0);
        WATERSHED_DRAINAGE_RATE = builder
                .comment("Normalized per-pass soil drainage, runoff decay, and dry-weather evaporation rate.")
                .defineInRange("drainageRate", 0.025, 0.001, 0.25);
        WATERSHED_MAX_WATER_LEVEL_OFFSET = builder
                .comment("Maximum visual/gameplay river or lake surface offset in blocks. Physical generated columns are not rewritten for this offset.")
                .defineInRange("maximumWaterLevelOffset", 0.45, 0.0, 1.5);
        ENABLE_LOCALIZED_FLOODING = builder
                .comment("Allow high-risk loaded river/lake chunks to place separately tracked temporary floodwater through canonical authority.")
                .define("floodingEnabled", true);
        WATERSHED_FLOOD_THRESHOLD = builder
                .comment("Normalized combined discharge, saturation, and rainfall risk required to start flooding.")
                .defineInRange("floodThreshold", 0.88, 0.5, 1.0);
        FLOOD_MAX_PLACEMENTS_PER_TICK = builder
                .comment("Global per-dimension cap on temporary floodwater placements in one server tick.")
                .defineInRange("maximumFloodPlacementsPerTick", 2, 0, 32);
        FLOOD_MAX_REMOVALS_PER_TICK = builder
                .comment("Global per-dimension cap on exact temporary floodwater recession removals in one server tick.")
                .defineInRange("maximumFloodRemovalsPerTick", 4, 0, 64);
        WATERSHED_SIMULATION_DISTANCE_CHUNKS = builder
                .comment("Loaded chunk radius around players eligible for staggered watershed simulation and condition sync.")
                .defineInRange("simulationDistanceChunks", 6, 1, 16);
        WATERSHED_UPDATE_INTERVAL_TICKS = builder
                .comment("Ticks between additions of player-relevant loaded chunks to the time-sliced watershed queue.")
                .defineInRange("updateIntervalTicks", 40, 20, 1200);
        WATERSHED_CHUNKS_PER_TICK = builder
                .comment("Maximum queued watershed chunks simulated in one dimension tick.")
                .defineInRange("chunksPerTick", 6, 1, 64);
        WATERSHED_MAX_SAVED_CHUNKS = builder
                .comment("Maximum compact watershed cells retained per dimension save.")
                .defineInRange("maximumSavedChunks", 32768, 1024, 65536);
        WATERSHED_MAX_TEMPORARY_FLOOD_CELLS = builder
                .comment("Maximum exact reversible flood, pond, wetland, and spring positions retained per dimension.")
                .defineInRange("maximumTemporaryFloodCells", 8192, 128, 65536);
        ENABLE_RAIN_FED_SURFACE_WATER = builder
                .comment("Allow sustained rain, snowmelt, and high groundwater to create reversible ponds, wetlands, and springs in safe terrain depressions.")
                .define("rainFedSurfaceWaterEnabled", true);
        WATERSHED_POND_FORMATION_THRESHOLD = builder
                .comment("Normalized sustained ponding pressure required before a closed loaded depression fills.")
                .defineInRange("pondFormationThreshold", 0.68, 0.35, 1.0);
        WATERSHED_WETLAND_FORMATION_THRESHOLD = builder
                .comment("Normalized soil and water-table wetness required for shallow temporary wetland water.")
                .defineInRange("wetlandFormationThreshold", 0.58, 0.30, 1.0);
        SURFACE_WATER_MAX_PLACEMENTS_PER_TICK = builder
                .comment("Global per-dimension cap on reversible rain-pond, wetland, and spring placements per server tick.")
                .defineInRange("surfaceWaterMaximumPlacementsPerTick", 1, 0, 16);
        SURFACE_WATER_MINIMUM_LIFETIME_TICKS = builder
                .comment("Minimum lifetime of owned pond, wetland, and spring cells before dry-weather recession may remove them.")
                .defineInRange("surfaceWaterMinimumLifetimeTicks", 1200, 100, 24000);
        ENABLE_WATERSHED_SEDIMENT_EFFECTS = builder
                .comment("Let runoff and floods raise synchronized sediment/turbidity metadata.")
                .define("sedimentEffects", true);
        ENABLE_WATERSHED_DEBRIS_EFFECTS = builder
                .comment("Let runoff and floods produce lightweight synchronized debris metadata for pooled particles/render hooks.")
                .define("debrisEffects", true);
        WATERSHED_DEBUG_LOGGING = builder
                .comment("Log bounded watershed queue and flood summaries. Leave disabled outside diagnostics.")
                .define("debugLogging", false);
        builder.pop();

        DEBUG_COMMAND_MAX_RADIUS = builder
                .comment("Maximum block radius accepted by /wowater summary and /wowater repair.")
                .defineInRange("debugCommandMaxRadius", 16, 1, 64);

        builder.pop();
    }

    private WaterSimulationConfig() {
    }

    /** Returns whether the pack-level Wilderness water master switch is enabled. */
    public static boolean wildernessWaterEnabled() {
        return ENABLE_WILDERNESS_ODYSSEY_WATER.get();
    }

    /** Returns whether successful bucket interactions may update custom authority. */
    public static boolean vanillaBucketCompatEnabled() {
        return wildernessWaterEnabled() && ENABLE_VANILLA_BUCKET_COMPAT.get();
    }

    /** Returns whether vanilla boats may consume custom buoyancy/current state. */
    public static boolean vanillaBoatCompatEnabled() {
        return wildernessWaterEnabled() && ENABLE_VANILLA_BOAT_COMPAT.get();
    }

    /** Returns whether centralized entity water-state sampling is active. */
    public static boolean entityWaterCompatEnabled() {
        return wildernessWaterEnabled() && ENABLE_ENTITY_WATER_COMPAT.get();
    }

    /** Returns whether server-authoritative entity hydrodynamic forces are active. */
    public static boolean entityHydrodynamicsEnabled() {
        return entityWaterCompatEnabled() && ENABLE_ENTITY_HYDRODYNAMICS.get();
    }

    /** Returns the configured multiplier for displacement buoyancy. */
    public static double entityBuoyancyScale() {
        return ENTITY_BUOYANCY_SCALE.get();
    }

    /** Returns the configured multiplier for fluid-relative drag. */
    public static double entityDragScale() {
        return ENTITY_DRAG_SCALE.get();
    }

    /** Returns the configured multiplier for the per-tick hydrodynamic safety cap. */
    public static double entityMaxAddedVelocityScale() {
        return ENTITY_MAX_ADDED_VELOCITY_SCALE.get();
    }

    /** Returns the configured multiplier for watercraft planing lift. */
    public static double entityPlaningScale() {
        return ENTITY_PLANING_SCALE.get();
    }

    /** Returns the configured multiplier for watercraft entry slamming. */
    public static double entitySlammingScale() {
        return ENTITY_SLAMMING_SCALE.get();
    }

    /** Returns the configured multiplier for watercraft angular stability. */
    public static double entityAngularResponseScale() {
        return ENTITY_ANGULAR_RESPONSE_SCALE.get();
    }

    /** Returns whether exact fishing-effect checks recognize Wilderness water. */
    public static boolean fishingCompatEnabled() {
        return wildernessWaterEnabled() && ENABLE_FISHING_COMPAT.get();
    }

    /** Returns whether explicit structure water markers convert during placement. */
    public static boolean structureWaterMarkersEnabled() {
        return wildernessWaterEnabled() && ENABLE_STRUCTURE_WATER_MARKERS.get();
    }

    /** Returns whether the transactional NeoForge fluid-handler bridge is enabled. */
    public static boolean fluidHandlerCompatEnabled() {
        return wildernessWaterEnabled() && ENABLE_FLUID_HANDLER_COMPAT.get();
    }

    /** Returns whether Create-local water recognition and world transfer reconciliation are enabled. */
    public static boolean createWaterCompatEnabled() {
        return fluidHandlerCompatEnabled() && ENABLE_CREATE_WATER_COMPAT.get();
    }

    /** Returns the active local-cell flow budget for one server tick. */
    public static int localFlowCellsPerTick() {
        return wildernessWaterEnabled() ? LOCAL_FLOW_CELLS_PER_TICK.get() : 0;
    }

    /** Returns the speed threshold under which immobile local cells can sleep. */
    public static float localFlowSleepSpeed() {
        return LOCAL_FLOW_SLEEP_SPEED.get().floatValue();
    }

    /** Returns the maximum persisted large-body column cache entries per dimension. */
    public static int largeBodyCacheMaxColumns() {
        return LARGE_BODY_CACHE_MAX_COLUMNS.get();
    }

    /** Returns how many high-level water-body regions may update in one dimension tick. */
    public static int waterBodyUpdatesPerTick() {
        return wildernessWaterEnabled() ? WATER_BODY_UPDATES_PER_TICK.get() : 0;
    }

    /** Returns how many compact local water network events may be sent in one dimension tick. */
    public static int localWaterNetworkEventsPerTick() {
        return wildernessWaterEnabled() ? LOCAL_WATER_NETWORK_EVENTS_PER_TICK.get() : 0;
    }

    /** Returns whether server-owned local SPH is allowed for gameplay-critical active water. */
    public static boolean serverSphLocalSimulationEnabled() {
        return wildernessWaterEnabled()
                && ENABLE_SERVER_SPH_LOCAL_SIMULATION.get()
                && SERVER_SPH_MAX_ACTIVE_BODIES.get() > 0
                && SERVER_SPH_PARTICLE_TICK_BUDGET.get() > 0;
    }

    /** Returns the maximum number of server gameplay SPH bodies per dimension. */
    public static int serverSphMaxActiveBodies() {
        return serverSphLocalSimulationEnabled() ? SERVER_SPH_MAX_ACTIVE_BODIES.get() : 0;
    }

    /** Returns the maximum particles allowed in one server gameplay SPH body. */
    public static int serverSphMaxParticlesPerBody() {
        return serverSphLocalSimulationEnabled() ? SERVER_SPH_MAX_PARTICLES_PER_BODY.get() : 0;
    }

    /** Returns the per-dimension server SPH particle simulation budget. */
    public static int serverSphParticleTickBudget() {
        return serverSphLocalSimulationEnabled() ? SERVER_SPH_PARTICLE_TICK_BUDGET.get() : 0;
    }

    /** Returns whether localized weather may drive authoritative water response. */
    public static boolean weatherWaterCouplingEnabled() {
        return wildernessWaterEnabled() && ENABLE_WEATHER_WATER_COUPLING.get();
    }

    /** Returns the regional sea-state cell width in blocks. */
    public static int seaStateCellSize() {
        return SEA_STATE_CELL_SIZE.get();
    }

    /** Returns the regional synchronization radius around each player. */
    public static int seaStateSyncRadiusCells() {
        return SEA_STATE_SYNC_RADIUS_CELLS.get();
    }

    /** Returns the server update interval for weather-driven sea state. */
    public static int seaStateUpdateIntervalTicks() {
        return SEA_STATE_UPDATE_INTERVAL_TICKS.get();
    }

    /** Returns the stronger-weather sea-state response time in seconds. */
    public static float seaStateBuildTimeSeconds() {
        return SEA_STATE_BUILD_TIME_SECONDS.get().floatValue();
    }

    /** Returns the post-storm swell decay time in seconds. */
    public static float seaStateDecayTimeSeconds() {
        return SEA_STATE_DECAY_TIME_SECONDS.get().floatValue();
    }

    /** Returns the maximum retained regional sea-state cells per dimension. */
    public static int seaStateMaxCells() {
        return SEA_STATE_MAX_CELLS.get();
    }

    /** Returns whether loaded finite water participates in rain/evaporation exchange. */
    public static boolean weatherHydrologyEnabled() {
        return weatherWaterCouplingEnabled() && ENABLE_WEATHER_HYDROLOGY.get();
    }

    /** Returns the loaded-only hydrology sampling interval. */
    public static int hydrologyIntervalTicks() {
        return HYDROLOGY_INTERVAL_TICKS.get();
    }

    /** Returns the hydrology probe budget for each player. */
    public static int hydrologyProbesPerPlayer() {
        return HYDROLOGY_PROBES_PER_PLAYER.get();
    }

    /** Returns the maximum committed hydrology transfers per dimension pass. */
    public static int hydrologyMaxTransfersPerTick() {
        return HYDROLOGY_MAX_TRANSFERS_PER_TICK.get();
    }

    /** Returns the maximum rain/thaw credit produced by one probe. */
    public static int hydrologyRainUnitsPerProbe() {
        return HYDROLOGY_RAIN_UNITS_PER_PROBE.get();
    }

    /** Returns the maximum evaporation debit produced by one probe. */
    public static int hydrologyEvaporationUnitsPerProbe() {
        return HYDROLOGY_EVAPORATION_UNITS_PER_PROBE.get();
    }

    /** Returns the accumulated balance required before a canonical mutation. */
    public static int hydrologyMinTransferUnits() {
        return HYDROLOGY_MIN_TRANSFER_UNITS.get();
    }

    /** Returns the maximum persisted chunk-scale hydrology balances. */
    public static int hydrologyMaxLedgerEntries() {
        return HYDROLOGY_MAX_LEDGER_ENTRIES.get();
    }

    /** Returns whether chunk-scale watershed conditions are active. */
    public static boolean watershedSimulationEnabled() {
        return wildernessWaterEnabled() && ENABLE_WATERSHED_SIMULATION.get();
    }

    /** Returns normalized rainfall memory added by one simulation pass. */
    public static float watershedRainfallAccumulationRate() {
        return WATERSHED_RAINFALL_ACCUMULATION_RATE.get().floatValue();
    }

    /** Returns the per-pass stored-snow thaw contribution to watershed runoff. */
    public static float watershedSnowmeltRate() {
        return WATERSHED_SNOWMELT_RATE.get().floatValue();
    }

    /** Returns whether persistent aquifer recharge and baseflow are active. */
    public static boolean watershedGroundwaterEnabled() {
        return watershedSimulationEnabled() && ENABLE_WATERSHED_GROUNDWATER.get();
    }

    /** Returns the fraction of infiltrating water retained as recharge. */
    public static float watershedGroundwaterRechargeRate() {
        return WATERSHED_GROUNDWATER_RECHARGE_RATE.get().floatValue();
    }

    /** Returns the slow aquifer seepage and baseflow rate. */
    public static float watershedGroundwaterSeepageRate() {
        return WATERSHED_GROUNDWATER_SEEPAGE_RATE.get().floatValue();
    }

    /** Returns the aquifer-storage threshold for natural spring formation. */
    public static float watershedSpringThreshold() {
        return WATERSHED_SPRING_THRESHOLD.get().floatValue();
    }

    /** Returns normalized soil/runoff drainage applied by one pass. */
    public static float watershedDrainageRate() {
        return WATERSHED_DRAINAGE_RATE.get().floatValue();
    }

    /** Returns the absolute river/lake surface-offset limit in blocks. */
    public static float watershedMaximumWaterLevelOffset() {
        return WATERSHED_MAX_WATER_LEVEL_OFFSET.get().floatValue();
    }

    /** Returns whether exact temporary floodwater placement is allowed. */
    public static boolean localizedFloodingEnabled() {
        return watershedSimulationEnabled()
                && ENABLE_LOCALIZED_FLOODING.get()
                && FLOOD_MAX_PLACEMENTS_PER_TICK.get() > 0;
    }

    /** Returns the normalized flood activation threshold. */
    public static float watershedFloodThreshold() {
        return WATERSHED_FLOOD_THRESHOLD.get().floatValue();
    }

    /** Returns the per-dimension temporary placement budget. */
    public static int maximumFloodPlacementsPerTick() {
        return localizedFloodingEnabled() ? FLOOD_MAX_PLACEMENTS_PER_TICK.get() : 0;
    }

    /** Returns the per-dimension exact recession budget. */
    public static int maximumFloodRemovalsPerTick() {
        return wildernessWaterEnabled() ? FLOOD_MAX_REMOVALS_PER_TICK.get() : 0;
    }

    /** Returns the player-centered loaded-chunk simulation radius. */
    public static int watershedSimulationDistanceChunks() {
        return WATERSHED_SIMULATION_DISTANCE_CHUNKS.get();
    }

    /** Returns the queue refresh interval. */
    public static int watershedUpdateIntervalTicks() {
        return WATERSHED_UPDATE_INTERVAL_TICKS.get();
    }

    /** Returns the time-sliced per-dimension chunk update budget. */
    public static int watershedChunksPerTick() {
        return watershedSimulationEnabled() ? WATERSHED_CHUNKS_PER_TICK.get() : 0;
    }

    /** Returns the persisted compact watershed-cell budget. */
    public static int watershedMaxSavedChunks() {
        return WATERSHED_MAX_SAVED_CHUNKS.get();
    }

    /** Returns the exact temporary-flood ledger budget. */
    public static int watershedMaxTemporaryFloodCells() {
        return WATERSHED_MAX_TEMPORARY_FLOOD_CELLS.get();
    }

    /** Returns the shared exact reversible-surface-water ledger budget. */
    public static int watershedMaxTransientWaterCells() {
        return WATERSHED_MAX_TEMPORARY_FLOOD_CELLS.get();
    }

    /** Returns whether loaded terrain may form reversible ponds, wetlands, and springs. */
    public static boolean rainFedSurfaceWaterEnabled() {
        return watershedSimulationEnabled()
                && ENABLE_RAIN_FED_SURFACE_WATER.get()
                && SURFACE_WATER_MAX_PLACEMENTS_PER_TICK.get() > 0;
    }

    /** Returns the sustained ponding-pressure threshold. */
    public static float watershedPondFormationThreshold() {
        return WATERSHED_POND_FORMATION_THRESHOLD.get().floatValue();
    }

    /** Returns the shallow-groundwater wetland threshold. */
    public static float watershedWetlandFormationThreshold() {
        return WATERSHED_WETLAND_FORMATION_THRESHOLD.get().floatValue();
    }

    /** Returns the per-dimension rain-fed surface-water placement budget. */
    public static int surfaceWaterMaximumPlacementsPerTick() {
        return rainFedSurfaceWaterEnabled() ? SURFACE_WATER_MAX_PLACEMENTS_PER_TICK.get() : 0;
    }

    /** Returns the minimum age before standing surface water may recede. */
    public static int surfaceWaterMinimumLifetimeTicks() {
        return SURFACE_WATER_MINIMUM_LIFETIME_TICKS.get();
    }

    /** Returns whether sediment/clarity state should respond to runoff. */
    public static boolean watershedSedimentEffectsEnabled() {
        return watershedSimulationEnabled() && ENABLE_WATERSHED_SEDIMENT_EFFECTS.get();
    }

    /** Returns whether pooled client debris effects should receive metadata. */
    public static boolean watershedDebrisEffectsEnabled() {
        return watershedSimulationEnabled() && ENABLE_WATERSHED_DEBRIS_EFFECTS.get();
    }

    /** Returns whether bounded watershed diagnostics should be logged. */
    public static boolean watershedDebugLoggingEnabled() {
        return watershedSimulationEnabled() && WATERSHED_DEBUG_LOGGING.get();
    }

    /** Returns the maximum debug radius allowed by server config. */
    public static int debugCommandMaxRadius() {
        return DEBUG_COMMAND_MAX_RADIUS.get();
    }
}
