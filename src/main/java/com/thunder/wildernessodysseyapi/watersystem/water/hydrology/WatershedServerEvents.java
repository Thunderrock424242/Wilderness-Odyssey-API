package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Connects watershed initialization and cleanup to loaded-world lifecycle events.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class WatershedServerEvents {

    private WatershedServerEvents() {
    }

    /** Initializes compact terrain metadata after a chunk is available as a LevelChunk. */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level
                && event.getChunk() instanceof LevelChunk chunk) {
            WatershedSimulationManager.onChunkLoad(level, chunk);
        }
    }

    /** Releases the unloading dimension's time-sliced queue and timing state. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            WatershedSimulationManager.clearLevel(level);
        }
    }
}
