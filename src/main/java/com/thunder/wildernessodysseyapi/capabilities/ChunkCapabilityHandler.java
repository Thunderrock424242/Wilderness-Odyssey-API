package com.thunder.wildernessodysseyapi.capabilities;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

/**
 * Hooks chunk events to manage the chunk capability lifecycle.
 */
@EventBusSubscriber(modid = MOD_ID)
public final class ChunkCapabilityHandler {

    private ChunkCapabilityHandler() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        // Clear dirty on load to avoid unnecessary saves after hydration.
        chunk.getExistingData(ModAttachments.CHUNK_DATA).ifPresent(ChunkDataCapability::clearDirty);
    }

    @SubscribeEvent
    public static void onChunkSave(ChunkDataEvent.Save event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        chunk.getExistingData(ModAttachments.CHUNK_DATA).ifPresent(data -> {
            if (data.isDirty()) {
                // Reset the flag post-save so we only write when necessary.
                data.clearDirty();
            }
        });
    }
}
