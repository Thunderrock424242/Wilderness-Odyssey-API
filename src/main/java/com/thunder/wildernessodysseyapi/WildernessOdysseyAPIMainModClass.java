package com.thunder.wildernessodysseyapi;

import com.thunder.wildernessodysseyapi.command.AdminCommand;
import com.thunder.wildernessodysseyapi.command.BanCommand;
import com.thunder.wildernessodysseyapi.command.ClearItemsCommand;
import com.thunder.wildernessodysseyapi.command.DimensionTPCommand;
import com.thunder.wildernessodysseyapi.config.ConfigGenerator;
import com.thunder.wildernessodysseyapi.config.ToolDamageConfig;
import com.thunder.wildernessodysseyapi.config.ClientConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;


import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.thunder.wildernessodysseyapi.WildernessOdysseyAPIMainModClass.MOD_ID;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(MOD_ID)
public class WildernessOdysseyAPIMainModClass
{
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "wildernessodysseyapii";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<CustomPacketPayload.Type<?>, NetworkMessage<?>> MESSAGES = new HashMap<>();

    private record NetworkMessage<T extends CustomPacketPayload>(StreamCodec<? extends FriendlyByteBuf, T> reader, IPayloadHandler<T> handler) {
    }
    private static boolean networkingRegistered = false;
    public static <T extends CustomPacketPayload> void addNetworkMessage(CustomPacketPayload.Type<T> id, StreamCodec<? extends FriendlyByteBuf, T> reader, IPayloadHandler<T> handler) {
        if (networkingRegistered)
            throw new IllegalStateException("Cannot register new network messages after networking has been registered");
        MESSAGES.put(id, new NetworkMessage<>(reader, handler));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerNetworking(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MOD_ID);
        MESSAGES.forEach((id, networkMessage) -> registrar.playBidirectional(id, ((NetworkMessage) networkMessage).reader(), ((NetworkMessage) networkMessage).handler()));
        networkingRegistered = true;
    }


    public static boolean ENABLE_OUTLINE = true; // Default is false, meant to be used in dev environment.

    // Hardcoded Server Whitelist - Only these servers can use the anti-cheat feature
    public static final Set<String> SERVER_WHITELIST = Set.of(
            "server-id-1",
            "server-id-2",
            "server-id-3"
    );

    /**
     * The constant antiCheatEnabled.
     */
// Configuration flags
    public static boolean antiCheatEnabled;
    /**
     * The constant globalLoggingEnabled.
     */
    public static boolean globalLoggingEnabled;

    // Scheduled Executor for periodic checks
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public WildernessOdysseyAPIMainModClass(IEventBus modEventBus, ModContainer container)
    {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);


        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExampleMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        //Register the config
        container.registerConfig(ModConfig.Type.COMMON, ClientConfig.CONFIG_SPEC);
        container.registerConfig(ModConfig.Type.COMMON, (IConfigSpec) ConfigGenerator.COMMON_CONFIG);
        container.registerConfig(ModConfig.Type.COMMON, (IConfigSpec) ToolDamageConfig.CONFIG);


        // If terms are not agreed to, terminate server startup
        if (!ConfigGenerator.AGREE_TO_TERMS) {
            LOGGER.error("You must agree to the terms outlined in the README.md file by setting 'agreeToTerms' to true in the configuration file.");
            throw new RuntimeException("Server cannot start without agreement to the mod's terms and conditions.");

    }
        // Enable anti-cheat only if the server is whitelisted
        String currentServerId = "server-unique-id";  // Replace with logic to fetch the current server's unique ID
        antiCheatEnabled = SERVER_WHITELIST.contains(currentServerId);

        // Generate README file during initialization
        READMEGenerator.generateReadme();
        // Start the periodic sync with GitHub to update banned players
        startBanSyncTask();

        LOGGER.info("Wilderness Oddessy Anti-Cheat Mod Initialized. Anti-cheat enabled: {}", antiCheatEnabled);
    }
    public static void queueServerWork(int delay, Runnable task) {
        // Schedule the task to be run after the specified delay
        Executors.newSingleThreadScheduledExecutor().schedule(task, delay, TimeUnit.MILLISECONDS);
    }


    private void commonSetup(final FMLCommonSetupEvent event)
    {

    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {

    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        BanCommand.register(event.getServer().getCommands().getDispatcher());
        ClearItemsCommand.register(event.getServer().getCommands().getDispatcher());
        AdminCommand.register(event.getServer().getCommands().getDispatcher());
        DimensionTPCommand.register(event.getServer().getCommands().getDispatcher());
        LOGGER.info("Ban command registered");


    }

    private void loadConfig() {
        // Load settings from configuration
        globalLoggingEnabled = ConfigGenerator.GLOBAL_LOGGING_ENABLED;
    }

    private void startBanSyncTask() {
        // Schedule periodic sync with GitHub to update the ban list every 10 minutes
        scheduler.scheduleAtFixedRate(() -> {
            try {
                BanManager.syncBanListFromGitHub();
                LOGGER.info("Ban list synced with GitHub");
            } catch (Exception e) {
                LOGGER.error("Failed to sync ban list with GitHub", e);
            }
        }, 0, 10, TimeUnit.MINUTES);
    }

    /**
     * Is global logging enabled boolean.
     *
     * @return the boolean
     */
    public static boolean isGlobalLoggingEnabled() {
        return globalLoggingEnabled;
    }
}
