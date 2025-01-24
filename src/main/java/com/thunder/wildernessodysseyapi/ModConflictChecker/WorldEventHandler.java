package com.thunder.wildernessodysseyapi.ModConflictChecker;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

@EventBusSubscriber
public class WorldEventHandler {

    public static void register() {
        ModConflictChecker.LOGGER.info("WorldEventHandler registered.");
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        ResourceLocation dimensionKey = event.getLevel().dimension().location();
        ModConflictChecker.LOGGER.info("Chunk loaded in dimension '{}'", dimensionKey);
    }

    @SubscribeEvent
    public static void onWorldLoad(WorldEvent.Load event) {
        ResourceLocation dimensionKey = event.getWorld().dimension().location();
        ModConflictChecker.LOGGER.info("World loaded in dimension '{}'", dimensionKey);
    }

    @SubscribeEvent
    public static void onStructureSpawn(WorldEvent.PotentialSpawns event) {
        ResourceLocation structureKey = event.getSpawner().getType().getRegistryName();
        ModConflictChecker.LOGGER.info("Structure '{}' attempted to spawn in the world.", structureKey);
    }
}