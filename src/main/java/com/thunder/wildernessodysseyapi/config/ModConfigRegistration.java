package com.thunder.wildernessodysseyapi.config;

import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.async.AsyncThreadingConfig;
import com.thunder.wildernessodysseyapi.debugoverlay.config.DebugOverlayConfig;
import com.thunder.wildernessodysseyapi.dataengine.DataEngine;
import com.thunder.wildernessodysseyapi.dataengine.config.DataEngineConfig;
import com.thunder.wildernessodysseyapi.donations.config.DonationReminderConfig;
import com.thunder.wildernessodysseyapi.developmentstudio.config.StudioConfig;
import com.thunder.wildernessodysseyapi.ecosystem.EcosystemEvents;
import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import com.thunder.wildernessodysseyapi.ecosystem.data.SpeciesBehaviorProfileManager;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeManager;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemSimulationManager;
import com.thunder.wildernessodysseyapi.feedback.FeedbackConfig;
import com.thunder.wildernessodysseyapi.meteor.config.MeteorConfig;
import com.thunder.wildernessodysseyapi.ownership.config.OwnershipConfig;
import com.thunder.wildernessodysseyapi.performance.background.BackgroundEfficiencyManager;
import com.thunder.wildernessodysseyapi.performance.background.config.BackgroundEfficiencyConfig;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickEngine;
import com.thunder.wildernessodysseyapi.performance.tickengine.config.TickEngineConfig;
import com.thunder.wildernessodysseyapi.playtest.verification.MinecraftVerificationRelayConfig;
import com.thunder.wildernessodysseyapi.riftfall.config.RiftfallConfig;
import com.thunder.wildernessodysseyapi.structureblock.StructureBlockSettings;
import com.thunder.wildernessodysseyapi.structureblock.config.StructureBlockConfig;
import com.thunder.wildernessodysseyapi.telemetry.EventTelemetryConfig;
import com.thunder.wildernessodysseyapi.telemetry.PlayerTelemetryConfig;
import com.thunder.wildernessodysseyapi.telemetry.TelemetryConfig;
import com.thunder.wildernessodysseyapi.temporalrift.config.TemporalRiftConfig;
import com.thunder.wildernessodysseyapi.vegetation.config.VegetationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterRenderingConfig;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.config.WeatherRenderingConfig;
import com.thunder.wildernessodysseyapi.weather.simulation.WeatherAuthority;
import com.thunder.wildernessodysseyapi.worldgen.config.StructureConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

/**
 * Registers the mod's config files and applies settings that are cached at runtime.
 */
public final class ModConfigRegistration {

    private static final String CONFIG_FOLDER = MOD_ID + "/";

    private ModConfigRegistration() {
    }

    /**
     * Registers client, common, and server config specifications with descriptive filenames.
     *
     * @param container the mod container that owns the config specs
     */
    public static void register(ModContainer container) {
        ConfigRegistrationValidator.register(container, ModConfig.Type.COMMON, StructureConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-structures.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.COMMON, AsyncThreadingConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-async.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.COMMON, OwnershipConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-ownership.toml");

        ConfigRegistrationValidator.register(container, ModConfig.Type.CLIENT, DonationReminderConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-donations-client.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.CLIENT, DebugOverlayConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-debug-overlay-client.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.CLIENT, WaterRenderingConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-water-rendering-client.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.CLIENT, WeatherRenderingConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-weather-rendering-client.toml");

        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, StructureBlockConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-structureblocks-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, BackgroundEfficiencyConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-background-efficiency-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, TickEngineConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-tick-engine-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, StudioConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-development-studio-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER,
                MinecraftVerificationRelayConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-verification-relay-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, PlayerTelemetryConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-telemetry-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, EventTelemetryConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-event-telemetry-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, TelemetryConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-telemetry-master-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, FeedbackConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-feedback-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, RiftfallConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-riftfall-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, MeteorConfig.SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-meteors-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, TemporalRiftConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-temporal-rift-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, WaterSimulationConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-water-simulation-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, WeatherConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-weather-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, EcosystemConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-ecosystem-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, VegetationConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-vegetation-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, DataEngineConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-data-engine-server.toml");
    }

    /** Applies runtime-backed settings after NeoForge loads a config file. */
    public static void onConfigLoaded(ModConfigEvent.Loading event) {
        applyRuntimeSettings(event.getConfig());
    }

    /** Refreshes runtime-backed settings after a config file is reloaded. */
    public static void onConfigReloaded(ModConfigEvent.Reloading event) {
        applyRuntimeSettings(event.getConfig());
    }

    private static void applyRuntimeSettings(ModConfig config) {
        if (config.getSpec() == AsyncThreadingConfig.CONFIG_SPEC) {
            AsyncTaskManager.initialize(AsyncThreadingConfig.values());
        } else if (config.getSpec() == BackgroundEfficiencyConfig.CONFIG_SPEC) {
            applyOnServerThread(() -> BackgroundEfficiencyManager.reload(BackgroundEfficiencyConfig.values()));
        } else if (config.getSpec() == TickEngineConfig.CONFIG_SPEC) {
            applyOnServerThread(() -> TickEngine.reload(TickEngineConfig.values()));
        } else if (config.getSpec() == StructureBlockConfig.CONFIG_SPEC) {
            StructureBlockSettings.reloadFromConfig();
        } else if (config.getSpec() == WeatherConfig.CONFIG_SPEC) {
            WeatherConfig.reload();
            WeatherAuthority.get().onConfigurationReload();
        } else if (config.getSpec() == WeatherRenderingConfig.CONFIG_SPEC) {
            WeatherRenderingConfig.reload();
        } else if (config.getSpec() == EcosystemConfig.CONFIG_SPEC) {
            EcosystemConfig.reload();
            SpeciesBehaviorProfileManager.clearConfiguredProfiles();
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.execute(() -> {
                    EcosystemEvents.refreshLoadedControllers(server);
                    DistantWildlifeManager.get().markAllPlayersDirty(server);
                    EcosystemSimulationManager.get().onConfigurationReload(server);
                });
            }
        } else if (config.getSpec() == DataEngineConfig.CONFIG_SPEC) {
            DataEngineConfig.Values values = DataEngineConfig.values();
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                DataEngine.get().reload(values);
            } else {
                // Queue/budget state is server-owned even though config events
                // originate on the mod bus during live reload.
                server.execute(() -> DataEngine.get().reload(values));
            }
        }
    }

    // A live config reload must not replace worker pools while server-owned result application is running.
    private static void applyOnServerThread(Runnable action) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            action.run();
        } else {
            server.execute(action);
        }
    }
}
