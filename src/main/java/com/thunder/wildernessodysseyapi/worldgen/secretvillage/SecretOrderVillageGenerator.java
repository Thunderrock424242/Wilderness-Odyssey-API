package com.thunder.wildernessodysseyapi.worldgen.secretvillage;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

/**
 * Triggers Secret Order village placement for newly loaded server chunks.
 *
 * <p>The explicit server-level check keeps world generation off the client.
 * The placer owns biome, rarity, and structure-template decisions.</p>
 */
@EventBusSubscriber(modid = MOD_ID)
public final class SecretOrderVillageGenerator {

    private SecretOrderVillageGenerator() {
    }

    /**
     * Attempts village placement when NeoForge loads a full server chunk.
     *
     * @param event the chunk load event fired on both logical sides
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }

        SecretOrderVillagePlacer.tryPlace(level, chunk);
    }
}
