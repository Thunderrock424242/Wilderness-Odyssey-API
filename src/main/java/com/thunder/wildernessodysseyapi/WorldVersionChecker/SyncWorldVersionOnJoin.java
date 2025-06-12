package com.thunder.wildernessodysseyapi.WorldVersionChecker;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.Core.WildernessOdysseyAPINetworkHandler;
import com.thunder.wildernessodysseyapi.WorldVersionChecker.Packet.SyncWorldVersionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public class SyncWorldVersionOnJoin {
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var level = player.serverLevel();
        var data  = level.getDataStorage().computeIfAbsent(
                WorldVersionData.factory(),
                WorldVersionData.FILE_NAME
        );
        WildernessOdysseyAPINetworkHandler.sendTo(player, new SyncWorldVersionPacket(data.getMajor(), data.getMinor()));
    }
}

