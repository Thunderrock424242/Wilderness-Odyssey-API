package com.thunder.wildernessodysseyapi.quest.reward;

import com.thunder.wildernessodysseyapi.quest.QuestFixtures;
import com.thunder.wildernessodysseyapi.quest.definition.RewardDefinition;
import com.thunder.wildernessodysseyapi.quest.progress.QuestPlayerProgress;
import com.thunder.wildernessodysseyapi.quest.workshop.QuestIoDispatcher;
import com.thunder.wildernessodysseyapi.quest.workshop.QuestPublicationStore;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class QuestRewardLedgerTest {
    @TempDir Path world;
    private static final ResourceLocation QUEST = ResourceLocation.parse("test:quest");
    private static final ResourceLocation REWARD = ResourceLocation.parse("test:reward");
    private static final RewardDefinition VALUE = new RewardDefinition(REWARD, new RewardDefinition.Experience(5));
    private static final String HASH = "a".repeat(64);

    private QuestIoDispatcher io() {
        return new QuestIoDispatcher(new QuestIoDispatcher.Backend() {
            public boolean enabled() { return false; }
            public boolean submit(Runnable work) { throw new AssertionError("Shared worker was disabled."); }
        });
    }

    @Test
    void originalValueAndPermanentTerminalIdentitySurviveCompactionAndRestart() throws Exception {
        var io = io();
        var store = QuestPublicationStore.open(world, io, QuestFixtures.lookup(), UUID.randomUUID(), boundary -> { }).toCompletableFuture().join();
        var ledger = QuestRewardLedger.open(store).toCompletableFuture().join();
        UUID player = UUID.randomUUID();
        var key = new QuestRewardKey(store.metadata().worldId(), player, QUEST, 0, REWARD);
        assertTrue(ledger.recordEarned(key, VALUE, HASH).toCompletableFuture().join().accepted());
        assertTrue(ledger.recordEarned(key, VALUE, HASH).toCompletableFuture().join().accepted());
        assertFalse(ledger.recordEarned(key, new RewardDefinition(REWARD, new RewardDefinition.Experience(50)), HASH).toCompletableFuture().join().accepted());
        UUID request = UUID.randomUUID();
        var reservation = ledger.reserve(key, request).toCompletableFuture().join();
        assertTrue(reservation.fresh());
        assertFalse(ledger.reserve(key, request).toCompletableFuture().join().fresh());
        assertFalse(ledger.reserve(key, UUID.randomUUID()).toCompletableFuture().join().accepted());
        ledger.recordDelivery(reservation.reservationId(), QuestRewardLedger.DeliveryOutcome.DELIVERED).toCompletableFuture().join();
        ledger.compact().toCompletableFuture().join();
        store.close().toCompletableFuture().join(); io.close();
        var restartedIo = io();
        var restartedStore = QuestPublicationStore.open(world, restartedIo, QuestFixtures.lookup(), UUID.randomUUID(), boundary -> { }).toCompletableFuture().join();
        var restored = QuestRewardLedger.open(restartedStore).toCompletableFuture().join();
        assertEquals(QuestRewardLedger.State.DELIVERED, restored.receipts().get(key).state());
        assertFalse(restored.reserve(key, UUID.randomUUID()).toCompletableFuture().join().accepted());
        var progress = restored.reconcile(QuestPlayerProgress.empty(player));
        assertTrue(progress.completed(QUEST));
        assertEquals(VALUE, progress.earned().getFirst().originalValue());
        restartedStore.close().toCompletableFuture().join(); restartedIo.close();
    }

    @Test
    void interruptedReservationBecomesReviewAndNeverAutomaticallyClaimable() {
        var io = io();
        var store = QuestPublicationStore.open(world, io, QuestFixtures.lookup(), UUID.randomUUID(), boundary -> { }).toCompletableFuture().join();
        var key = new QuestRewardKey(store.metadata().worldId(), UUID.randomUUID(), QUEST, 0, REWARD);
        var ledger = QuestRewardLedger.open(store).toCompletableFuture().join();
        ledger.recordEarned(key, VALUE, HASH).toCompletableFuture().join();
        ledger.reserve(key, UUID.randomUUID()).toCompletableFuture().join();
        store.close().toCompletableFuture().join(); io.close();
        var nextIo = io();
        var nextStore = QuestPublicationStore.open(world, nextIo, QuestFixtures.lookup(), UUID.randomUUID(), boundary -> { }).toCompletableFuture().join();
        var restored = QuestRewardLedger.open(nextStore).toCompletableFuture().join();
        assertEquals(QuestRewardLedger.State.NEEDS_REVIEW, restored.receipts().get(key).state());
        assertFalse(restored.reserve(key, UUID.randomUUID()).toCompletableFuture().join().accepted());
        nextStore.close().toCompletableFuture().join(); nextIo.close();
    }

    @Test
    void aTruncatedTailLocksClaimsAndIsPreserved() throws Exception {
        var io = io();
        var store = QuestPublicationStore.open(world, io, QuestFixtures.lookup(), UUID.randomUUID(), boundary -> { }).toCompletableFuture().join();
        store.close().toCompletableFuture().join(); io.close();
        Path ledger = world.resolve("wildernessodysseyapi/quests/transactions/reward-ledger.jsonl");
        Files.writeString(ledger, "{partial", java.nio.file.StandardOpenOption.APPEND);
        byte[] evidence = Files.readAllBytes(ledger);
        var nextIo = io();
        var nextStore = QuestPublicationStore.open(world, nextIo, QuestFixtures.lookup(), UUID.randomUUID(), boundary -> { }).toCompletableFuture().join();
        assertThrows(CompletionException.class, () -> QuestRewardLedger.open(nextStore).toCompletableFuture().join());
        assertArrayEquals(evidence, Files.readAllBytes(ledger));
        nextStore.close().toCompletableFuture().join(); nextIo.close();
    }

    @Test
    void repeatRunsAreIndependentButBeforeEffectCancellationNeedsDurableAcknowledgement() {
        var io = io();
        var store = QuestPublicationStore.open(world, io, QuestFixtures.lookup(), UUID.randomUUID(), boundary -> { }).toCompletableFuture().join();
        var ledger = QuestRewardLedger.open(store).toCompletableFuture().join();
        UUID player = UUID.randomUUID();
        for (int run = 0; run < 2; run++) {
            var key = new QuestRewardKey(store.metadata().worldId(), player, QUEST, run, REWARD);
            ledger.recordEarned(key, VALUE, HASH).toCompletableFuture().join();
            var reservation = ledger.reserve(key, UUID.randomUUID()).toCompletableFuture().join();
            ledger.recordDelivery(reservation.reservationId(), QuestRewardLedger.DeliveryOutcome.NOT_APPLIED).toCompletableFuture().join();
            assertEquals(QuestRewardLedger.State.EARNED, ledger.receipts().get(key).state());
            assertTrue(ledger.reserve(key, UUID.randomUUID()).toCompletableFuture().join().fresh());
        }
        store.close().toCompletableFuture().join(); io.close();
    }

    @Test
    void newerDurableRepeatRestoresCurrentRunFromAnOlderAutosave() {
        var io = io();
        var store = QuestPublicationStore.open(world, io, QuestFixtures.lookup(), UUID.randomUUID(), boundary -> { }).toCompletableFuture().join();
        var ledger = QuestRewardLedger.open(store).toCompletableFuture().join();
        UUID player = UUID.randomUUID();
        for (int run = 0; run <= 2; run++) ledger.recordEarned(new QuestRewardKey(store.metadata().worldId(), player, QUEST, run, REWARD), VALUE, HASH).toCompletableFuture().join();
        var recovered = ledger.reconcile(QuestPlayerProgress.empty(player));
        assertEquals(2, recovered.currentRun(QUEST), "Recovery must not let a completed historical repeat start again");
        assertTrue(recovered.completed(QUEST));
        assertTrue(new com.thunder.wildernessodysseyapi.quest.progress.QuestProgressCodec().decode(
                new com.thunder.wildernessodysseyapi.quest.progress.QuestProgressCodec().encode(recovered)).accepted());
        store.close().toCompletableFuture().join(); io.close();
    }

    @Test
    void largeHistoryUsesLinearIdentityAndRunVisitsAndIsIdempotent() {
        UUID player = UUID.randomUUID(); UUID worldId = UUID.randomUUID();
        var history = new java.util.ArrayList<QuestRewardLedger.Receipt>();
        for (int run = 0; run < 3000; run++) for (int reward = 0; reward < 2; reward++) {
            var value = new RewardDefinition(ResourceLocation.parse("test:reward_" + reward), new RewardDefinition.Experience(5));
            history.add(new QuestRewardLedger.Receipt(new QuestRewardKey(worldId, player, QUEST, run, value.id()), value, HASH, QuestRewardLedger.State.EARNED, null, null));
        }
        var recovered = QuestRewardLedger.reconcileHistory(QuestPlayerProgress.empty(player), history);
        assertTrue(recovered.entitlementVisits() <= history.size() * 2L, "Identity reconciliation must index the admitted history once");
        assertEquals(3000, recovered.runVisits(), "Each completed run must be reconciled once, irrespective of rewards per run");
        assertEquals(2999, recovered.progress().currentRun(QUEST));
        var again = QuestRewardLedger.reconcileHistory(recovered.progress(), history);
        assertSame(recovered.progress(), again.progress());
        assertTrue(again.entitlementVisits() <= history.size() * 2L);
        assertEquals(3000, again.runVisits());
    }
}
