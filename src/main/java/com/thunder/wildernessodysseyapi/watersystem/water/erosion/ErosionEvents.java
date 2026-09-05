package com.thunder.wildernessodysseyapi.watersystem.water.erosion;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Enrolls new natural terrain and conservatively revokes eligibility around construction. */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class ErosionEvents {
    private ErosionEvents() { }

    /** Existing saves are deliberately not retroactively classified as natural. */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.isNewChunk() && event.getLevel() instanceof ServerLevel level
                && event.getChunk() instanceof LevelChunk chunk && ErosionConfig.enabled()) {
            ErosionSavedData.get(level).enroll(chunk.getPos().toLong());
        }
    }

    /** Placement includes automation events; every part of a multi-place is protected. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            protectAround(level, event.getPos());
            if (event instanceof BlockEvent.EntityMultiPlaceEvent multiple) {
                multiple.getReplacedBlockSnapshots().forEach(snapshot -> protectAround(level, snapshot.getPos()));
            }
        }
    }

    /** Player excavation also indicates occupied terrain whose supports must remain safe. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) protectAround(level, event.getPos());
    }

    /** Public protection hook for claims, story generation and automation integrations. */
    public static void protectAround(ServerLevel level, BlockPos pos) {
        ErosionSavedData data = ErosionSavedData.get(level);
        for (int z = -1; z <= 1; z++) {
            for (int x = -1; x <= 1; x++) data.protect(ChunkPos.asLong((pos.getX() >> 4) + x, (pos.getZ() >> 4) + z));
        }
    }

    /** Runtime exposure is transient; material and eligibility stay in level SavedData. */
    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ErosionManager.clear(level);
            com.thunder.wildernessodysseyapi.watersystem.water.surface.WaterDepthSampler.clear(level);
        }
    }
}
