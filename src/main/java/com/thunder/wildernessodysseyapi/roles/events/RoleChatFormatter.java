package com.thunder.wildernessodysseyapi.roles.events;

import com.thunder.wildernessodysseyapi.roles.api.PlayerRole;
import com.thunder.wildernessodysseyapi.roles.core.PlayerRoleManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;

public class RoleChatFormatter {
    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        PlayerRole role = PlayerRoleManager.getRole(player.getUUID());

        Component newMessage = Component.literal("[")
                .append(role.getDisplayName())
                .append("] ")
                .append(player.getDisplayName())
                .append(": ")
                .append(event.getMessage());

        event.setMessage(newMessage);
    }
}