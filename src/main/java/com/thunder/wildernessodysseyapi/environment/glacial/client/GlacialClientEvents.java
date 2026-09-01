package com.thunder.wildernessodysseyapi.environment.glacial.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.environment.glacial.GlacialBiomeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.QuartPos;
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

/** Client chunk lifecycle and capped render-section invalidation for glacial tints. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class GlacialClientEvents {

    private static final int MAXIMUM_DIRTY_CHUNKS_PER_TICK = 2;

    private GlacialClientEvents() {
    }

    /** Retains only the key of a loaded chunk whose surface includes a glacial biome. */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel level
                && event.getChunk() instanceof LevelChunk chunk
                && isGlacialChunk(level, chunk)) {
            ClientGlacialState.track(level, chunk.getPos().x, chunk.getPos().z);
        }
    }

    /** Rebuilds at most two surface-bearing chunk sections per client tick. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        for (int count = 0; count < MAXIMUM_DIRTY_CHUNKS_PER_TICK; count++) {
            Long key = ClientGlacialState.pollDirty(level);
            if (key == null) {
                break;
            }
            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null) {
                continue;
            }
            Set<Integer> sections = new HashSet<>();
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    sections.add(SectionPos.blockToSectionCoord(
                            chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1));
                }
            }
            for (int sectionY : sections) {
                minecraft.levelRenderer.setSectionDirty(chunkX, sectionY, chunkZ);
            }
        }
    }

    /** Drops a client chunk from the future invalidation set. */
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level && event.getChunk() instanceof LevelChunk chunk) {
            ClientGlacialState.forget(level, chunk.getPos().x, chunk.getPos().z);
        }
    }

    /** Clears the client mirror during dimension changes and disconnects. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ClientGlacialState.clear(level);
        }
    }

    private static boolean isGlacialChunk(ClientLevel level, LevelChunk chunk) {
        int[][] samples = {{2, 2}, {8, 8}, {13, 2}, {2, 13}, {13, 13}};
        for (int[] sample : samples) {
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, sample[0], sample[1]) - 1;
            if (GlacialBiomeManager.isGlacial(chunk.getNoiseBiome(
                    QuartPos.fromBlock(chunk.getPos().getMinBlockX() + sample[0]),
                    QuartPos.fromBlock(Math.max(level.getMinBuildHeight(), y)),
                    QuartPos.fromBlock(chunk.getPos().getMinBlockZ() + sample[1])))) {
                return true;
            }
        }
        return false;
    }
}
