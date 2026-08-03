package com.thunder.wildernessodysseyapi.watersystem.water.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Defines server-side water simulation and compatibility controls.
 *
 * <p>These values affect persistent canonical water data, so they live in a
 * server config instead of the client rendering config. Renderers should react
 * to synchronized canonical state rather than owning these simulation choices.</p>
 */
public final class WaterSimulationConfig {

    public static final ModConfigSpec CONFIG_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_WILDERNESS_ODYSSEY_WATER;
    public static final ModConfigSpec.BooleanValue ENABLE_VANILLA_BUCKET_COMPAT;
    public static final ModConfigSpec.BooleanValue ENABLE_VANILLA_BOAT_COMPAT;
    public static final ModConfigSpec.BooleanValue ENABLE_ENTITY_WATER_COMPAT;
    public static final ModConfigSpec.BooleanValue ENABLE_ENTITY_HYDRODYNAMICS;
    public static final ModConfigSpec.DoubleValue ENTITY_BUOYANCY_SCALE;
    public static final ModConfigSpec.DoubleValue ENTITY_DRAG_SCALE;
    public static final ModConfigSpec.DoubleValue ENTITY_MAX_ADDED_VELOCITY_SCALE;
    public static final ModConfigSpec.DoubleValue ENTITY_PLANING_SCALE;
    public static final ModConfigSpec.DoubleValue ENTITY_SLAMMING_SCALE;
    public static final ModConfigSpec.DoubleValue ENTITY_ANGULAR_RESPONSE_SCALE;
    public static final ModConfigSpec.BooleanValue ENABLE_FISHING_COMPAT;
    public static final ModConfigSpec.BooleanValue ENABLE_STRUCTURE_WATER_MARKERS;
    public static final ModConfigSpec.BooleanValue ENABLE_FLUID_HANDLER_COMPAT;
    public static final ModConfigSpec.BooleanValue ENABLE_CREATE_WATER_COMPAT;
    public static final ModConfigSpec.IntValue LOCAL_FLOW_CELLS_PER_TICK;
    public static final ModConfigSpec.DoubleValue LOCAL_FLOW_SLEEP_SPEED;
    public static final ModConfigSpec.IntValue LARGE_BODY_CACHE_MAX_COLUMNS;
    public static final ModConfigSpec.IntValue WATER_BODY_UPDATES_PER_TICK;
    public static final ModConfigSpec.IntValue LOCAL_WATER_NETWORK_EVENTS_PER_TICK;
    public static final ModConfigSpec.BooleanValue ENABLE_SERVER_SPH_LOCAL_SIMULATION;
    public static final ModConfigSpec.IntValue SERVER_SPH_MAX_ACTIVE_BODIES;
    public static final ModConfigSpec.IntValue SERVER_SPH_MAX_PARTICLES_PER_BODY;
    public static final ModConfigSpec.IntValue SERVER_SPH_PARTICLE_TICK_BUDGET;
    public static final ModConfigSpec.BooleanValue ENABLE_WEATHER_WATER_COUPLING;
    public static final ModConfigSpec.IntValue SEA_STATE_CELL_SIZE;
    public static final ModConfigSpec.IntValue SEA_STATE_SYNC_RADIUS_CELLS;
    public static final ModConfigSpec.IntValue SEA_STATE_UPDATE_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue SEA_STATE_BUILD_TIME_SECONDS;
    public static final ModConfigSpec.DoubleValue SEA_STATE_DECAY_TIME_SECONDS;
    public static final ModConfigSpec.IntValue SEA_STATE_MAX_CELLS;
    public static final ModConfigSpec.BooleanValue ENABLE_WEATHER_HYDROLOGY;
    public static final ModConfigSpec.IntValue HYDROLOGY_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue HYDROLOGY_PROBES_PER_PLAYER;
    public static final ModConfigSpec.IntValue HYDROLOGY_MAX_TRANSFERS_PER_TICK;
    public static final ModConfigSpec.IntValue HYDROLOGY_RAIN_UNITS_PER_PROBE;
    public static final ModConfigSpec.IntValue HYDROLOGY_EVAPORATION_UNITS_PER_PROBE;
    public static final ModConfigSpec.IntValue HYDROLOGY_MIN_TRANSFER_UNITS;
    public static final ModConfigSpec.IntValue HYDROLOGY_MAX_LEDGER_ENTRIES;
    public static final ModConfigSpec.IntValue DEBUG_COMMAND_MAX_RADIUS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Server-side water simulation and compatibility options.")
                .push("water_simulation");

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

        DEBUG_COMMAND_MAX_RADIUS = builder
                .comment("Maximum block radius accepted by /wowater summary and /wowater repair.")
                .defineInRange("debugCommandMaxRadius", 16, 1, 64);

        builder.pop();
        CONFIG_SPEC = builder.build();
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

    /** Returns the maximum debug radius allowed by server config. */
    public static int debugCommandMaxRadius() {
        return DEBUG_COMMAND_MAX_RADIUS.get();
    }
}
