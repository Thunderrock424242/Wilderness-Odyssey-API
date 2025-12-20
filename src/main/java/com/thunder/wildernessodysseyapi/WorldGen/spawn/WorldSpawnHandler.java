package com.thunder.wildernessodysseyapi.WorldGen.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;
import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

/**
 * Handles selecting a world spawn location based on cryo tube placements.
 */
@EventBusSubscriber(modid = MOD_ID)
public class WorldSpawnHandler {

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel world) {
            configureWorldSpawn(world);
        }
    }

    public static void refreshWorldSpawn(ServerLevel world) {
        configureWorldSpawn(world);
    }

    private static void configureWorldSpawn(ServerLevel world) {
        CryoSpawnData data = CryoSpawnData.get(world);
        List<BlockPos> spawnBlockPositions = new ArrayList<>(data.getPositions());

        PlayerSpawnHandler.setSpawnBlocks(spawnBlockPositions);

        if (!spawnBlockPositions.isEmpty()) {
            BlockPos anchor = world.getSharedSpawnPos();
            BlockPos spawnBlockPos = spawnBlockPositions.stream()
                    .min(Comparator.comparingDouble(pos -> pos.distSqr(anchor)))
                    .orElse(spawnBlockPositions.get(0));
            world.setDefaultSpawnPos(spawnBlockPos, 0.0F);
        } else {
            LOGGER.warn("No Cryo Tube Blocks found in {}; players will remain at the vanilla spawn until one generates.",
                    world.dimension().location());
        }
    }
}
