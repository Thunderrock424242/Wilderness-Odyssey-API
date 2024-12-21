package com.thunder.wildernessodysseyapi;

import com.thunder.wildernessodysseyapi.GlobalChat.ChatClient;
import com.thunder.wildernessodysseyapi.GlobalChat.gui.Screen.CustomChatScreen;
import com.thunder.wildernessodysseyapi.biome.BiomeModifiers;
import com.thunder.wildernessodysseyapi.block.WorldSpawnBlock;
import com.thunder.wildernessodysseyapi.item.ModItems;
import com.thunder.wildernessodysseyapi.AntiCheat.BlacklistChecker;
import com.thunder.wildernessodysseyapi.structure.ModStructures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
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
        NeoForge.EVENT_BUS.register(new BlacklistChecker());// Register BlacklistChecker

        WorldSpawnBlock.register(modEventBus);
        ModItems.register(modEventBus);


    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // General setup logic
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(WorldSpawnBlock.WORLD_SPAWN_BLOCK.get());
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event){
        event.enqueueWork(() -> {
            RegistryObject<ConfiguredStructureFeature<?, ?>> structure = ModStructures.CONFIGURED_CUSTOM_STRUCTURE;
            BiomeModifier biomeModifier = new BiomeModifiers(structure.getHolder().orElseThrow());
            ForgeRegistries.BIOME_MODIFIER_SERIALIZERS.register(
                    new ResourceLocation("wildernessodyssey", "custom_structure_modifier"),
                    biomeModifier
            );
        });
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        ChatClient.startClient("209.192.200.84", 25582); // Replace it with actual IP and port
    }
    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Screen currentScreen = mc.screen;

        // Check if the current screen is the default ChatScreen
        if (currentScreen instanceof ChatScreen && !(currentScreen instanceof CustomChatScreen)) {
            mc.setScreen(new CustomChatScreen());
        }
    }

}
