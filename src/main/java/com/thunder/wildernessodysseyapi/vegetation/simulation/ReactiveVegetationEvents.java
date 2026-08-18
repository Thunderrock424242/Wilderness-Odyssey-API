package com.thunder.wildernessodysseyapi.vegetation.simulation;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.vegetation.api.ReactiveVegetationServices;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** NeoForge lifecycle bridge for the loaded-chunk vegetation scheduler. */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class ReactiveVegetationEvents {

    private ReactiveVegetationEvents() {
    }

    /** Tracks a chunk only after normal promotion to a loaded server LevelChunk. */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level
                && event.getChunk() instanceof LevelChunk chunk) {
            ReactiveVegetationScheduler.onChunkLoad(level, chunk);
        }
    }

    /** Removes the chunk's due entry before it can perform any further work. */
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level
                && event.getChunk() instanceof LevelChunk chunk) {
            ReactiveVegetationScheduler.onChunkUnload(level, chunk);
        }
    }

    /** Drains staggered due work after normal server tick processing. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!event.hasTime()) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            ReactiveVegetationScheduler.tickLevel(level);
        }
    }

    /** Releases only the unloading dimension's ephemeral schedule. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ReactiveVegetationScheduler.clearLevel(level);
            ReactiveVegetationServices.clearDisturbances(level);
        }
    }

    /** Clears process-scoped queues after all dimensions have saved. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ReactiveVegetationScheduler.clearAll();
        ReactiveVegetationServices.clearAllDisturbances();
    }
}
