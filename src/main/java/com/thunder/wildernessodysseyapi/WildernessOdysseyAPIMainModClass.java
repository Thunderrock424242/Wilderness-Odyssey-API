package com.thunder.wildernessodysseyapi;

import com.thunder.wildernessodysseyapi.command.DimensionTPCommand;
import com.thunder.wildernessodysseyapi.security.BlacklistChecker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Mod(WildernessOdysseyAPIMainModClass.MOD_ID)
public class WildernessOdysseyAPIMainModClass {

    public static final String MOD_ID = "wildernessodysseyapi";
    private static final Map<CustomPacketPayload.Type<?>, NetworkMessage<?>> MESSAGES = new HashMap<>();
    public static final Logger LOGGER = LogUtils.getLogger();

    private record NetworkMessage<T extends CustomPacketPayload>(StreamCodec<? extends FriendlyByteBuf, T> reader, IPayloadHandler<T> handler) {}

    public WildernessOdysseyAPIMainModClass(IEventBus modEventBus, ModContainer container) {
        // Register mod setup and creative tabs
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);

        // Register global events and BlacklistChecker
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new BlacklistChecker()); // Register BlacklistChecker
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // General setup logic
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // Creative tab logic
    }

    @SubscribeEvent
    public void onServerStarting(@NotNull ServerStartingEvent event) {
        DimensionTPCommand.register(event.getServer().getCommands().getDispatcher());
        LOGGER.info("Server starting: commands registered");
    }

    public static void queueServerWork(int delay, Runnable task) {
        Executors.newSingleThreadScheduledExecutor().schedule(task, delay, TimeUnit.MILLISECONDS);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerNetworking(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MOD_ID);
        MESSAGES.forEach((id, networkMessage) -> registrar.playBidirectional(
                id, ((NetworkMessage) networkMessage).reader(), ((NetworkMessage) networkMessage).handler()));
        boolean networkingRegistered = true;
    }
}
