package com.thunder.wildernessodysseyapi.Core;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.ModPackPatches.BugFixes.InfiniteSourceHandler;
import com.thunder.wildernessodysseyapi.ErrorLog.UncaughtExceptionLogger;
import com.thunder.wildernessodysseyapi.ModPackPatches.FAQ.FaqCommand;
import com.thunder.wildernessodysseyapi.ModPackPatches.FAQ.FaqReloadListener;
import com.thunder.wildernessodysseyapi.MemUtils.MemCheckCommand;
import com.thunder.wildernessodysseyapi.MemUtils.MemoryUtils;
import com.thunder.wildernessodysseyapi.ModListTracker.commands.ModListDiffCommand;
import com.thunder.wildernessodysseyapi.ModListTracker.commands.ModListVersionCommand;
import com.thunder.wildernessodysseyapi.WorldGen.blocks.CryoTubeBlock;
import com.thunder.wildernessodysseyapi.WorldGen.blocks.TerrainReplacerBlock;
import com.thunder.wildernessodysseyapi.WorldGen.worldgen.configurable.StructureConfig;
import com.thunder.wildernessodysseyapi.command.StructureInfoCommand;
import com.thunder.wildernessodysseyapi.donations.command.DonateCommand;
import com.thunder.wildernessodysseyapi.doorlock.DoorLockEvents;
import com.thunder.wildernessodysseyapi.command.DoorLockCommand;
import com.thunder.wildernessodysseyapi.item.ModCreativeTabs;
import com.thunder.wildernessodysseyapi.item.ModItems;
import com.thunder.wildernessodysseyapi.AntiCheat.BlacklistChecker;
import com.thunder.wildernessodysseyapi.donations.config.DonationReminderConfig;
import com.thunder.wildernessodysseyapi.ModConflictChecker.Util.LoggerUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import com.thunder.wildernessodysseyapi.WorldGen.worldgen.structures.MeteorStructureSpawner;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
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
        ModCreativeTabs.register(modEventBus);

        // Register global events
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(BlacklistChecker.class);
        NeoForge.EVENT_BUS.register(InfiniteSourceHandler.class);
        NeoForge.EVENT_BUS.register(DoorLockEvents.class);

        CryoTubeBlock.register(modEventBus);
        TerrainReplacerBlock.register(modEventBus);
        ModItems.register(modEventBus);

        container.registerConfig(ModConfig.Type.COMMON, StructureConfig.CONFIG_SPEC);
        // Previously registered client-only events have been removed
        container.registerConfig(ModConfig.Type.CLIENT, DonationReminderConfig.CONFIG_SPEC);
        DonationReminderConfig.validateVersion();

    }


    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            System.out.println("Wilderness Odyssey setup complete!");
            // WorldEdit performs internal initialization asynchronously. Referencing
            // BlockTypes here can trigger its static initialiser before WorldEdit is
            // ready, causing a NoClassDefFoundError and preventing WorldEdit from
            // loading later on. Simply log a message and allow WorldEdit to finish
            // bootstrapping naturally.
            if (ModList.get().isLoaded("worldedit")) {
                LOGGER.debug("WorldEdit detected; deferring BlockTypes usage until after startup");
            }
        });
        LOGGER.warn("Mod Pack Version: {}", VERSION); // Logs as a warning
        LOGGER.warn("This message is for development purposes only."); // Logs as info
        UncaughtExceptionLogger.init();
        dynamicModCount = ModList.get().getMods().size();
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
        ModListVersionCommand.register(dispatcher);
        MemCheckCommand.register(event.getDispatcher());
        StructureInfoCommand.register(event.getDispatcher());
        FaqCommand.register(event.getDispatcher());
        DonateCommand.register(event.getDispatcher());
        DoorLockCommand.register(event.getDispatcher());
    }

    /**
     * On world load.
     *
     * @param event the event
     */
    @SubscribeEvent
    public void onWorldLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        MeteorStructureSpawner.tryPlace(serverLevel);
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
        event.addListener(com.thunder.wildernessodysseyapi.WorldGen.schematic.SchematicManager.INSTANCE);
    }
}
