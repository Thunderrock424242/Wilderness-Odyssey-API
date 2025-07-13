package com.thunder.wildernessodysseyapi.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber
public class WorldLoadHandler {
    private static boolean spawned = false;

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || spawned) return;

        spawned = true;

        BlockPos spawnPos = new BlockPos(0, level.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0), 0); // or random
        VillageStructurePlacer.tryPlaceStructureOnce(level, spawnPos);

        // Save spawnPos in world data for /locate support
    }
}
