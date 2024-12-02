package com.thunder.wildernessodysseyapi;

import com.thunder.wildernessodysseyapi.config.ToolDamageConfig;
import com.thunder.wildernessodysseyapi.security.BlacklistChecker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
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

        // Register the common config
        container.registerConfig(ModConfig.Type.COMMON, ToolDamageConfig.CONFIG_SPEC);

    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // General setup logic
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // Creative tab logic
    }

    @SubscribeEvent
    public void onServerStarting(@NotNull ServerStartingEvent event) {

        LOGGER.info("Server starting: commands registered");
    }
    // Handle config loading
    private void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ToolDamageConfig.CONFIG_SPEC) {
            ToolDamageConfig.CONFIG.loadConfig();
        }
    }

    // Handle config reloading
    private void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ToolDamageConfig.CONFIG_SPEC) {
            ToolDamageConfig.CONFIG.loadConfig();
        }
    }
}
