package com.thunder.wildernessodysseyapi.quest.reward;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.quest.definition.RewardDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Game-thread claim coordinator. Only a fresh, forced reservation can call the effect sink. */
public final class QuestRewardService implements AutoCloseable {
    public enum ClaimResult { PENDING, DELIVERED, INVENTORY_FULL, UNAVAILABLE, NEEDS_REVIEW }
    public interface Authority {
        boolean current(UUID player);
        default Object identity(UUID player) { return player; }
        boolean canDeliver(UUID player, RewardDefinition reward);
        QuestRewardLedger.DeliveryOutcome deliver(UUID player, RewardDefinition reward);
        void execute(Runnable callback);
    }
    private record Request(QuestRewardKey key, CompletableFuture<ClaimResult> result) { }
    private final Thread owner = Thread.currentThread();
    private final QuestRewardLedger ledger;
    private final Authority authority;
    private final LinkedHashMap<UUID, Request> requests = new LinkedHashMap<>();
    private final Map<QuestRewardKey, UUID> inFlight = new HashMap<>();
    private boolean closed;

    public QuestRewardService(QuestRewardLedger ledger, Authority authority) { this.ledger = ledger; this.authority = authority; }

    public CompletionStage<ClaimResult> claim(ServerPlayer player, UUID worldId, ResourceLocation quest, long run,
                                             ResourceLocation reward, UUID request) {
        return claim(new QuestRewardKey(worldId, player.getUUID(), quest, run, reward), request);
    }

    /** Trusted server port for tests and commands; no client payload accepts a target player UUID. */
    public CompletionStage<ClaimResult> claim(QuestRewardKey key, UUID request) {
        requireOwner();
        pump();
        var replay = requests.get(request);
        if (replay != null) return replay.key().equals(key) ? replay.result() : CompletableFuture.completedFuture(ClaimResult.UNAVAILABLE);
        if (closed || !authority.current(key.playerId()) || ledger.locked()) return CompletableFuture.completedFuture(ClaimResult.UNAVAILABLE);
        var receipt = ledger.receipts().get(key);
        if (receipt == null || receipt.state() != QuestRewardLedger.State.EARNED) return CompletableFuture.completedFuture(ClaimResult.UNAVAILABLE);
        if (inFlight.containsKey(key) || inFlight.size() >= 128) return CompletableFuture.completedFuture(ClaimResult.PENDING);
        if (!authority.canDeliver(key.playerId(), receipt.originalValue())) return CompletableFuture.completedFuture(ClaimResult.INVENTORY_FULL);
        var result = new CompletableFuture<ClaimResult>();
        Object incarnation = authority.identity(key.playerId());
        requests.put(request, new Request(key, result)); inFlight.put(key, request);
        while (requests.size() > 512) {
            var oldest = requests.entrySet().stream().filter(entry -> entry.getValue().result().isDone()).findFirst();
            if (oldest.isEmpty()) break;
            requests.remove(oldest.get().getKey());
        }
        ledger.reserve(key, request).whenComplete((reservation, failure) -> authority.execute(() -> {
            requireOwner();
            if (failure != null || reservation == null || !reservation.accepted() || !reservation.fresh()) {
                finish(key, result, ClaimResult.UNAVAILABLE); return;
            }
            QuestRewardLedger.DeliveryOutcome outcome;
            ClaimResult status;
            if (closed || !authority.current(key.playerId()) || !java.util.Objects.equals(incarnation, authority.identity(key.playerId()))) {
                outcome = QuestRewardLedger.DeliveryOutcome.NOT_APPLIED; status = ClaimResult.UNAVAILABLE;
            } else if (!authority.canDeliver(key.playerId(), reservation.originalValue())) {
                outcome = QuestRewardLedger.DeliveryOutcome.NOT_APPLIED; status = ClaimResult.INVENTORY_FULL;
            } else {
                try {
                    outcome = authority.deliver(key.playerId(), reservation.originalValue());
                } catch (RuntimeException exception) {
                    ModConstants.LOGGER.error("Quest reward effect interrupted; reservation {} needs review", reservation.reservationId(), exception);
                    outcome = QuestRewardLedger.DeliveryOutcome.UNKNOWN;
                }
                status = switch (outcome) {
                    case DELIVERED -> ClaimResult.DELIVERED;
                    case NOT_APPLIED -> ClaimResult.UNAVAILABLE;
                    case UNKNOWN -> ClaimResult.NEEDS_REVIEW;
                };
            }
            ClaimResult finalStatus = status;
            ledger.recordDelivery(reservation.reservationId(), outcome).whenComplete((ignored, receiptFailure) -> {
                // Completing a future does not access Minecraft. Retain inFlight until the next owner-thread request.
                result.complete(receiptFailure == null ? finalStatus : ClaimResult.NEEDS_REVIEW);
            });
        }));
        return result;
    }

    private void finish(QuestRewardKey key, CompletableFuture<ClaimResult> result, ClaimResult status) {
        inFlight.remove(key); result.complete(status);
    }

    /** Periodic cleanup is mandatory even when the optional DataEngine is disabled. */
    public void pump() {
        requireOwner();
        inFlight.entrySet().removeIf(entry -> requests.get(entry.getValue()).result().isDone());
    }

    private void requireOwner() { if (Thread.currentThread() != owner) throw new IllegalStateException("Quest claims require the server thread."); }

    @Override public void close() {
        requireOwner(); closed = true;
        requests.values().forEach(request -> request.result().complete(ClaimResult.UNAVAILABLE));
        inFlight.clear();
    }
}
