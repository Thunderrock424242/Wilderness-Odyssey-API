package com.thunder.wildernessodysseyapi.quest.network;

import com.thunder.wildernessodysseyapi.quest.client.QuestClientState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import java.util.UUID;

/** Feature-owned contract delegated by the existing central registrar; the cache has no client-only game types. */
public final class QuestPayloads {
    private static long lastResync;
    private QuestPayloads() { }
    public static void register(PayloadRegistrar registrar) {
        registrar.playBidirectional(QuestWorkshopPayload.TYPE, QuestWorkshopPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player)
                com.thunder.wildernessodysseyapi.quest.runtime.QuestServerEvents.session(player.getServer())
                        .flatMap(com.thunder.wildernessodysseyapi.quest.runtime.QuestServerSession::workshop).ifPresent(workshop -> workshop.handle(player, payload));
            else com.thunder.wildernessodysseyapi.quest.client.workshop.QuestWorkshopClientState.INSTANCE.accept(payload);
        });
        registrar.playToClient(QuestViewPayload.TYPE, QuestViewPayload.STREAM_CODEC, (payload, context) -> QuestClientState.INSTANCE.accept(payload));
        registrar.playToClient(QuestProgressDeltaPayload.TYPE, QuestProgressDeltaPayload.STREAM_CODEC, (payload, context) -> {
            if (QuestClientState.INSTANCE.accept(payload) == QuestClientState.ApplyResult.RESYNC && System.nanoTime() - lastResync > 2_000_000_000L) {
                lastResync = System.nanoTime();
                PacketDistributor.sendToServer(new QuestPlayerRequestPayload(UUID.randomUUID(), payload.session(), payload.hash(), 0,
                        QuestPlayerRequestPayload.Operation.RESYNC, "", "", 0));
            }
        });
        registrar.playToClient(QuestRequestResultPayload.TYPE, QuestRequestResultPayload.STREAM_CODEC, (payload, context) -> { /* Phase 1 commands also present the server message. */ });
        registrar.playToServer(QuestPlayerRequestPayload.TYPE, QuestPlayerRequestPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) QuestRequests.handle(player, payload);
        });
    }
}
