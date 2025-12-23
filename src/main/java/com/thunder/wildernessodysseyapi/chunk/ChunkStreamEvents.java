package com.thunder.wildernessodysseyapi.chunk;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
    public static void onChunkSave(ChunkEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        ChunkStreamManager.scheduleSave(chunk.getPos(), chunk.getPersistentData(), serverLevel.getGameTime());
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        ChunkPos pos = chunk.getPos();
        ChunkStreamManager.flushChunk(pos);
    }

    @SubscribeEvent
    public static void onWorldSave(LevelEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;

        ChunkStreamManager.flushAll();
    }
}
