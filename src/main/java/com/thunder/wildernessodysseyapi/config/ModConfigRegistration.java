package com.thunder.wildernessodysseyapi.config;

import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.async.AsyncThreadingConfig;
import com.thunder.wildernessodysseyapi.dataengine.DataEngine;
import com.thunder.wildernessodysseyapi.dataengine.config.DataEngineConfig;
import com.thunder.wildernessodysseyapi.ecosystem.EcosystemEvents;
import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import com.thunder.wildernessodysseyapi.ecosystem.data.SpeciesBehaviorProfileManager;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeManager;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemSimulationManager;
import com.thunder.wildernessodysseyapi.performance.background.BackgroundEfficiencyManager;
import com.thunder.wildernessodysseyapi.performance.background.config.BackgroundEfficiencyConfig;
import com.thunder.wildernessodysseyapi.performance.tickengine.TickEngine;
import com.thunder.wildernessodysseyapi.performance.tickengine.config.TickEngineConfig;
import com.thunder.wildernessodysseyapi.simulation.core.SimulationEngine;
import com.thunder.wildernessodysseyapi.structureblock.StructureBlockSettings;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.config.WeatherRenderingConfig;
import com.thunder.wildernessodysseyapi.weather.simulation.WeatherAuthority;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.nio.file.Files;
import java.nio.file.Path;

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
        Path configDirectory = FMLPaths.CONFIGDIR.get().resolve(MOD_ID);
        if (!Files.exists(configDirectory.resolve(WildernessConfigSpecs.SERVER_FILE))) {
            PerformanceConfigMigration.MigrationResult performanceMigration =
                    PerformanceConfigMigration.prepare(configDirectory);
            if (performanceMigration == PerformanceConfigMigration.MigrationResult.INVALID_DIRECTORY
                    || performanceMigration == PerformanceConfigMigration.MigrationResult.FAILED) {
                throw new IllegalStateException(
                        "Cannot safely prepare the unified Wilderness performance config: " + performanceMigration
                );
            }
        }

        UnifiedConfigMigration.MigrationResult unifiedMigration = UnifiedConfigMigration.prepare(configDirectory);
        if (unifiedMigration == UnifiedConfigMigration.MigrationResult.INVALID_DIRECTORY
                || unifiedMigration == UnifiedConfigMigration.MigrationResult.FAILED) {
            throw new IllegalStateException(
                    "Cannot safely prepare the three Wilderness config files: " + unifiedMigration
            );
        }

        WildernessConfigSpecs.initialize();
        ConfigRegistrationValidator.register(
                container,
                ModConfig.Type.COMMON,
                WildernessConfigSpecs.commonSpec(),
                CONFIG_FOLDER + WildernessConfigSpecs.COMMON_FILE
        );
        ConfigRegistrationValidator.register(
                container,
                ModConfig.Type.CLIENT,
                WildernessConfigSpecs.clientSpec(),
                CONFIG_FOLDER + WildernessConfigSpecs.CLIENT_FILE
        );
        ConfigRegistrationValidator.register(
                container,
                ModConfig.Type.SERVER,
                WildernessConfigSpecs.serverSpec(),
                CONFIG_FOLDER + WildernessConfigSpecs.SERVER_FILE
        );
    }

    /** Applies runtime-backed settings after NeoForge loads a config file. */
    public static void onConfigLoaded(ModConfigEvent.Loading event) {
        applyRuntimeSettings(event.getConfig());
    }

    /** Refreshes runtime-backed settings after a config file is reloaded. */
    public static void onConfigReloaded(ModConfigEvent.Reloading event) {
        applyRuntimeSettings(event.getConfig());
        if (event.getConfig().getSpec() == WildernessConfigSpecs.serverSpec()) {
            applyOnServerThread(() -> SimulationEngine.get().onConfigurationReload());
        }
    }

    private static void applyRuntimeSettings(ModConfig config) {
        if (config.getSpec() == WildernessConfigSpecs.commonSpec()) {
            // Executor replacement and the shared result queue are runtime-owned.
            // Initial config loading precedes server ownership, so it must not
            // create worker pools on the mod-loading thread. ServerStarting
            // initializes them; only a live server receives a reload.
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.execute(() -> AsyncTaskManager.reload(AsyncThreadingConfig.values()));
            }
        } else if (config.getSpec() == WildernessConfigSpecs.clientSpec()) {
            WeatherRenderingConfig.reload();
        } else if (config.getSpec() == WildernessConfigSpecs.serverSpec()) {
            applyOnServerThread(() -> {
                BackgroundEfficiencyManager.reload(BackgroundEfficiencyConfig.values());
                TickEngine.reload(TickEngineConfig.values());
                DataEngine.get().reload(DataEngineConfig.values());
            });
            StructureBlockSettings.reloadFromConfig();
            WeatherConfig.reload();
            WeatherAuthority.get().onConfigurationReload();
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
