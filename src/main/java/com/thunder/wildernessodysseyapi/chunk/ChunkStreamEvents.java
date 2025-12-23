package com.thunder.wildernessodysseyapi.chunk;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Bridges vanilla chunk lifecycle events to the chunk streaming pipeline.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class ChunkStreamEvents {
    private ChunkStreamEvents() {
    }

    @SubscribeEvent
    public static void onChunkSave(ChunkDataEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        ChunkStreamManager.scheduleSave(event.getChunk().getPos(), event.getData(), serverLevel.getGameTime());
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        ChunkStreamManager.flushChunk(event.getChunk().getPos());
    }

    @SubscribeEvent
    public static void onWorldSave(LevelEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;

        ChunkStreamManager.flushAll();
    }
}
