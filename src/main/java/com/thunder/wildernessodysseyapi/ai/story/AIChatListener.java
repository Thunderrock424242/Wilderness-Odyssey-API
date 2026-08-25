package com.thunder.wildernessodysseyapi.ai.story;

import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.lorebook.LoreBookManager;
import com.thunder.wildernessodysseyapi.meteor.api.MeteorSiteServices;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Listens to ordinary single-player chat and returns A.E.T.H.E.R replies. */
public class AIChatListener {

    private static final AIClient CLIENT = new AIClient();

    public static AIClient getClient() {
        return CLIENT;
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String message = event.getMessage().getString().trim();
        MinecraftServer server = player.server;

        if (message.isEmpty() || !CLIENT.isAtlasEnabled() || !AIChatAccessPolicy.isAvailable(server)) {
            return;
        }

        String worldKey = player.serverLevel().dimension().location().toString();
        AIFallbackResponder.ResponseContext responseContext = new AIFallbackResponder.ResponseContext(buildContextTags(player, worldKey));
        UUID playerId = player.getUUID();
        String playerName = player.getName().getString();

        // Onboarding only reads the captured chat text and per-player progress.
        String onboardingReply = CLIENT.handleOnboarding(playerId, message);
        if (onboardingReply != null && !onboardingReply.isBlank()) {
            player.sendSystemMessage(Component.literal("[" + CLIENT.getDisplayName() + "] " + onboardingReply));
            return;
        }

        AsyncTaskManager.submitIoTask("AI_Chat_" + playerName, () -> {
            // Local model I/O and scripted matching never block the server thread.
            VoiceIntegration.VoiceResult reply = CLIENT.sendMessageWithVoice(worldKey, playerName, message, responseContext);

            if (reply.text() == null || reply.text().isBlank()) {
                return java.util.Optional.empty();
            }

            String speaker = (reply.speaker() == null || reply.speaker().isBlank()) ? CLIENT.resolveSpeaker(message) : reply.speaker();

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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarting(ServerStartingEvent event) {
        // ServerLifecycleEvents initializes the shared worker pools at normal priority first.
        CLIENT.scanGameData(event.getServer());
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

        addContextTag(tags, "interface:singleplayer_chat");
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
}
