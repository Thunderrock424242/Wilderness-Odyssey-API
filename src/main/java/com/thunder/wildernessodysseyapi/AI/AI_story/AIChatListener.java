package com.thunder.wildernessodysseyapi.AI.AI_story;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for chat messages that include the Atlas wake word or otherwise look
 * conversational and sends back lightweight AI replies using {@link AIClient}.
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
        if (!isHoldingActivationItem(player)) {
            return;
        }

        String wakeWord = CLIENT.getWakeWord();
        boolean mentionsWakeWord = message.toLowerCase(Locale.ROOT).contains(wakeWord);
        boolean sessionActive = ACTIVE_SESSIONS.contains(player.getUUID());

        boolean conversational = isConversational(message);

        if (!mentionsWakeWord && !sessionActive && !conversational) {
            return;
        }

        ACTIVE_SESSIONS.add(player.getUUID());

        String worldKey = player.serverLevel().dimension().location().toString();
        VoiceIntegration.VoiceResult reply = CLIENT.sendMessageWithVoice(worldKey,
                player.getName().getString(), message);

        player.getServer().execute(() -> player.sendSystemMessage(Component.literal("[Atlas] " + reply.text())));
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE_SESSIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        CLIENT.scanGameData(event.getServer());
    }

    private static boolean isConversational(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return message.endsWith("?")
                || lower.startsWith("hey")
                || lower.startsWith("hi")
                || lower.contains("help")
                || lower.contains("you")
                || lower.contains("can i")
                || lower.contains("can you")
                || lower.contains("should i")
                || lower.contains("what")
                || lower.contains("how");
    }

    private static boolean isHoldingActivationItem(ServerPlayer player) {
        return isRedWool(player.getMainHandItem()) || isRedWool(player.getOffhandItem());
    }

    private static boolean isRedWool(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.RED_WOOL);
    }
}
