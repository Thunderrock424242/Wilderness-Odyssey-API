package com.thunder.wildernessodysseyapi.ai.story;

import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.ai.voice.network.AetherVoiceLinePayload;
import com.thunder.wildernessodysseyapi.lorebook.LoreBookManager;
import com.thunder.wildernessodysseyapi.meteor.api.MeteorSiteServices;
import com.thunder.wildernessodysseyapi.temporalrift.echo.EchoDiscoveryManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Captures authoritative context on the logical server, then schedules Aether I/O.
 * Clients only receive the resulting dialogue and optional voice metadata.
 */
public class AIChatListener {
    private static volatile Session session;
    private static final AtomicLong RESPONSE_IDS = new AtomicLong();

    /** Returns the active logical server client, or null while no server is running. */
    public static AIClient getClient() {
        Session current = session;
        return current == null ? null : current.client();
    }

    /** Only addressed chat or an active onboarding exchange enters the bounded I/O pool. */
    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        MinecraftServer server = player.server;
        Session current = session;
        String message = event.getMessage().getString().trim();
        if (current == null || current.server() != server || message.isEmpty()
                || !current.client().isAtlasEnabled() || !AIChatAccessPolicy.isAvailable(server)) {
            return;
        }
        AIClient client = current.client();
        UUID playerId = player.getUUID();
        if (!client.isAiInvocation(message) && !client.hasPendingOnboarding(playerId)) {
            return;
        }
        long responseId = RESPONSE_IDS.incrementAndGet();
        if (!current.requests().acquire(playerId)) {
            player.sendSystemMessage(Component.literal("[" + client.getDisplayName()
                    + "] I'm still handling a request. Please try again shortly."));
            return;
        }
        String worldKey = player.serverLevel().dimension().location().toString();
        AIFallbackResponder.ResponseContext context =
                new AIFallbackResponder.ResponseContext(buildContextTags(player, worldKey));
        String profileKey = current.saveId() + "-" + playerId;
        String playerName = player.getName().getString();

        // This existing bounded path avoids the generic task timeout discarding a
        // valid 30-second backend reply. The transport owns the total I/O deadline.
        boolean accepted = AsyncTaskManager.trySubmitIoWork("Aether_Chat", () -> {
            VoiceIntegration.VoiceResult reply;
            try {
                String onboarding = client.handleOnboarding(playerId, message);
                reply = onboarding == null || onboarding.isBlank()
                        ? client.sendMessageWithVoice(worldKey, profileKey, playerId, playerName, message, context)
                        : new VoiceIntegration().wrap(client.getDisplayName(), onboarding);
            } catch (Exception exception) {
                // Exception contents can include remote data. The deterministic reply is sufficient.
                reply = client.fallback(message, context);
            }
            VoiceIntegration.VoiceResult result = reply;
            server.execute(() -> {
                current.requests().release(playerId);
                if (session != current) {
                    return;
                }
                ServerPlayer online = server.getPlayerList().getPlayer(playerId);
                if (online != null && worldKey.equals(online.serverLevel().dimension().location().toString())) {
                    deliver(online, responseId, result);
                }
            });
        });
        if (!accepted) {
            current.requests().release(playerId);
            deliver(player, responseId, client.fallback(message, context));
        }
    }

    /** Shared worker pools initialize at normal priority before this readiness submission. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarting(ServerStartingEvent event) {
        Session previous = session;
        session = null;
        if (previous != null) {
            previous.client().close();
        }
        AIClient client = new AIClient();
        MinecraftServer server = event.getServer();
        String saveIdentity = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString();
        UUID saveId = UUID.nameUUIDFromBytes(saveIdentity.getBytes(StandardCharsets.UTF_8));
        session = new Session(server, client, saveId, new AIChatRequestGate(32));
        client.scanGameData(server);
    }

    /** Invalidate callbacks before the shared workers shut down; cancel only this session's HTTP work. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerStopping(ServerStoppingEvent event) {
        Session current = session;
        if (current != null && current.server() == event.getServer()) {
            session = null;
            current.client().close();
        }
    }

    private static void deliver(ServerPlayer player, long responseId, VoiceIntegration.VoiceResult reply) {
        if (reply.text() == null || reply.text().isBlank()) {
            return;
        }
        player.sendSystemMessage(Component.literal("[" + reply.speaker() + "] " + reply.text()));
        PacketDistributor.sendToPlayer(player, new AetherVoiceLinePayload(responseId, reply.asVoiceLine()));
    }

    private static Set<String> buildContextTags(ServerPlayer player, String worldKey) {
        Set<String> tags = new LinkedHashSet<>();
        addContextTag(tags, "dimension:" + worldKey);
        String dimensionPath = player.serverLevel().dimension().location().getPath();
        addContextTag(tags, "dimension:" + dimensionPath);
        addContextTag(tags, "overworld".equals(dimensionPath) ? "surface" : dimensionPath);
        player.serverLevel().getBiome(player.blockPosition()).unwrapKey()
                .ifPresent(key -> addContextTag(tags, "biome:" + key.location().getPath()));
        if (MeteorSiteServices.isNearSite(player.serverLevel(), player.blockPosition(), 96)) {
            addContextTag(tags, "zone:meteor_site");
            addContextTag(tags, "discovery:meteor_site");
        }
        addContextTag(tags, "interface:server_chat");
        tags.addAll(EchoDiscoveryManager.contextTags(player));
        LoreBookManager.scanInventory(player);
        Set<String> lore = LoreBookManager.getCollected(player);
        if (!lore.isEmpty()) {
            addContextTag(tags, "has_lore");
            for (String loreId : lore) {
                if (tags.size() >= 128) {
                    break;
                }
                addContextTag(tags, "lore:" + loreId);
            }
        }
        return tags;
    }

    private static void addContextTag(Set<String> tags, String tag) {
        if (tag != null && !tag.isBlank() && tag.length() <= 256) {
            tags.add(tag.trim().toLowerCase(Locale.ROOT));
        }
    }

    private record Session(MinecraftServer server, AIClient client, UUID saveId, AIChatRequestGate requests) {
    }
}
