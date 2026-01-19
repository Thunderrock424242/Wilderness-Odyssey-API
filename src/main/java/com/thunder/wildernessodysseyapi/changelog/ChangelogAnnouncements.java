package com.thunder.wildernessodysseyapi.changelog;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

@EventBusSubscriber
public class ChangelogAnnouncements {

    private static final AtomicBoolean announced = new AtomicBoolean(false);
    private static volatile boolean newWorld = false;

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        Path worldVersionPath = server.getWorldPath(LevelResource.ROOT).resolve("world_version.json");
        newWorld = !Files.exists(worldVersionPath);
        announced.set(false);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!newWorld || announced.get()) {
            return;
        }
        MinecraftServer server = player.serverLevel().getServer();
        Path seenPath = server.getWorldPath(LevelResource.ROOT).resolve("wildernessodysseyapi_changelog.json");
        if (Files.exists(seenPath)) {
            announced.set(true);
            return;
        }
        if (!announced.compareAndSet(false, true)) {
            return;
        }
        boolean sent = ChangelogManager.sendChangelog(player, ModConstants.VERSION);
        if (sent) {
            ChangelogManager.writeSeenFile(seenPath, ModConstants.VERSION);
        }
    }
}
