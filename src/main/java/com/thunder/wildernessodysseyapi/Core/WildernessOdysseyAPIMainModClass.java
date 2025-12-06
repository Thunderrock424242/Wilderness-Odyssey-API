package com.thunder.wildernessodysseyapi.Core;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.ModPackPatches.BugFixes.InfiniteSourceHandler;
import com.thunder.wildernessodysseyapi.ErrorLog.UncaughtExceptionLogger;
import com.thunder.wildernessodysseyapi.ModPackPatches.FAQ.FaqCommand;
import com.thunder.wildernessodysseyapi.ModPackPatches.FAQ.FaqReloadListener;
import com.thunder.wildernessodysseyapi.ModPackPatches.cache.ModDataCache;
import com.thunder.wildernessodysseyapi.ModPackPatches.cache.ModDataCacheCommand;
import com.thunder.wildernessodysseyapi.ModPackPatches.cache.ModDataCacheConfig;
import com.thunder.wildernessodysseyapi.MemUtils.MemCheckCommand;
import com.thunder.wildernessodysseyapi.MemUtils.MemoryUtils;
import com.thunder.wildernessodysseyapi.ModListTracker.commands.ModListDiffCommand;
import com.thunder.wildernessodysseyapi.ModListTracker.commands.ModListVersionCommand;
import com.thunder.wildernessodysseyapi.command.GlobalChatCommand;
import com.thunder.wildernessodysseyapi.command.GlobalChatOptToggleCommand;
import com.thunder.wildernessodysseyapi.analytics.AnalyticsTracker;
import com.thunder.wildernessodysseyapi.command.AnalyticsCommand;
import com.thunder.wildernessodysseyapi.WorldGen.blocks.CryoTubeBlock;
import com.thunder.wildernessodysseyapi.WorldGen.blocks.TerrainReplacerBlock;
import com.thunder.wildernessodysseyapi.WorldGen.configurable.StructureConfig;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.Features.ModFeatures;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.biome.ModBiomeModifiers;
import com.thunder.wildernessodysseyapi.WorldGen.processor.ModProcessors;
import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.async.AsyncThreadingConfig;
import com.thunder.wildernessodysseyapi.command.AiAdvisorCommand;
import com.thunder.wildernessodysseyapi.command.AsyncStatsCommand;
import com.thunder.wildernessodysseyapi.command.StructureInfoCommand;
import com.thunder.wildernessodysseyapi.donations.command.DonateCommand;
import com.thunder.wildernessodysseyapi.command.DoorLockCommand;
import com.thunder.wildernessodysseyapi.command.WorldGenScanCommand;
import com.thunder.wildernessodysseyapi.config.ConfigRegistrationValidator;
import com.thunder.wildernessodysseyapi.config.StructureBlockConfig;
import com.thunder.wildernessodysseyapi.item.ModCreativeTabs;
import com.thunder.wildernessodysseyapi.item.ModItems;
import com.thunder.wildernessodysseyapi.util.StructureBlockSettings;
import com.thunder.wildernessodysseyapi.AI_story.AIChatListener;
import com.thunder.wildernessodysseyapi.AntiCheat.AntiCheatConfig;
import com.thunder.wildernessodysseyapi.AntiCheat.BlacklistChecker;
import com.thunder.wildernessodysseyapi.AI_perf.requestperfadvice;
import com.thunder.wildernessodysseyapi.AI_perf.PerformanceAdvisor;
import com.thunder.wildernessodysseyapi.AI_perf.PerformanceAdvisoryRequest;
import com.thunder.wildernessodysseyapi.AI_perf.PerformanceMitigationController;
import com.thunder.wildernessodysseyapi.donations.config.DonationReminderConfig;
import com.thunder.wildernessodysseyapi.globalchat.GlobalChatManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.BunkerProtectionHandler;
import com.thunder.wildernessodysseyapi.WorldGen.datapack.ImpactSitePlacementLoader;
import com.thunder.wildernessodysseyapi.WorldGen.util.DeferredTaskScheduler;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.world.item.CreativeModeTabs;


import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;
import static com.thunder.wildernessodysseyapi.Core.ModConstants.VERSION;

/**
 * The type Wilderness odyssey api main mod class.
 */
@Mod(ModConstants.MOD_ID)
public class WildernessOdysseyAPIMainModClass {

    public static int dynamicModCount = 0;
    private static final String CONFIG_FOLDER = ModConstants.MOD_ID + "/";

    private static AABB structureBoundingBox;

    private int serverTickCounter = 0;
    private static final int LOG_INTERVAL = 600;

    private long lastTickTimeNanos = 0L;
    private long worstTickTimeNanos = 0L;
    private final requestperfadvice requestperfadvice = new requestperfadvice();

    private static final Map<CustomPacketPayload.Type<?>, NetworkMessage<?>> MESSAGES = new HashMap<>();
    private final GlobalChatManager globalChatManager = GlobalChatManager.getInstance();

    private record NetworkMessage<T extends CustomPacketPayload>(StreamCodec<? extends FriendlyByteBuf, T> reader,
                                                                 IPayloadHandler<T> handler) {
    }

    /**
     * Instantiates a new Wilderness odyssey api main mod class.
     *
     * @param modEventBus the mod event bus
     * @param container   the container
     */
    public WildernessOdysseyAPIMainModClass(IEventBus modEventBus, ModContainer container) {
        LOGGER.info("WildernessOdysseyAPI initialized. I will also start to track mod conflicts");

        // Register mod setup and creative tabs
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onConfigLoaded);
        modEventBus.addListener(this::onConfigReloaded);
        ModFeatures.FEATURES.register(modEventBus);
        ModFeatures.CONFIGURED_FEATURES.register(modEventBus);
        ModFeatures.PLACED_FEATURES.register(modEventBus);
        ModBiomeModifiers.BIOME_MODIFIERS.register(modEventBus);
        ModProcessors.PROCESSORS.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        // Register global events
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(BlacklistChecker.class);
        NeoForge.EVENT_BUS.register(InfiniteSourceHandler.class);
        NeoForge.EVENT_BUS.register(AIChatListener.class);

        CryoTubeBlock.register(modEventBus);
        TerrainReplacerBlock.register(modEventBus);
        ModItems.register(modEventBus);

        ConfigRegistrationValidator.register(container, ModConfig.Type.COMMON, StructureConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-structures.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.COMMON, ModDataCacheConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-cache.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.CLIENT, DonationReminderConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-donations-client.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.COMMON, AsyncThreadingConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-async.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, AntiCheatConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-anticheat-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, StructureBlockConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-structureblocks-server.toml");
        // Previously registered client-only events have been removed
        DonationReminderConfig.validateVersion();

    }


    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            System.out.println("Wilderness Odyssey setup complete!");
            ModDataCache.initialize();
        });
        LOGGER.warn("Mod Pack Version: {}", VERSION); // Logs as a warning
        LOGGER.warn("This message is for development purposes only."); // Logs as info
        UncaughtExceptionLogger.init();
        dynamicModCount = ModList.get().getMods().size();
    }
  
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(CryoTubeBlock.CRYO_TUBE.get());
        }
    }
    /**
     * On server starting.
     *
     * @param event the event
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event){
        BunkerProtectionHandler.clear();
        AsyncTaskManager.initialize(AsyncThreadingConfig.values());
        globalChatManager.initialize(event.getServer(), event.getServer().getFile("config"));
        AnalyticsTracker.initialize(event.getServer(), event.getServer().getFile("config"));
    }

    /**
     * On register commands.
     *
     * @param event the event
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        ModListDiffCommand.register(dispatcher);
        ModListVersionCommand.register(dispatcher);
        MemCheckCommand.register(event.getDispatcher());
        StructureInfoCommand.register(event.getDispatcher());
        FaqCommand.register(event.getDispatcher());
        ModDataCacheCommand.register(dispatcher);
        DonateCommand.register(event.getDispatcher());
        DoorLockCommand.register(event.getDispatcher());
        WorldGenScanCommand.register(event.getDispatcher());
        AiAdvisorCommand.register(event.getDispatcher());
        AsyncStatsCommand.register(dispatcher);
        AnalyticsCommand.register(dispatcher);
        GlobalChatCommand.register(dispatcher);
        GlobalChatOptToggleCommand.register(dispatcher);
    }

    /**
     * Schedule meteor and bunker placement shortly after the first player joins.
     *
     * @param event the login event
     */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        player.sendSystemMessage(Component.literal("[GlobalChat] Global chat is opt-in. Use /globalchatoptin to join or /globalchatoptout to leave."));
    }
    /**
     * On mob spawn.
     *
     * @param event the event
     */
    @SubscribeEvent
    public void onMobSpawn(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Mob mob) {
            if (mob.getType().getCategory() == MobCategory.MONSTER && structureBoundingBox != null) {
                if (structureBoundingBox.contains(mob.blockPosition().getX(), mob.blockPosition().getY(), mob.blockPosition().getZ())) {
                    event.setCanceled(true); // Prevent mob spawn
                }
            }
        }
    }



    /**
     * On server stopping.
     *
     * @param event the event
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        globalChatManager.shutdown();
        BunkerProtectionHandler.clear();
        AsyncTaskManager.shutdown();
        AnalyticsTracker.shutdown();
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        // Register structure placer or any late logic
        //NeoForge.EVENT_BUS.register(new WorldEvents());
        /// i think we don't need this anymore ^ but keep for now.
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        // Every server tick event
        // This is equivalent to the old "END" phase.
        MinecraftServer server = event.getServer();
        long now = System.nanoTime();
        if (lastTickTimeNanos != 0L) {
            long duration = now - lastTickTimeNanos;
            worstTickTimeNanos = Math.max(worstTickTimeNanos, duration);
        }
        lastTickTimeNanos = now;
        AsyncTaskManager.drainMainThreadQueue(server);
        PerformanceMitigationController.tick(server);
        DeferredTaskScheduler.tick();
        if (!event.hasTime()) return;

        if (++serverTickCounter >= LOG_INTERVAL) {
            serverTickCounter = 0;
            long usedMB = MemoryUtils.getUsedMemoryMB();
            long totalMB = MemoryUtils.getTotalMemoryMB();

            // Use the dynamic mod count
            int recommendedMB = MemoryUtils.calculateRecommendedRAM(usedMB, dynamicModCount);

            LOGGER.info("[ResourceManager] Memory usage: {}MB / {}MB. Recommended ~{}MB for {} loaded mods.", (Object) Optional.of(usedMB), (Object) totalMB, (Object) recommendedMB, (Object) dynamicModCount);

            long worstTickMillis = TimeUnit.NANOSECONDS.toMillis(worstTickTimeNanos);
            worstTickTimeNanos = 0L;
            if (worstTickMillis > PerformanceAdvisor.DEFAULT_TICK_BUDGET_MS) {
                PerformanceAdvisoryRequest request = PerformanceAdvisor.observe(server, worstTickMillis);
                PerformanceMitigationController.buildActionsFromRequest(request);
                String advisory = requestperfadvice.requestPerformanceAdvice(request);
                LOGGER.info("[AI Advisor] {}", advisory);
            }
        }
    }
    @SubscribeEvent
    public void onReload(AddReloadListenerEvent event) {
        event.addListener(new FaqReloadListener());
        event.addListener(new ImpactSitePlacementLoader());
    }

    public void onConfigLoaded(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ModDataCacheConfig.CONFIG_SPEC) {
            ModDataCache.initialize();
        }
        if (event.getConfig().getSpec() == AsyncThreadingConfig.CONFIG_SPEC) {
            AsyncTaskManager.initialize(AsyncThreadingConfig.values());
        }
        if (event.getConfig().getSpec() == StructureBlockConfig.CONFIG_SPEC) {
            StructureBlockSettings.reloadFromConfig();
        }
    }

    public void onConfigReloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ModDataCacheConfig.CONFIG_SPEC) {
            ModDataCache.initialize();
        }
        if (event.getConfig().getSpec() == AsyncThreadingConfig.CONFIG_SPEC) {
            AsyncTaskManager.initialize(AsyncThreadingConfig.values());
        }
        if (event.getConfig().getSpec() == StructureBlockConfig.CONFIG_SPEC) {
            StructureBlockSettings.reloadFromConfig();
        }
    }
}
