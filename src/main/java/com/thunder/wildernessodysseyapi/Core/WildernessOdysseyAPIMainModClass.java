package com.thunder.wildernessodysseyapi.Core;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.ModPackPatches.BugFixes.InfiniteSourceHandler;
import com.thunder.wildernessodysseyapi.ErrorLog.UncaughtExceptionLogger;
import com.thunder.wildernessodysseyapi.ModPackPatches.FAQ.FaqCommand;
import com.thunder.wildernessodysseyapi.ModPackPatches.FAQ.FaqReloadListener;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.Features.ModFeatures;
import com.thunder.wildernessodysseyapi.MemUtils.MemCheckCommand;
import com.thunder.wildernessodysseyapi.MemUtils.MemoryUtils;
import com.thunder.wildernessodysseyapi.MobControl.EventHandler;
import com.thunder.wildernessodysseyapi.ModListTracker.commands.ModListDiffCommand;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.biome.ModBiomeModifiers;
import com.thunder.wildernessodysseyapi.ModPackPatches.ClientSaveHandler;
import com.thunder.wildernessodysseyapi.WorldGen.blocks.WorldSpawnBlock;
import com.thunder.wildernessodysseyapi.WorldGen.client.ClientSetup;
import com.thunder.wildernessodysseyapi.WorldGen.worldgen.configurable.StructureConfig;
import com.thunder.wildernessodysseyapi.command.StructureInfoCommand;
import com.thunder.wildernessodysseyapi.item.ModItems;
import com.thunder.wildernessodysseyapi.AntiCheat.BlacklistChecker;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.ModStructures;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.WordlEdit.WorldEditStructurePlacer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import java.util.HashMap;
import java.util.Map;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;
import static com.thunder.wildernessodysseyapi.Core.ModConstants.VERSION;

/**
 * The type Wilderness odyssey api main mod class.
 */
@Mod(ModConstants.MOD_ID)
public class WildernessOdysseyAPIMainModClass {

    public static int dynamicModCount = 0;

    private static AABB structureBoundingBox;

    private static final Map<CustomPacketPayload.Type<?>, NetworkMessage<?>> MESSAGES = new HashMap<>();

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
        modEventBus.addListener(this::addCreative);

        // Register global events
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(BlacklistChecker.class);
        NeoForge.EVENT_BUS.register(InfiniteSourceHandler.class);
        NeoForge.EVENT_BUS.register(EventHandler.class);
        NeoForge.EVENT_BUS.register(ClientSaveHandler.class);
        ModBiomeModifiers.BIOME_MODIFIERS.register(modEventBus);
        ModStructures.PLACED_FEATURES.register(modEventBus);
        ModFeatures.CONFIGURED_FEATURES.register(modEventBus);
        ModFeatures.PLACED_FEATURES.register(modEventBus);

        WorldSpawnBlock.register(modEventBus);
        ModItems.register(modEventBus);

       // todo remove  modEventBus.addListener(ModBiomes::register);
        // TerraBlender region
        //terrablender.addRegion(new ModRegion(ResourceLocation.tryBuild(ModConstants.MOD_ID, "meteor_region"), 1));
        container.registerConfig(ModConfig.Type.COMMON, StructureConfig.CONFIG_SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.registerClientEvents();
        }

    }


    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> System.out.println("Wilderness Odyssey setup complete!"));
        LOGGER.warn("Mod Pack Version: {}", VERSION); // Logs as a warning
        LOGGER.warn("This message is for development purposes only."); // Logs as info
        UncaughtExceptionLogger.init();
        dynamicModCount = ModList.get().getMods().size();
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(WorldSpawnBlock.WORLD_SPAWN_BLOCK.get());
        }
    }

    /**
     * On server starting.
     *
     * @param event the event
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event){

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
        MemCheckCommand.register(event.getDispatcher());
        StructureInfoCommand.register(event.getDispatcher());
        FaqCommand.register(event.getDispatcher());
    }

    /**
     * On world load.
     *
     * @param event the event
     */
    @SubscribeEvent
    public void onWorldLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        // Find a position in the Plains biome
        BlockPos pos = findPlainsBiomePosition(serverLevel);
        if (pos == null) return;

        // Place the BunkerStructure
        WorldEditStructurePlacer placer = new WorldEditStructurePlacer("wildernessodyssey", "schematics/my_structure.schem");
        if (placer.placeStructure(serverLevel, pos)) {
            structureBoundingBox = new AABB(
                    pos.getX() - 10, pos.getY() - 5, pos.getZ() - 10,
                    pos.getX() + 10, pos.getY() + 10, pos.getZ() + 10
            ); // Define a bounding box around the BunkerStructure
            // Mark as generated
        }
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


    /**
     * On server stopping.
     *
     * @param event the event
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {

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
        if (!event.hasTime()) return;

        if (server.getTickCount() % 600 == 0) {
                long usedMB = MemoryUtils.getUsedMemoryMB();
                long totalMB = MemoryUtils.getTotalMemoryMB();

                // Use the dynamic mod count
                int recommendedMB = MemoryUtils.calculateRecommendedRAM(usedMB, dynamicModCount);

            LOGGER.info("[ResourceManager] Memory usage: {}MB / {}MB. Recommended ~{}MB for {} loaded mods.", usedMB, totalMB, recommendedMB, dynamicModCount);
            }
        }
    @SubscribeEvent
    public void onReload(AddReloadListenerEvent event) {
        event.addListener(new FaqReloadListener());
    }
}
