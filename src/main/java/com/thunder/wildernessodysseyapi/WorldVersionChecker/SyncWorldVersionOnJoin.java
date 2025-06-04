package com.thunder.wildernessodysseyapi.WorldVersionChecker;

import com.thunder.wildernessodysseyapi.Core.NovaAPINetworkHandler;
import com.thunder.wildernessodysseyapi.WorldVersionChecker.Packet.SyncWorldVersionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = "novaapi")
public class SyncWorldVersionOnJoin {
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = player.serverLevel();
        var storage = level.getDataStorage();
        var data = storage.computeIfAbsent(
                WorldVersionData.factory(),
                WorldVersionData.FILE_NAME
        );


        int version = data.getVersion();
        NovaAPINetworkHandler.sendTo(player, new SyncWorldVersionPacket(version));
    }
}
