package com.thunder.wildernessodysseyapi.temporalrift;

import com.thunder.wildernessodysseyapi.temporalrift.command.TemporalRiftCommand;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.EventPriority;
import com.thunder.wildernessodysseyapi.temporalrift.echo.EchoSyncManager;
import com.thunder.wildernessodysseyapi.temporalrift.echo.EchoStabilityManager;
import com.thunder.wildernessodysseyapi.temporalrift.echo.EchoDebugCommand;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public final class TemporalRiftEventHandler {
    private TemporalRiftEventHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TemporalRiftManager.tick(event.getServer());
        EchoBuildEchoManager.tick(event.getServer());
        EchoSyncManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        TemporalRiftCommand.register(event.getDispatcher());
        EchoDebugCommand.register(event.getDispatcher());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled()) return;
        if (event.getLevel() instanceof ServerLevel echoLevel
                && echoLevel.dimension().equals(TemporalRiftDimensions.THE_ECHO_KEY)
                && event.getEntity() instanceof ServerPlayer) {
            markPlayerEdited(echoLevel, event.getPos());
        }
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(TemporalRiftDimensions.THE_BEFORE_KEY)
                && event.getEntity() instanceof ServerPlayer player) {
            TemporalEchoManager.recordPlayerPlacedBlock(level, event.getPos(), event.getPlacedBlock(), player);
        } else if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(Level.OVERWORLD)
                && event.getEntity() instanceof ServerPlayer player) {
            EchoBuildEchoManager.recordOverworldPlacedBlock(level, event.getPos(), event.getPlacedBlock(), player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        if (event.getLevel() instanceof ServerLevel echoLevel
                && echoLevel.dimension().equals(TemporalRiftDimensions.THE_ECHO_KEY)
                && event.getPlayer() instanceof ServerPlayer) {
            markPlayerEdited(echoLevel, event.getPos());
        }
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(TemporalRiftDimensions.THE_BEFORE_KEY)
                && event.getPlayer() instanceof ServerPlayer player) {
            TemporalEchoManager.recordPlayerBrokenBlock(level, event.getPos(), event.getState(), player);
        } else if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(Level.OVERWORLD)
                && event.getPlayer() instanceof ServerPlayer player) {
            EchoBuildEchoManager.recordOverworldBrokenBlock(level, event.getPos(), event.getState(), player);
        }
    }

    private static void markPlayerEdited(ServerLevel level, net.minecraft.core.BlockPos position) {
        var chunk = level.getChunkAt(position);
        chunk.getData(com.thunder.wildernessodysseyapi.core.ModAttachments.ECHO_CHUNK).markPlayerEdited(position);
    }

    /** Fresh local summaries after travel; no cached state crosses a dimension transition. */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) EchoSyncManager.clearPlayer(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) EchoSyncManager.clearPlayer(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) EchoSyncManager.clearPlayer(player);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(TemporalRiftDimensions.THE_ECHO_KEY)) {
            EchoSyncManager.clear(level.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        EchoSyncManager.clear(event.getServer());
    }
}
