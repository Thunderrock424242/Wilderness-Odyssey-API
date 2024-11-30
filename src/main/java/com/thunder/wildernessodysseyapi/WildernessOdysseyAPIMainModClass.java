package com.thunder.wildernessodysseyapi;

import com.thunder.wildernessodysseyapi.command.AdminCommand;
import com.thunder.wildernessodysseyapi.command.ClearItemsCommand;
import com.thunder.wildernessodysseyapi.command.DimensionTPCommand;
import com.thunder.wildernessodysseyapi.config.ClientConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.NotNull;
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

    private static final Map<CustomPacketPayload.Type<?>, NetworkMessage<?>> MESSAGES = new HashMap<>();
    public static final Logger LOGGER = LogUtils.getLogger();

    public WildernessOdysseyAPIMainModClass() {

    }

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
    }
    public static void queueServerWork(int delay, Runnable task) {
        // Schedule the task to be run after the specified delay
        Executors.newSingleThreadScheduledExecutor().schedule(task, delay, TimeUnit.MILLISECONDS);
    }


    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Register server-side crash handler
        setServerCrashHandler();

        // Register client-side crash handler (only executed on client side)
        setClientCrashHandler();

    }

    // Server-side crash handler setup using NeoForge event bus
    private void setServerCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            LOGGER.error("A server-side crash occurred on thread: " + thread.getName());
            LOGGER.error("Analyzing server crash report...");

            analyzeCrash(throwable);
        });
    }

    // Client-side crash handler setup (only used in single-player/client context)
    @OnlyIn(Dist.CLIENT)
    private void setClientCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler(new WildernessOdysseyAPIMainModClass.CrashHandler());
    }

    // A reusable method to analyze crash causes
    private void analyzeCrash(@NotNull Throwable throwable) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;

        LOGGER.error("Crash caused by: " + cause.getMessage());
        for (StackTraceElement element : cause.getStackTrace()) {
            String className = element.getClassName();

            // Check for possible mod involvement (customize with your package names)
            if (className.contains("com.mymod") || className.contains("net.modpackage")) {
                LOGGER.error("Potential Mod Culprit: " + className);
            }
        }

        LOGGER.error("Crash analysis complete. Please check logs for details.");
    }

    // Inner static class to handle client-side crashes
    public static class CrashHandler implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(@NotNull Thread thread, Throwable throwable) {
            LOGGER.error("A crash occurred on thread: " + thread.getName());
            LOGGER.error("Analyzing crash report...");

            // Reuse crash analysis method for consistency
            new WildernessOdysseyAPIMainModClass().analyzeCrash(throwable);
        }
    }


    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {

    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        ClearItemsCommand.register(event.getServer().getCommands().getDispatcher());
        AdminCommand.register(event.getServer().getCommands().getDispatcher());
        DimensionTPCommand.register(event.getServer().getCommands().getDispatcher());
        LOGGER.info("Ban command registered");


    }
}
