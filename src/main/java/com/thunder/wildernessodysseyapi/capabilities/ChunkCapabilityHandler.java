package com.thunder.wildernessodysseyapi.capabilities;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWaterMigrationQueue;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;

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
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        // Clear dirty on load to avoid unnecessary saves after hydration.
        ChunkDataCapability chunkData = chunk.getData(ModAttachments.CHUNK_DATA);
        chunkData.clearDirty();
        boolean waterEnabled = WildernessWaterRules.isEnabled(level);
        chunk.getExistingData(ModAttachments.WATER_VOLUME).ifPresent(volume -> {
            volume.clearDirty();
            if (!waterEnabled) {
                return;
            }
            for (WaterVolumeChunk.CellEntry entry : volume.snapshot()) {
                if (!entry.cell().imported()) {
                    CanonicalWater.schedule(level, WaterVolumeChunk.unpack(
                            chunk.getPos().x,
                            chunk.getPos().z,
                            entry.packedPosition()
                    ));
                }
            }
        });
        if (!waterEnabled) {
            return;
        }
        // Chunk load may run on Minecraft's streaming path, so never rewrite
        // water blocks here. Loaded chunks are priority-queued once players
        // exist, then the server tick queue performs bounded finalization.
        if (!chunkData.isWaterFinalized()) {
            CanonicalWaterMigrationQueue.queueLoadedChunkForFinalization(level, chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        if (!WildernessWaterRules.isEnabled(event.getLevel())) {
            return;
        }
        // A watched chunk is the first safe player-visible boundary: worldgen is
        // done, the chunk is loaded, and a player is about to see it. Promote
        // it instead of rewriting in the watch callback so chunk sending stays
        // responsive when many ocean chunks stream at once.
        CanonicalWaterMigrationQueue.queueVisibleChunkForFinalization(event.getLevel(), event.getChunk());
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
        chunk.getExistingData(ModAttachments.WATER_VOLUME).ifPresent(volume -> {
            if (volume.isDirty()) {
                volume.clearDirty();
            }
        });
    }
}
