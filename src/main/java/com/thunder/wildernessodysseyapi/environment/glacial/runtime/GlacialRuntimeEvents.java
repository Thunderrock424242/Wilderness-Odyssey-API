package com.thunder.wildernessodysseyapi.environment.glacial.runtime;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.environment.glacial.GlacialSeasonManager;
import com.thunder.wildernessodysseyapi.environment.glacial.network.GlacialSeasonSyncService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** NeoForge lifecycle bridge for glacial block updates and season snapshots. */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class GlacialRuntimeEvents {

    private GlacialRuntimeEvents() {
    }

    /** Initializes presentation even when this dimension's periodic send is not due. */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GlacialSeasonSyncService.publishToPlayer(player);
        }
    }

    /** Initializes the new client level after a dimension transition. */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GlacialSeasonSyncService.publishToPlayer(player);
        }
    }

    /** Restores the client mirror when respawning replaces the player's client level. */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GlacialSeasonSyncService.publishToPlayer(player);
        }
    }

    /** Tracks normally promoted server chunks without retaining unloaded regions. */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            GlacialFreezeManager.onChunkLoad(level, chunk);
        }
    }

    /** Removes a chunk from seasonal work before it unloads. */
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            GlacialFreezeManager.onChunkUnload(level, chunk);
        }
    }

    /** Runs one bounded pass and publishes any changed season presentation. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!event.hasTime()) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            GlacialFreezeManager.tickLevel(level);
            GlacialSeasonSyncService.tickLevel(level);
        }
    }

    /** Releases one dimension's process-local state. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            GlacialFreezeManager.clearLevel(level);
            GlacialSeasonManager.clearLevel(level);
            GlacialSeasonSyncService.clearLevel(level);
        }
    }

    /** Clears all process-local state after worlds have saved. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        GlacialFreezeManager.clearAll();
        GlacialSeasonManager.clearAll();
        GlacialSeasonSyncService.clearAll();
    }
}
