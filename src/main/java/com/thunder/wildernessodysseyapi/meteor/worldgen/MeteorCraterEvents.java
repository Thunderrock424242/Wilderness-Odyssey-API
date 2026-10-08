package com.thunder.wildernessodysseyapi.meteor.worldgen;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.environment.api.EnvironmentDimensionProfile;
import com.thunder.wildernessodysseyapi.meteor.api.MeteorSiteServices;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Hands immutable structure metadata to the server's meteor index after chunk promotion. */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class MeteorCraterEvents {

    private static final int SITES_PER_LEVEL_PER_TICK = 8;
    // Entries are coalesced by loaded start chunk and removed on unload. Their
    // lifetime and cardinality are bounded by the server's loaded crater chunks.
    private static final Map<ServerLevel, LinkedHashMap<Long, MeteorCraterPlan>> PENDING = new HashMap<>();

    private MeteorCraterEvents() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getChunk() instanceof LevelChunk chunk)
                || !EnvironmentDimensionProfile.forDimension(level.dimension()).naturalMeteors()) {
            return;
        }
        for (var start : chunk.getAllStarts().values()) {
            if (!start.isValid() || !(start.getStructure() instanceof MeteorCraterStructure)) {
                continue;
            }
            for (var piece : start.getPieces()) {
                if (piece instanceof MeteorCraterPiece crater) {
                    MeteorCraterPlan plan = crater.plan();
                    long key = new ChunkPos(plan.center()).toLong();
                    if (key == chunk.getPos().toLong()) {
                        level.getServer().execute(() -> PENDING.computeIfAbsent(level,
                                ignored -> new LinkedHashMap<>()).put(key, plan));
                    }
                }
            }
        }
    }

    /** Defers SavedData access beyond the pre-FULL chunk load notification. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (var levelEntry : PENDING.entrySet()) {
            ServerLevel level = levelEntry.getKey();
            LinkedHashMap<Long, MeteorCraterPlan> pending = levelEntry.getValue();
            int work = Math.min(SITES_PER_LEVEL_PER_TICK, pending.size());
            for (int i = 0; i < work; i++) {
                var entry = pending.pollFirstEntry();
                if (level.getChunkSource().getChunkNow(ChunkPos.getX(entry.getKey()),
                        ChunkPos.getZ(entry.getKey())) == null) {
                    pending.put(entry.getKey(), entry.getValue());
                    continue;
                }
                MeteorCraterPlan plan = entry.getValue();
                MeteorSiteServices.recordGeneratedSite(level, plan.center(), plan.radius());
            }
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Map<Long, MeteorCraterPlan> pending = PENDING.get(level);
            if (pending != null) {
                pending.remove(event.getChunk().getPos().toLong());
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PENDING.remove(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PENDING.clear();
    }
}
