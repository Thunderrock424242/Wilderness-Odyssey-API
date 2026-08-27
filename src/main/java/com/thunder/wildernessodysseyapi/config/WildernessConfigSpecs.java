package com.thunder.wildernessodysseyapi.config;

import com.thunder.wildernessodysseyapi.async.AsyncThreadingConfig;
import com.thunder.wildernessodysseyapi.ai.voice.config.AetherVoiceConfig;
import com.thunder.wildernessodysseyapi.dataengine.config.DataEngineConfig;
import com.thunder.wildernessodysseyapi.debugoverlay.config.DebugOverlayConfig;
import com.thunder.wildernessodysseyapi.developmentstudio.config.StudioConfig;
import com.thunder.wildernessodysseyapi.donations.config.DonationReminderConfig;
import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import com.thunder.wildernessodysseyapi.feedback.FeedbackConfig;
import com.thunder.wildernessodysseyapi.meteor.config.MeteorConfig;
import com.thunder.wildernessodysseyapi.ownership.config.OwnershipConfig;
import com.thunder.wildernessodysseyapi.performance.background.config.BackgroundEfficiencyConfig;
import com.thunder.wildernessodysseyapi.performance.tickengine.config.TickEngineConfig;
import com.thunder.wildernessodysseyapi.playtest.verification.MinecraftVerificationRelayConfig;
import com.thunder.wildernessodysseyapi.riftfall.config.RiftfallConfig;
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
import com.thunder.wildernessodysseyapi.worldgen.config.StructureConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.function.Consumer;

/**
 * Assembles the three side-owned Wilderness configuration files.
 *
 * <p>Feature classes continue to own their typed values and runtime behavior.
 * This class owns only file composition, category layout, and compatibility
 * aliases for callers that historically referenced a feature-local spec.</p>
 */
public final class WildernessConfigSpecs {
    public static final String COMMON_FILE = "wildernessodysseyapi-common.toml";
    public static final String CLIENT_FILE = "wildernessodysseyapi-client.toml";
    public static final String SERVER_FILE = "wildernessodysseyapi-server.toml";

    private static boolean initializing;
    private static boolean initialized;
    private static ModConfigSpec commonSpec;
    private static ModConfigSpec clientSpec;
    private static ModConfigSpec serverSpec;

    private WildernessConfigSpecs() {
    }

    /** Ensures all feature categories have been defined exactly once. */
    public static synchronized void initialize() {
        if (initialized || initializing) {
            return;
        }
        initializing = true;
        try {
            commonSpec = buildCommonSpec();
            clientSpec = buildClientSpec();
            serverSpec = buildServerSpec();
            attachCompatibilityAliases();
            initialized = true;
        } finally {
            initializing = false;
        }
    }

    /** Returns the installation-wide common specification. */
    public static ModConfigSpec commonSpec() {
        initialize();
        return commonSpec;
    }

    /** Returns the local client-only specification. */
    public static ModConfigSpec clientSpec() {
        initialize();
        return clientSpec;
    }

    /** Returns the world/server-authoritative specification. */
    public static ModConfigSpec serverSpec() {
        initialize();
        return serverSpec;
    }

    private static ModConfigSpec buildCommonSpec() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        defineCategory(builder, "structures", StructureConfig::define);
        AsyncThreadingConfig.define(builder);
        OwnershipConfig.define(builder);
        return builder.build();
    }

    private static ModConfigSpec buildClientSpec() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        defineCategory(builder, "donations", DonationReminderConfig::define);
        AetherVoiceConfig.define(builder);
        DebugOverlayConfig.define(builder);
        WaterRenderingConfig.define(builder);
        defineCategory(builder, "weather_rendering", WeatherRenderingConfig::define);
        return builder.build();
    }

    private static ModConfigSpec buildServerSpec() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        StructureBlockConfig.define(builder);
        PerformanceServerConfig.define(builder);
        defineCategory(builder, "development_studio", StudioConfig::define);
        MinecraftVerificationRelayConfig.define(builder);
        TelemetryConfig.define(builder);
        PlayerTelemetryConfig.define(builder);
        EventTelemetryConfig.define(builder);
        FeedbackConfig.define(builder);
        RiftfallConfig.define(builder);
        MeteorConfig.define(builder);
        TemporalRiftConfig.define(builder);
        WaterSimulationConfig.define(builder);
        WeatherConfig.define(builder);
        EcosystemConfig.define(builder);
        VegetationConfig.define(builder);
        return builder.build();
    }

    private static void defineCategory(
            ModConfigSpec.Builder builder,
            String category,
            Consumer<ModConfigSpec.Builder> definition
    ) {
        builder.push(category);
        definition.accept(builder);
        builder.pop();
    }

    private static void attachCompatibilityAliases() {
        StructureConfig.CONFIG_SPEC = commonSpec;
        AsyncThreadingConfig.CONFIG_SPEC = commonSpec;
        OwnershipConfig.CONFIG_SPEC = commonSpec;

        DonationReminderConfig.CONFIG_SPEC = clientSpec;
        AetherVoiceConfig.CONFIG_SPEC = clientSpec;
        DebugOverlayConfig.CONFIG_SPEC = clientSpec;
        WaterRenderingConfig.CONFIG_SPEC = clientSpec;
        WeatherRenderingConfig.CONFIG_SPEC = clientSpec;

        StructureBlockConfig.CONFIG_SPEC = serverSpec;
        PerformanceServerConfig.attachSpec(serverSpec);
        StudioConfig.CONFIG_SPEC = serverSpec;
        MinecraftVerificationRelayConfig.CONFIG_SPEC = serverSpec;
        TelemetryConfig.CONFIG_SPEC = serverSpec;
        PlayerTelemetryConfig.CONFIG_SPEC = serverSpec;
        EventTelemetryConfig.CONFIG_SPEC = serverSpec;
        FeedbackConfig.CONFIG_SPEC = serverSpec;
        RiftfallConfig.CONFIG_SPEC = serverSpec;
        MeteorConfig.SPEC = serverSpec;
        TemporalRiftConfig.CONFIG_SPEC = serverSpec;
        WaterSimulationConfig.CONFIG_SPEC = serverSpec;
        WeatherConfig.CONFIG_SPEC = serverSpec;
        EcosystemConfig.CONFIG_SPEC = serverSpec;
        VegetationConfig.CONFIG_SPEC = serverSpec;

        BackgroundEfficiencyConfig.attachSpec(serverSpec);
        TickEngineConfig.attachSpec(serverSpec);
        DataEngineConfig.attachSpec(serverSpec);
    }
}
