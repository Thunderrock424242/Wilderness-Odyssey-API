package com.thunder.wildernessodysseyapi.worldgen.SecretOrderVillage;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
/**
 * Listens for chunk loads to spawn the secret village structure.
 */
public class SecretOrderVillageGenerator {
    /**
     * Attempts to place the village when a chunk loads.
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        SecretOrderVillagePlacer.tryPlace(level, chunk);
    }
}
