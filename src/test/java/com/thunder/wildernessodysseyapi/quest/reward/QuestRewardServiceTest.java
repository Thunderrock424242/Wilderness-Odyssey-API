package com.thunder.wildernessodysseyapi.quest.reward;

import com.thunder.wildernessodysseyapi.quest.QuestFixtures;
import com.thunder.wildernessodysseyapi.quest.definition.RewardDefinition;
import com.thunder.wildernessodysseyapi.quest.workshop.QuestIoDispatcher;
import com.thunder.wildernessodysseyapi.quest.workshop.QuestPublicationStore;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class QuestRewardServiceTest {
    @TempDir Path world;

    @Test
    void retriesAndSimultaneousRequestsHaveOneEffectAndFullInventoryKeepsEntitlement() throws Exception {
        var io = io();
        var store = QuestPublicationStore.open(world, io, QuestFixtures.lookup(), UUID.randomUUID(), boundary -> { }).toCompletableFuture().join();
        var ledger = QuestRewardLedger.open(store).toCompletableFuture().join();
        var key = key(store);
        ledger.recordEarned(key, value(), "a".repeat(64)).toCompletableFuture().join();
        var authority = new Authority();
        var service = new QuestRewardService(ledger, authority);
        authority.fit = false;
        assertEquals(QuestRewardService.ClaimResult.INVENTORY_FULL, service.claim(key, UUID.randomUUID()).toCompletableFuture().join());
        assertEquals(QuestRewardLedger.State.EARNED, ledger.receipts().get(key).state());
        authority.fit = true;
        UUID request = UUID.randomUUID();
        var first = service.claim(key, request).toCompletableFuture();
        assertSame(first, service.claim(key, request).toCompletableFuture());
        assertEquals(QuestRewardService.ClaimResult.PENDING, service.claim(key, UUID.randomUUID()).toCompletableFuture().join());
        authority.awaitAndRun();
        assertEquals(QuestRewardService.ClaimResult.DELIVERED, first.join());
        assertEquals(1, authority.effects.get());
        assertEquals(QuestRewardService.ClaimResult.UNAVAILABLE, service.claim(key, UUID.randomUUID()).toCompletableFuture().join());
        service.close(); store.close().toCompletableFuture().join(); io.close();
    }

    @Test
    void disconnectOrCloseBeforeTheAcknowledgementCannotGrantThroughAnOldActor() throws Exception {
        var io = io();
        var store = QuestPublicationStore.open(world, io, QuestFixtures.lookup(), UUID.randomUUID(), boundary -> { }).toCompletableFuture().join();
        var ledger = QuestRewardLedger.open(store).toCompletableFuture().join();
        var key = key(store);
        ledger.recordEarned(key, value(), "a".repeat(64)).toCompletableFuture().join();
        var authority = new Authority();
        var service = new QuestRewardService(ledger, authority);
        var result = service.claim(key, UUID.randomUUID()).toCompletableFuture();
        authority.incarnation = UUID.randomUUID();
        authority.awaitAndRun();
        assertEquals(QuestRewardService.ClaimResult.UNAVAILABLE, result.join());
        assertEquals(0, authority.effects.get());
        assertEquals(QuestRewardLedger.State.EARNED, ledger.receipts().get(key).state());
        authority.current = true;
        var stopped = service.claim(key, UUID.randomUUID()).toCompletableFuture();
        service.close(); authority.awaitAndRun();
        assertEquals(QuestRewardService.ClaimResult.UNAVAILABLE, stopped.join());
        assertEquals(0, authority.effects.get());
        store.close().toCompletableFuture().join(); io.close();
    }

    @Test
    void anEffectExceptionLeavesAnUncertainReceiptRatherThanRegranting() throws Exception {
        var io = io();
        var store = QuestPublicationStore.open(world, io, QuestFixtures.lookup(), UUID.randomUUID(), boundary -> { }).toCompletableFuture().join();
        var ledger = QuestRewardLedger.open(store).toCompletableFuture().join();
        var key = key(store);
        ledger.recordEarned(key, value(), "a".repeat(64)).toCompletableFuture().join();
        var authority = new Authority(); authority.failEffect = true;
        var service = new QuestRewardService(ledger, authority);
        var result = service.claim(key, UUID.randomUUID()).toCompletableFuture(); authority.awaitAndRun();
        assertEquals(QuestRewardService.ClaimResult.NEEDS_REVIEW, result.join());
        assertEquals(QuestRewardLedger.State.NEEDS_REVIEW, ledger.receipts().get(key).state());
        assertEquals(QuestRewardService.ClaimResult.UNAVAILABLE, service.claim(key, UUID.randomUUID()).toCompletableFuture().join());
        assertEquals(1, authority.effects.get());
        service.close(); store.close().toCompletableFuture().join(); io.close();
    }

    private QuestRewardKey key(QuestPublicationStore store) { return new QuestRewardKey(store.metadata().worldId(), UUID.randomUUID(), ResourceLocation.parse("test:quest"), 0, value().id()); }
    private RewardDefinition value() { return new RewardDefinition(ResourceLocation.parse("test:xp"), new RewardDefinition.Experience(5)); }
    private QuestIoDispatcher io() { return new QuestIoDispatcher(new QuestIoDispatcher.Backend() {
        public boolean enabled() { return false; }
        public boolean submit(Runnable task) { throw new AssertionError(); }
    }); }

    private static final class Authority implements QuestRewardService.Authority {
        final ArrayDeque<Runnable> callbacks = new ArrayDeque<>();
        final AtomicInteger effects = new AtomicInteger();
        volatile boolean current = true;
        boolean fit = true;
        boolean failEffect;
        Object incarnation = UUID.randomUUID();
        public Object identity(UUID player) { return incarnation; }
        public boolean current(UUID player) { return current; }
        public boolean canDeliver(UUID player, RewardDefinition reward) { return fit; }
        public QuestRewardLedger.DeliveryOutcome deliver(UUID player, RewardDefinition reward) {
            effects.incrementAndGet();
            if (failEffect) throw new IllegalStateException("Effect interrupted");
            return QuestRewardLedger.DeliveryOutcome.DELIVERED;
        }
        public synchronized void execute(Runnable callback) { callbacks.add(callback); notifyAll(); }
        void awaitAndRun() throws Exception {
            Runnable callback;
            synchronized (this) {
                long deadline = System.nanoTime() + 5_000_000_000L;
                while (callbacks.isEmpty() && System.nanoTime() < deadline) wait(10);
                callback = callbacks.poll();
            }
            assertNotNull(callback, "No durable acknowledgement arrived"); callback.run();
        }
    }
}
