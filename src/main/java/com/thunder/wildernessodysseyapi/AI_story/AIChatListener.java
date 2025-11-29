package com.thunder.wildernessodysseyapi.AI_story;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for chat messages that include the Atlas wake word and sends back
 * lightweight AI replies using {@link AIClient}.
 */
public class AIChatListener {

    private static final AIClient CLIENT = new AIClient();
    private static final Set<UUID> ACTIVE_SESSIONS = ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String message = event.getMessage().getString().trim();
        if (message.isEmpty()) {
            return;
        }

        String wakeWord = CLIENT.getWakeWord();
        boolean mentionsWakeWord = message.toLowerCase(Locale.ROOT).contains(wakeWord);
        boolean sessionActive = ACTIVE_SESSIONS.contains(player.getUUID());

        if (!mentionsWakeWord && !sessionActive) {
            return;
        }

        ACTIVE_SESSIONS.add(player.getUUID());

        String worldKey = player.serverLevel().dimension().location().toString();
        VoiceIntegration.VoiceResult reply = CLIENT.sendMessageWithVoice(worldKey,
                player.getName().getString(), message);

        player.sendSystemMessage(Component.literal("[Atlas] " + reply.text()));
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE_SESSIONS.remove(event.getEntity().getUUID());
    }
}
