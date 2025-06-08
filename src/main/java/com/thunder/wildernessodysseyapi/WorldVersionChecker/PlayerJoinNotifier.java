package com.thunder.wildernessodysseyapi.WorldVersionChecker;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public class PlayerJoinNotifier {

    private static boolean notify = false;

    public static void setNotifyOnJoin(boolean shouldNotify) {
        notify = shouldNotify;
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!notify) return;

        player.sendSystemMessage(Component.literal("""
            §c[Wilderness Odyssey] This world was upgraded from an older version.
            §eIf you encounter bugs, test them in a clean world before reporting.
            """));
    }
}
