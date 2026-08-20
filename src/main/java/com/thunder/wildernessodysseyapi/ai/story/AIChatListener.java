package com.thunder.wildernessodysseyapi.ai.story;

import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.lorebook.LoreBookManager;
import com.thunder.wildernessodysseyapi.meteor.api.MeteorSiteServices;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AIChatListener {

    private static final AIClient CLIENT = new AIClient();
    private static final Set<UUID> ACTIVE_SESSIONS = ConcurrentHashMap.newKeySet();

    public static AIClient getClient() {
        return CLIENT;
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String message = event.getMessage().getString().trim();

        if (message.isEmpty() || !CLIENT.isAtlasEnabled() || !isHoldingActivationItem(player)) {
            return;
        }

        boolean mentionsWakeWord = CLIENT.isAiInvocation(message);
        boolean sessionActive = ACTIVE_SESSIONS.contains(player.getUUID());
        boolean conversational = isConversational(message);

        if (!mentionsWakeWord && !sessionActive && !conversational) {
            return;
        }

        ACTIVE_SESSIONS.add(player.getUUID());
        String worldKey = player.serverLevel().dimension().location().toString();
        AIFallbackResponder.ResponseContext responseContext = new AIFallbackResponder.ResponseContext(buildContextTags(player, worldKey));
        UUID playerId = player.getUUID();
        String playerName = player.getName().getString();
        var server = player.server;

        // Check onboarding first (fast, can be done main thread)
        String onboardingReply = CLIENT.handleOnboarding(playerId, message);
        if (onboardingReply != null && !onboardingReply.isBlank()) {
            player.sendSystemMessage(Component.literal("[" + CLIENT.getDisplayName() + "] " + onboardingReply));
            return;
        }

        // --- THE FIX: OFFLOAD TO ASYNC MANAGER ---
        AsyncTaskManager.submitIoTask("AI_Chat_" + playerName, () -> {
            // 1. This block runs on a background IO thread! No server freezing!
            VoiceIntegration.VoiceResult reply = CLIENT.sendMessageWithVoice(worldKey, playerName, message, responseContext);

            if (reply.text() == null || reply.text().isBlank()) {
                return java.util.Optional.empty();
            }

            String speaker = (reply.speaker() == null || reply.speaker().isBlank()) ? CLIENT.resolveSpeaker(message) : reply.speaker();

            // 2. We return a MainThreadTask. AsyncTaskManager will safely execute this on the main tick.
            return java.util.Optional.of(owningServer -> {
                ServerPlayer onlinePlayer = owningServer.getPlayerList().getPlayer(playerId);
                if (onlinePlayer != null) {
                    onlinePlayer.sendSystemMessage(Component.literal("[" + speaker + "] " + reply.text()));
                }
            });
        }).thenAccept(accepted -> {
            if (accepted) {
                return;
            }
            // Rejection/timeout may complete on a worker. Route user feedback
            // through the server executor and resolve the player by UUID again.
            server.execute(() -> {
                ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerId);
                if (onlinePlayer != null) {
                    onlinePlayer.sendSystemMessage(Component.literal(
                            "[" + CLIENT.getDisplayName() + "] I'm handling too many requests right now. Try again shortly."
                    ));
                }
            });
        });
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE_SESSIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        CLIENT.scanGameData(event.getServer());
    }

    // Helper methods remain exactly the same
    private static boolean isConversational(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return message.endsWith("?") || lower.startsWith("hey") || lower.startsWith("hi") || lower.contains("help") || lower.contains("you") || lower.contains("can i") || lower.contains("can you") || lower.contains("should i") || lower.contains("what") || lower.contains("how");
    }

    private static Set<String> buildContextTags(ServerPlayer player, String worldKey) {
        Set<String> tags = new LinkedHashSet<>();
        addContextTag(tags, "dimension:" + worldKey);

        String dimensionPath = player.serverLevel().dimension().location().getPath();
        addContextTag(tags, "dimension:" + dimensionPath);
        if ("overworld".equals(dimensionPath)) {
            addContextTag(tags, "surface");
        } else {
            addContextTag(tags, dimensionPath);
        }

        player.serverLevel().getBiome(player.blockPosition()).unwrapKey()
                .ifPresent(key -> addContextTag(tags, "biome:" + key.location().getPath()));

        LoreBookManager.scanInventory(player);
        Set<String> collectedLore = LoreBookManager.getCollected(player);
        if (!collectedLore.isEmpty()) {
            addContextTag(tags, "has_lore");
            for (String loreId : collectedLore) {
                addContextTag(tags, "lore:" + loreId);
            }
        }

        if (isNearMeteorSite(player)) {
            addContextTag(tags, "zone:meteor_site");
            addContextTag(tags, "discovery:meteor_site");
        }

        if (isHoldingActivationItem(player)) {
            addContextTag(tags, "interface:aether_relay");
        }
        return tags;
    }

    private static boolean isNearMeteorSite(ServerPlayer player) {
        return MeteorSiteServices.isNearSite(
                player.serverLevel(), player.blockPosition(), 96);
    }

    private static void addContextTag(Set<String> tags, String tag) {
        if (tag != null && !tag.isBlank()) {
            tags.add(tag.trim().toLowerCase(Locale.ROOT));
        }
    }

    private static boolean isHoldingActivationItem(ServerPlayer player) {
        return isRedWool(player.getMainHandItem()) || isRedWool(player.getOffhandItem());
    }

    private static boolean isRedWool(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.RED_WOOL);
    }
}
