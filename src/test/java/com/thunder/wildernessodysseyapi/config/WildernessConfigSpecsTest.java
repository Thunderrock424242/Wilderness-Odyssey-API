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
import com.thunder.wildernessodysseyapi.rendering.config.RendererConfig;
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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Verifies side ownership, compatibility aliases, and the visible category layout. */
class WildernessConfigSpecsTest {

    @Test
    void featureConfigsShareExactlyOneSpecPerNeoForgeType() {
        WildernessConfigSpecs.initialize();

        assertSame(WildernessConfigSpecs.commonSpec(), StructureConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.commonSpec(), AsyncThreadingConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.commonSpec(), OwnershipConfig.CONFIG_SPEC);

        assertSame(WildernessConfigSpecs.clientSpec(), DonationReminderConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.clientSpec(), AetherVoiceConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.clientSpec(), DebugOverlayConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.clientSpec(), RendererConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.clientSpec(), WaterRenderingConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.clientSpec(), WeatherRenderingConfig.CONFIG_SPEC);

        assertSame(WildernessConfigSpecs.serverSpec(), StructureBlockConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.serverSpec(), PerformanceServerConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.serverSpec(), BackgroundEfficiencyConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.serverSpec(), TickEngineConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.serverSpec(), DataEngineConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.serverSpec(), StudioConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.serverSpec(), MinecraftVerificationRelayConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.serverSpec(), TelemetryConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.serverSpec(), PlayerTelemetryConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.serverSpec(), EventTelemetryConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.serverSpec(), FeedbackConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.serverSpec(), RiftfallConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.serverSpec(), MeteorConfig.SPEC);
        assertSame(WildernessConfigSpecs.serverSpec(), TemporalRiftConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.serverSpec(), WaterSimulationConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.serverSpec(), WeatherConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.serverSpec(), EcosystemConfig.CONFIG_SPEC);
        assertSame(WildernessConfigSpecs.serverSpec(), VegetationConfig.CONFIG_SPEC);
    }

    @Test
    void multiSectionSystemsHaveClearOuterCategories() {
        WildernessConfigSpecs.initialize();

        assertEquals(
                List.of("structures", "placement", "enableAutoTerrainBlend"),
                StructureConfig.ENABLE_AUTO_TERRAIN_BLEND.getPath()
        );
        assertEquals(
                List.of("donations", "disableReminder"),
                DonationReminderConfig.disableReminder.getPath()
        );
        assertEquals(
                List.of("aether_voice", "enabled"),
                AetherVoiceConfig.VOICE_ENABLED.getPath()
        );
        assertEquals(
                List.of("renderer", "adaptiveQuality"),
                RendererConfig.ADAPTIVE_QUALITY.getPath()
        );
        assertEquals(
                List.of("weather_rendering", "localized_clouds", "enabled"),
                WeatherRenderingConfig.ENABLE_LOCALIZED_CLOUDS.getPath()
        );
        assertEquals(
                List.of("development_studio", "access", "allowInNormalWorlds"),
                StudioConfig.ALLOW_IN_NORMAL_WORLDS.getPath()
        );
        assertEquals(
                List.of("meteor_event", "enableNaturalMeteorEvents"),
                MeteorConfig.NATURAL_EVENTS_ENABLED.getPath()
        );
        assertEquals(
                List.of("water_simulation", "enableWildernessOdysseyWater"),
                WaterSimulationConfig.ENABLE_WILDERNESS_ODYSSEY_WATER.getPath()
        );
        assertEquals(
                List.of("weather", "enabled"),
                WeatherConfig.WEATHER_SYSTEM_ENABLED.getPath()
        );
        assertEquals(
                List.of("ecosystem", "enabled"),
                EcosystemConfig.ENABLED.getPath()
        );
        assertEquals(
                List.of("ecosystem", "distantWildlife", "populationEcology", "regionalCarryingCapacity"),
                EcosystemConfig.REGIONAL_CARRYING_CAPACITY.getPath()
        );
    }
}
