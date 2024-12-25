package com.thunder.wildernessodysseyapi.GlobalChat;

import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@EventBusSubscriber
public class ChatGameRule {
    public static final GameRules.Key<GameRules.BooleanValue> ENABLE_CHAT_CLIENT =
            GameRules.register("enableChatClient", GameRules.Category.MISC, GameRules.BooleanValue.create(false));

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // Example usage: Check if the game rule is enabled
        boolean isChatEnabled = event.getServer().getGameRules().getRule(ENABLE_CHAT_CLIENT).get();
        if (isChatEnabled) {
            System.out.println("Chat Client is enabled!");
            // Start the chat client logic here
        } else {
            System.out.println("Chat Client is disabled.");
        }
    }
}
