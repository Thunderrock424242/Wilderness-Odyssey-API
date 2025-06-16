package com.thunder.wildernessodysseyapi.WorldGen.debug;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;

@EventBusSubscriber
public class WorldgenEventHooks {
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        WorldgenErrorTracker.dumpToFile(worldPath);
    }
}