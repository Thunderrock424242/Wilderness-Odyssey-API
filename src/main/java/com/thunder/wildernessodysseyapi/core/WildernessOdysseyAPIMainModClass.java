package com.thunder.wildernessodysseyapi.core;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.ModPackPatches.bugfixes.InfiniteSourceHandler;
import com.thunder.wildernessodysseyapi.ModPackPatches.faq.FaqCommand;
import com.thunder.wildernessodysseyapi.ModPackPatches.faq.FaqReloadListener;
import com.thunder.wildernessodysseyapi.ModPackPatches.ModListTracker.commands.ModListDiffCommand;
import com.thunder.wildernessodysseyapi.ModPackPatches.ModListTracker.commands.ModListVersionCommand;
import com.thunder.wildernessodysseyapi.ModPackPatches.ModListTracker.commands.ConfigAuditCommand;
import com.thunder.wildernessodysseyapi.command.*;
import com.thunder.wildernessodysseyapi.feedback.FeedbackCommand;
import com.thunder.wildernessodysseyapi.feedback.FeedbackConfig;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideWorldUpdater;
import com.thunder.wildernessodysseyapi.watersystem.water.entity.BoatTiltStore;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import com.thunder.wildernessodysseyapi.worldgen.blocks.CryoTubeBlock;
import com.thunder.wildernessodysseyapi.worldgen.configurable.StructureConfig;
import com.thunder.wildernessodysseyapi.worldgen.processor.ModProcessors;
import com.thunder.wildernessodysseyapi.worldgen.biome.BiomeCompatibilityBootstrap;
import com.thunder.wildernessodysseyapi.worldgen.modpack.ModpackStructureRegistry;
import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.async.AsyncThreadingConfig;
import com.thunder.wildernessodysseyapi.donations.command.DonateCommand;
import com.thunder.wildernessodysseyapi.config.*;
import com.thunder.wildernessodysseyapi.item.ModCreativeTabs;
import com.thunder.wildernessodysseyapi.entity.ModEntities;
import com.thunder.wildernessodysseyapi.item.ModItems;
import com.thunder.wildernessodysseyapi.item.ModSoundEvents;
import com.thunder.wildernessodysseyapi.lorebook.LoreBookEvents;
import com.thunder.wildernessodysseyapi.lorebook.loot.ModLootConditions;
import com.thunder.wildernessodysseyapi.lorebook.loot.ModLootFunctions;
import com.thunder.wildernessodysseyapi.util.StructureBlockSettings;
import com.thunder.wildernessodysseyapi.ai.AI_story.AIBackendCommand;
import com.thunder.wildernessodysseyapi.ai.AI_story.AIChatListener;
import com.thunder.wildernessodysseyapi.donations.config.DonationReminderConfig;
import com.thunder.wildernessodysseyapi.globalchat.GlobalChatManager;
import com.thunder.wildernessodysseyapi.ModPackPatches.rules.GameRulesListManager;
import com.thunder.wildernessodysseyapi.ModPackPatches.telemetry.*;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.particle.WildernessParticleRegistry;

import com.thunder.wildernessodysseyapi.worldgen.spawn.OceanSpawnLocator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Map;

import static com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER;
import static com.thunder.wildernessodysseyapi.core.ModConstants.VERSION;

@Mod(ModConstants.MOD_ID)
public class WildernessOdysseyAPIMainModClass {

    private static final String CONFIG_FOLDER = ModConstants.MOD_ID + "/";
    private static final Map<CustomPacketPayload.Type<?>, NetworkMessage<?>> MESSAGES = new HashMap<>();
    private final GlobalChatManager globalChatManager = GlobalChatManager.getInstance();

    private record NetworkMessage<T extends CustomPacketPayload>(StreamCodec<? extends FriendlyByteBuf, T> reader, IPayloadHandler<T> handler) {}

    public WildernessOdysseyAPIMainModClass(IEventBus modEventBus, ModContainer container) {
        LOGGER.info("WildernessOdysseyAPI initialized. I will also start to track mod conflicts");

        // 1. Mod Bus Events
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::onConfigLoaded);
        modEventBus.addListener(this::addCreative);

        // 2. Registries
        registerRegistries(modEventBus);

        // 3. Forge Bus Events
        registerEventHandlers();

        // 4. Configs
        registerConfigs(container);

        DonationReminderConfig.validateVersion();
    }

    // =========================================
    // REGISTRATION HELPERS
    // =========================================

    private void registerRegistries(IEventBus modEventBus) {
        ModProcessors.PROCESSORS.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModLootFunctions.LOOT_FUNCTIONS.register(modEventBus);
        ModLootConditions.LOOT_CONDITIONS.register(modEventBus);

        CryoTubeBlock.register(modEventBus);
        ModItems.register(modEventBus);
        ModSoundEvents.register(modEventBus);
        WildernessFluidRegistry.register(modEventBus);
        WildernessParticleRegistry.register(modEventBus);
    }

    private void registerEventHandlers() {
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(InfiniteSourceHandler.class);
        NeoForge.EVENT_BUS.register(AIChatListener.class);
        NeoForge.EVENT_BUS.register(PlayerTelemetryReporter.class);
        NeoForge.EVENT_BUS.register(EventTelemetryReporter.class);
        NeoForge.EVENT_BUS.register(TelemetryQueueProcessor.class);
        NeoForge.EVENT_BUS.register(LoreBookEvents.class);
        NeoForge.EVENT_BUS.register(TideWorldUpdater.class);
        NeoForge.EVENT_BUS.register(OceanSpawnLocator.class);
    }

    private void registerConfigs(ModContainer container) {
        ConfigRegistrationValidator.register(container, ModConfig.Type.COMMON, StructureConfig.CONFIG_SPEC, CONFIG_FOLDER + "wildernessodysseyapi-structures.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.COMMON, AsyncThreadingConfig.CONFIG_SPEC, CONFIG_FOLDER + "wildernessodysseyapi-async.toml");

        ConfigRegistrationValidator.register(container, ModConfig.Type.CLIENT, DonationReminderConfig.CONFIG_SPEC, CONFIG_FOLDER + "wildernessodysseyapi-donations-client.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.CLIENT, CurioRenderConfig.CONFIG_SPEC, CONFIG_FOLDER + "wildernessodysseyapi-curio-rendering-client.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.CLIENT, TelemetryConsentConfig.CONFIG_SPEC, CONFIG_FOLDER + "wildernessodysseyapi-telemetry-client.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.CLIENT, DebugOverlayConfig.CONFIG_SPEC, CONFIG_FOLDER + "wildernessodysseyapi-debug-overlay-client.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.CLIENT, TrueDarknessConfig.CONFIG_SPEC, CONFIG_FOLDER + "wildernessodysseyapi-true-darkness-client.toml");

        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, StructureBlockConfig.CONFIG_SPEC, CONFIG_FOLDER + "wildernessodysseyapi-structureblocks-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, CloakChipConfig.CONFIG_SPEC, CONFIG_FOLDER + "wildernessodysseyapi-cloak-chip-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, PlayerTelemetryConfig.CONFIG_SPEC, CONFIG_FOLDER + "wildernessodysseyapi-telemetry-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, EventTelemetryConfig.CONFIG_SPEC, CONFIG_FOLDER + "wildernessodysseyapi-event-telemetry-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, TelemetryConfig.CONFIG_SPEC, CONFIG_FOLDER + "wildernessodysseyapi-telemetry-master-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, FeedbackConfig.CONFIG_SPEC, CONFIG_FOLDER + "wildernessodysseyapi-feedback-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.COMMON, OwnershipConfig.CONFIG_SPEC, CONFIG_FOLDER + "wildernessodysseyapi-ownership.toml");
    }

    // =========================================
    // LIFECYCLE & SETUP
    // =========================================

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            BiomeCompatibilityBootstrap.initialize();
            LOGGER.info("Wilderness Odyssey setup complete!");
        });
        LOGGER.warn("Mod Pack Version: {}", VERSION);
        LOGGER.warn("This message is for development purposes only.");
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(CryoTubeBlock.CRYO_TUBE.get());
        }
    }

    // =========================================
    // SERVER EVENTS
    // =========================================

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        AsyncTaskManager.initialize(AsyncThreadingConfig.values());
        globalChatManager.initialize(event.getServer(), event.getServer().getFile("config"));
        GameRulesListManager.ensureRulesFileExists(event.getServer());
        GameRulesListManager.applyConfiguredRules(event.getServer());
        ModpackStructureRegistry.loadAll();

        if (OwnershipConfig.CONFIG.showNoticeOnStartup()) {
            LOGGER.info("[Ownership] Project: {}", OwnershipConfig.CONFIG.projectName());
            LOGGER.info("[Ownership] Owner: {}", OwnershipConfig.CONFIG.ownerName());
            LOGGER.info("[Ownership] Notice: {}", OwnershipConfig.CONFIG.ownershipNotice());
            LOGGER.info("[Ownership] Contact: {}", OwnershipConfig.CONFIG.supportContact());
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        // Phase 1 Fix: Flushes the async queue on the main thread safely
        if (event.hasTime() && event.getServer() != null) {
            AsyncTaskManager.drainMainThreadQueue(event.getServer());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        globalChatManager.shutdown();
        AsyncTaskManager.shutdown();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        ModListDiffCommand.register(dispatcher);
        ModListVersionCommand.register(dispatcher);
        ConfigAuditCommand.register(dispatcher);
        StructureInfoCommand.register(dispatcher);
        FaqCommand.register(dispatcher);
        DonateCommand.register(dispatcher);
        ChangelogCommand.register(dispatcher);
        WorldGenScanCommand.register(dispatcher);
        StructurePlacementDebugCommand.register(dispatcher);
        GlobalChatCommand.register(dispatcher);
        GlobalChatOptToggleCommand.register(dispatcher);
        LoreBookCommand.register(dispatcher);
        ModpackStructureCommand.register(dispatcher);
        TelemetryConsentCommand.register(dispatcher);
        TelemetryQueueStatsCommand.register(dispatcher);
        FeedbackCommand.register(dispatcher);
        WorldUpgradeCommand.register(dispatcher);
        MeteorCommand.register(dispatcher);
        UnstuckCommand.register(dispatcher);
        AIBackendCommand.register(dispatcher);
    }

    // =========================================
    // PLAYER & WORLD EVENTS
    // =========================================

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal("[GlobalChat] Global chat is opt-in. Use /globalchatoptin to join or /globalchatoptout to leave."));
        }
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        WaterBodyClassifier.clearCache();
        BoatTiltStore.clear();
        SPHSimulationManager.get().shutdown();
    }

    @SubscribeEvent
    public void onReload(AddReloadListenerEvent event) {
        event.addListener(new FaqReloadListener());
    }

    public void onConfigLoaded(ModConfigEvent.Loading event) {
        handleConfigUpdate(event.getConfig());
    }

    public void onConfigReloaded(ModConfigEvent.Reloading event) {
        handleConfigUpdate(event.getConfig());
    }

    private void handleConfigUpdate(ModConfig config) {
        if (config.getSpec() == AsyncThreadingConfig.CONFIG_SPEC) {
            AsyncTaskManager.initialize(AsyncThreadingConfig.values());
        } else if (config.getSpec() == StructureBlockConfig.CONFIG_SPEC) {
            StructureBlockSettings.reloadFromConfig();
        }
    }
}