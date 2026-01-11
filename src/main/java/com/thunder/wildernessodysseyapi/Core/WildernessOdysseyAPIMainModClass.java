package com.thunder.wildernessodysseyapi.Core;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.ModPackPatches.BugFixes.InfiniteSourceHandler;
import com.thunder.wildernessodysseyapi.ModPackPatches.FAQ.FaqCommand;
import com.thunder.wildernessodysseyapi.ModPackPatches.FAQ.FaqReloadListener;
import com.thunder.wildernessodysseyapi.ModPackPatches.ModListTracker.commands.ModListDiffCommand;
import com.thunder.wildernessodysseyapi.ModPackPatches.ModListTracker.commands.ModListVersionCommand;
import com.thunder.wildernessodysseyapi.command.GlobalChatCommand;
import com.thunder.wildernessodysseyapi.command.GlobalChatOptToggleCommand;
import com.thunder.wildernessodysseyapi.WorldGen.blocks.CryoTubeBlock;
import com.thunder.wildernessodysseyapi.WorldGen.blocks.TerrainReplacerBlock;
import com.thunder.wildernessodysseyapi.WorldGen.configurable.StructureConfig;
import com.thunder.wildernessodysseyapi.WorldGen.processor.ModProcessors;
import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.async.AsyncThreadingConfig;
import com.thunder.wildernessodysseyapi.command.StructureInfoCommand;
import com.thunder.wildernessodysseyapi.donations.command.DonateCommand;
import com.thunder.wildernessodysseyapi.command.WorldGenScanCommand;
import com.thunder.wildernessodysseyapi.command.StructurePlacementDebugCommand;
import com.thunder.wildernessodysseyapi.command.TideInfoCommand;
import com.thunder.wildernessodysseyapi.config.ConfigRegistrationValidator;
import com.thunder.wildernessodysseyapi.config.StructureBlockConfig;
import com.thunder.wildernessodysseyapi.item.ModCreativeTabs;
import com.thunder.wildernessodysseyapi.item.ModItems;
import com.thunder.wildernessodysseyapi.util.StructureBlockSettings;
import com.thunder.wildernessodysseyapi.AI.AI_story.AIChatListener;
import com.thunder.wildernessodysseyapi.AntiCheat.AntiCheatConfig;
import com.thunder.wildernessodysseyapi.AntiCheat.BlacklistChecker;
import com.thunder.wildernessodysseyapi.donations.config.DonationReminderConfig;
import com.thunder.wildernessodysseyapi.globalchat.GlobalChatManager;
import com.thunder.wildernessodysseyapi.tide.TideConfig;
import com.thunder.wildernessodysseyapi.tide.TideManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.world.item.CreativeModeTabs;


import java.util.HashMap;
import java.util.Map;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;
import static com.thunder.wildernessodysseyapi.Core.ModConstants.VERSION;

/**
 * The type Wilderness odyssey api main mod class.
 */
@Mod(ModConstants.MOD_ID)
public class WildernessOdysseyAPIMainModClass {

    private static final String CONFIG_FOLDER = ModConstants.MOD_ID + "/";


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
        ModProcessors.PROCESSORS.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);

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
        ConfigRegistrationValidator.register(container, ModConfig.Type.CLIENT, DonationReminderConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-donations-client.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.COMMON, AsyncThreadingConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-async.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, AntiCheatConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-anticheat-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, StructureBlockConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-structureblocks-server.toml");
        ConfigRegistrationValidator.register(container, ModConfig.Type.SERVER, TideConfig.CONFIG_SPEC,
                CONFIG_FOLDER + "wildernessodysseyapi-tides-server.toml");
        // Previously registered client-only events have been removed
        DonationReminderConfig.validateVersion();

    }


    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            System.out.println("Wilderness Odyssey setup complete!");
        });
        LOGGER.warn("Mod Pack Version: {}", VERSION); // Logs as a warning
        LOGGER.warn("This message is for development purposes only."); // Logs as info
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
        AsyncTaskManager.initialize(AsyncThreadingConfig.values());
        globalChatManager.initialize(event.getServer(), event.getServer().getFile("config"));
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
        StructureInfoCommand.register(event.getDispatcher());
        FaqCommand.register(event.getDispatcher());
        DonateCommand.register(event.getDispatcher());
        WorldGenScanCommand.register(event.getDispatcher());
        StructurePlacementDebugCommand.register(event.getDispatcher());
        GlobalChatCommand.register(dispatcher);
        GlobalChatOptToggleCommand.register(dispatcher);
        TideInfoCommand.register(dispatcher);
    }

    /**
     * Inform players about opt-in global chat on login.
     *
     * @param event the login event
     */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        player.sendSystemMessage(Component.literal("[GlobalChat] Global chat is opt-in. Use /globalchatoptin to join or /globalchatoptout to leave."));
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
    }
    /**
     * On server stopping.
     *
     * @param event the event
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        globalChatManager.shutdown();
        long gameTime = event.getServer().overworld() != null ? event.getServer().overworld().getGameTime() : 0L;
        AsyncTaskManager.shutdown();
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        // Register structure placer or any late logic
        //NeoForge.EVENT_BUS.register(new WorldEvents());
        /// i think we don't need this anymore ^ but keep for now.
    }
    @SubscribeEvent
    public void onReload(AddReloadListenerEvent event) {
        event.addListener(new FaqReloadListener());
    }


    public void onConfigLoaded(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == AsyncThreadingConfig.CONFIG_SPEC) {
            AsyncTaskManager.initialize(AsyncThreadingConfig.values());
        }
        if (event.getConfig().getSpec() == StructureBlockConfig.CONFIG_SPEC) {
            StructureBlockSettings.reloadFromConfig();
        }
        if (event.getConfig().getSpec() == TideConfig.CONFIG_SPEC) {
            TideManager.reloadConfig();
        }
    }

    public void onConfigReloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == AsyncThreadingConfig.CONFIG_SPEC) {
            AsyncTaskManager.initialize(AsyncThreadingConfig.values());
        }
        if (event.getConfig().getSpec() == StructureBlockConfig.CONFIG_SPEC) {
            StructureBlockSettings.reloadFromConfig();
        }
        if (event.getConfig().getSpec() == TideConfig.CONFIG_SPEC) {
            TideManager.reloadConfig();
        }
    }
}
