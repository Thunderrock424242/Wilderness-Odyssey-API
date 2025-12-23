package com.thunder.wildernessodysseyapi.capabilities;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AttachCapabilitiesEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

/**
 * Hooks chunk events to manage the chunk capability lifecycle.
 */
@EventBusSubscriber(modid = MOD_ID)
public final class ChunkCapabilityHandler {

    private ChunkCapabilityHandler() {
    }

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<LevelChunk> event) {
        LevelChunk chunk = event.getObject();
        ChunkDataCapabilityProvider provider = new ChunkDataCapabilityProvider(chunk);
        event.addCapability(ChunkDataCapabilityProvider.IDENTIFIER, provider);
        event.addListener(provider::invalidate);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        chunk.getCapability(ChunkDataCapabilityProvider.CHUNK_DATA_CAPABILITY).ifPresent(data -> {
            // Clear dirty on load to avoid unnecessary saves after hydration.
            data.clearDirty();
        });
    }

    @SubscribeEvent
    public static void onChunkSave(ChunkEvent.Save event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        chunk.getCapability(ChunkDataCapabilityProvider.CHUNK_DATA_CAPABILITY).ifPresent(data -> {
            if (!data.isDirty()) {
                return;
            }
            // Reset the flag post-save so we only write when necessary.
            data.clearDirty();
        });
    }
}
