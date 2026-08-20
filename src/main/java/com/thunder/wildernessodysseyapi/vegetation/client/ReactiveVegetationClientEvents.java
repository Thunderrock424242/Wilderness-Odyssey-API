package com.thunder.wildernessodysseyapi.vegetation.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.HashSet;
import java.util.Set;

/** Client lifecycle and gradual surface-section invalidation for vegetation tints. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class ReactiveVegetationClientEvents {

    private static final int MAXIMUM_DIRTY_CHUNKS_PER_TICK = 2;

    private ReactiveVegetationClientEvents() {
    }

    /** Promotes a safely retained early climate snapshot after chunk installation. */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel level
                && event.getChunk() instanceof LevelChunk chunk) {
            ClientVegetationClimateStore.onChunkLoaded(level, chunk.getPos().x, chunk.getPos().z);
        }
    }

    /** Rebuilds only surface-bearing sections and caps invalidation work per frame. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        for (long chunkKey : ClientVegetationClimateStore.drainDirty(
                level,
                MAXIMUM_DIRTY_CHUNKS_PER_TICK
        )) {
            int chunkX = ChunkPos.getX(chunkKey);
            int chunkZ = ChunkPos.getZ(chunkKey);
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null) {
                continue;
            }
            Set<Integer> surfaceSections = new HashSet<>();
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int surfaceY = chunk.getHeight(
                            Heightmap.Types.WORLD_SURFACE,
                            localX,
                            localZ
                    ) - 1;
                    surfaceSections.add(SectionPos.blockToSectionCoord(surfaceY));
                }
            }
            for (int sectionY : surfaceSections) {
                minecraft.levelRenderer.setSectionDirty(chunkX, sectionY, chunkZ);
            }
        }
    }

    /** Drops the immutable mirror as soon as a client chunk unloads. */
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level
                && event.getChunk() instanceof LevelChunk chunk) {
            ClientVegetationClimateStore.forget(level, chunk.getPos().x, chunk.getPos().z);
        }
    }

    /** Clears all climate mirrors during a client dimension unload. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ClientVegetationClimateStore.clear(level);
        }
    }
}
