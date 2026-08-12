package com.thunder.wildernessodysseyapi.core;

import com.thunder.wildernessodysseyapi.ai.story.AIChatListener;
import com.thunder.wildernessodysseyapi.anomaly.registry.AnomalyBlocks;
import com.thunder.wildernessodysseyapi.command.ModCommands;
import com.thunder.wildernessodysseyapi.config.ModConfigRegistration;
import com.thunder.wildernessodysseyapi.cryo.block.CryoTubeBlock;
import com.thunder.wildernessodysseyapi.donations.config.DonationReminderConfig;
import com.thunder.wildernessodysseyapi.developmentstudio.campus.StudioLocationRegistry;
import com.thunder.wildernessodysseyapi.developmentstudio.data.StudioWorldgenData;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioInspectionRegistry;
import com.thunder.wildernessodysseyapi.developmentstudio.module.StudioModuleRegistry;
import com.thunder.wildernessodysseyapi.ecosystem.EcosystemEvents;
import com.thunder.wildernessodysseyapi.item.ModItems;
import com.thunder.wildernessodysseyapi.lorebook.LoreBookEvents;
import com.thunder.wildernessodysseyapi.meteor.event.MeteorImpactEvent;
import com.thunder.wildernessodysseyapi.network.ModPayloads;
import com.thunder.wildernessodysseyapi.radiation.RadiationTickHandler;
import com.thunder.wildernessodysseyapi.server.ServerLifecycleEvents;
import com.thunder.wildernessodysseyapi.structuregen.content.StructureBlockCatalogSnapshotProvider;
import com.thunder.wildernessodysseyapi.telemetry.EventTelemetryReporter;
import com.thunder.wildernessodysseyapi.telemetry.PlayerTelemetryReporter;
import com.thunder.wildernessodysseyapi.telemetry.TelemetryQueueProcessor;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftBlocks;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideWorldUpdater;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.WaterCompatibilityRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla.VanillaWaterBucketCompatibility;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.weather.integration.survival.SurvivalWeatherIntegrations;
import com.thunder.wildernessodysseyapi.worldgen.biome.BiomeCompatibilityBootstrap;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import static com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER;
import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

/**
 * Wires the Wilderness Odyssey API modules into NeoForge.
 *
 * <p>The entrypoint deliberately owns only startup orchestration. Registries,
 * configs, payloads, commands, and runtime events live in focused classes so
 * feature code does not accumulate in the mod constructor.</p>
 */
@Mod(MOD_ID)
public final class WildernessOdysseyAPIMainModClass {

    private final ModContainer container;

    /**
     * Creates the mod and registers its NeoForge integration points.
     *
     * @param modEventBus the mod-scoped lifecycle and registry event bus
     * @param container the active mod container used for config registration
     */
    public WildernessOdysseyAPIMainModClass(IEventBus modEventBus, ModContainer container) {
        this.container = container;
        LOGGER.info("Initializing Wilderness Odyssey API and mod-conflict tracking");

        WildernessWaterRules.bootstrap();
        WaterCompatibilityRegistry.bootstrap(modEventBus);

        // Mod-bus listeners handle lifecycle work that NeoForge runs during startup.
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModPayloads::register);
        modEventBus.addListener(ModConfigRegistration::onConfigLoaded);
        modEventBus.addListener(ModConfigRegistration::onConfigReloaded);
        modEventBus.addListener(this::addCreativeTabEntries);
        modEventBus.addListener(StudioWorldgenData::onGatherData);
        modEventBus.addListener(StructureBlockCatalogSnapshotProvider::onGatherData);

        ModRegistries.register(modEventBus);
        ModConfigRegistration.register(container);
        SurvivalWeatherIntegrations.bootstrap();
        registerGameEventHandlers();
        RadiationTickHandler.register();

        DonationReminderConfig.validateVersion();
    }

    // Common setup is queued because biome compatibility touches registries after construction.
    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            VanillaWaterBucketCompatibility.bootstrap();
            BiomeCompatibilityBootstrap.initialize();
            StudioLocationRegistry.bootstrapDefaults();
            StudioModuleRegistry.bootstrapDefaults();
            StudioInspectionRegistry.bootstrapDefaults();
            LOGGER.info("Wilderness Odyssey API setup complete");
        });
        LOGGER.info("Mod pack version: {}", container.getModInfo().getVersion());
    }

    // Adds the mod's utility items only when NeoForge is building the matching vanilla tab.
    private void addCreativeTabEntries(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.TOOLS_AND_UTILITIES) {
            return;
        }

        event.accept(CryoTubeBlock.CRYO_TUBE.get());
        event.accept(TemporalRiftBlocks.TIME_CAPSULE.get());
        event.accept(AnomalyBlocks.ANOMALY_GATEWAY.get());
        event.accept(ModItems.FIELD_CODEX.get());
        event.accept(ModItems.STUDIO_DEVELOPER_TOOL.get());
    }

    // The game bus owns live server/world events after mod construction is complete.
    private static void registerGameEventHandlers() {
        NeoForge.EVENT_BUS.register(AIChatListener.class);
        NeoForge.EVENT_BUS.register(PlayerTelemetryReporter.class);
        NeoForge.EVENT_BUS.register(EventTelemetryReporter.class);
        NeoForge.EVENT_BUS.register(TelemetryQueueProcessor.class);
        NeoForge.EVENT_BUS.register(LoreBookEvents.class);
        NeoForge.EVENT_BUS.register(EcosystemEvents.class);
        NeoForge.EVENT_BUS.register(MeteorImpactEvent.class);
        NeoForge.EVENT_BUS.register(TideWorldUpdater.class);
        NeoForge.EVENT_BUS.register(ModCommands.class);
        NeoForge.EVENT_BUS.register(ServerLifecycleEvents.class);
    }
}
