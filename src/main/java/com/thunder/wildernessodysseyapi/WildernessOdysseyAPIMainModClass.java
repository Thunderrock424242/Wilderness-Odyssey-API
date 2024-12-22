package com.thunder.wildernessodysseyapi;

import com.thunder.wildernessodysseyapi.GlobalChat.ChatClient;
import com.thunder.wildernessodysseyapi.GlobalChat.gui.Screen.CustomChatScreen;
import com.thunder.wildernessodysseyapi.block.WorldSpawnBlock;
import com.thunder.wildernessodysseyapi.item.ModItems;
import com.thunder.wildernessodysseyapi.AntiCheat.BlacklistChecker;
import com.thunder.wildernessodysseyapi.structure.ModStructureHandler;
import com.thunder.wildernessodysseyapi.structure.StructureGenerationData;
import com.thunder.wildernessodysseyapi.structure.WorldEditStructurePlacer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
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

    private static AABB structureBoundingBox;
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
        NeoForge.EVENT_BUS.register(new ModStructureHandler());

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

    }

    @SubscribeEvent
    public void onWorldLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        // Check if the structure has already been generated
        StructureGenerationData data = StructureGenerationData.get(serverLevel);
        if (data.isStructureGenerated()) return;

        // Find a position in the Plains biome
        BlockPos pos = findPlainsBiomePosition(serverLevel);
        if (pos == null) return;

        // Place the structure
        WorldEditStructurePlacer placer = new WorldEditStructurePlacer("wildernessodyssey", "schematics/my_structure.schem");
        if (placer.placeStructure(serverLevel, pos)) {
            structureBoundingBox = new AABB(
                    pos.getX() - 10, pos.getY() - 5, pos.getZ() - 10,
                    pos.getX() + 10, pos.getY() + 10, pos.getZ() + 10
            ); // Define a bounding box around the structure
            data.setStructureGenerated(true); // Mark as generated
        }
    }

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

    private BlockPos findPlainsBiomePosition(ServerLevel serverLevel) {
        for (int x = -1000; x < 1000; x += 16) {
            for (int z = -1000; z < 1000; z += 16) {
                BlockPos pos = new BlockPos(x, serverLevel.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
                if (serverLevel.getBiome(pos).is(Biomes.PLAINS)) {
                    return pos;
                }
            }
        }
        return null; // No Plains biome found in the search range
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
