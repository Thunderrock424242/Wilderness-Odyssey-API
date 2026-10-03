package com.thunder.wildernessodysseyapi.quest.workshop;

import com.thunder.wildernessodysseyapi.quest.QuestFixtures;
import com.thunder.wildernessodysseyapi.quest.definition.QuestCampaignSnapshot;
import com.thunder.wildernessodysseyapi.quest.definition.QuestDefinitionCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class QuestPublicationServiceTest {
    @TempDir Path world;

    private QuestIoDispatcher io() {
        return new QuestIoDispatcher(new QuestIoDispatcher.Backend() {
            @Override public boolean enabled() { return false; }
            @Override public boolean submit(Runnable work) { fail("Unexpected shared worker"); return false; }
        });
    }

    @Test
    void changedPermissionCandidateOrRegistryGenerationAbortsBeforeCommitAcceptance() throws Exception {
        for (String mutation : new String[]{"permission", "candidate", "generation"}) {
            var io = io();
            var store = QuestPublicationStore.open(world.resolve(mutation), io, QuestFixtures.lookup(), UUID.randomUUID(), boundary -> { }).toCompletableFuture().join();
            var authority = new Authority(store);
            var service = new QuestPublicationService(store, authority);
            var result = service.publish(UUID.randomUUID(), authority.candidate.hash(), "").toCompletableFuture();
            var callback = authority.callback();
            switch (mutation) {
                case "permission" -> authority.permitted = false;
                case "candidate" -> authority.candidate = authority.snapshot("Changed candidate");
                case "generation" -> authority.generation++;
                default -> fail("Unknown mutation");
            }
            callback.run();
            assertFalse(result.join().accepted(), mutation);
            assertTrue(store.activeSnapshot().isEmpty());
            service.close();
            store.close().toCompletableFuture().join();
            io.close();
        }
    }

    @Test
    void permissionLossAfterTokenAcceptanceCannotUndoDurableCommit() throws Exception {
        var io = io();
        var store = QuestPublicationStore.open(world, io, QuestFixtures.lookup(), UUID.randomUUID(), boundary -> { }).toCompletableFuture().join();
        var authority = new Authority(store);
        var service = new QuestPublicationService(store, authority);
        var result = service.publish(UUID.randomUUID(), authority.candidate.hash(), "").toCompletableFuture();
        authority.callback().run();
        authority.permitted = false;
        authority.callback().run();
        assertTrue(result.join().accepted());
        assertEquals(authority.candidate.hash(), authority.committed.snapshot().hash());
        assertFalse(service.publish(UUID.randomUUID(), authority.candidate.hash(), "").toCompletableFuture().join().accepted());
        service.close();
        store.close().toCompletableFuture().join();
        io.close();
        var restartedIo = io();
        var restarted = QuestPublicationStore.open(world, restartedIo, QuestFixtures.lookup(), UUID.randomUUID(), boundary -> { }).toCompletableFuture().join();
        assertEquals(authority.candidate.hash(), restarted.activeSnapshot().orElseThrow().hash());
        restarted.close().toCompletableFuture().join();
        restartedIo.close();
    }

    @Test
    void staleBaseAndStoppedSessionCannotActivateACandidate() throws Exception {
        var io = io();
        var store = QuestPublicationStore.open(world, io, QuestFixtures.lookup(), UUID.randomUUID(), boundary -> { }).toCompletableFuture().join();
        var authority = new Authority(store);
        var service = new QuestPublicationService(store, authority);
        assertFalse(service.publish(UUID.randomUUID(), authority.candidate.hash(), "a".repeat(64)).toCompletableFuture().join().accepted());
        var pending = service.publish(UUID.randomUUID(), authority.candidate.hash(), "").toCompletableFuture();
        var callback = authority.callback();
        service.close();
        callback.run();
        assertFalse(pending.join().accepted());
        assertTrue(store.activeSnapshot().isEmpty());
        store.close().toCompletableFuture().join();
        io.close();
    }

    static final class Authority implements QuestPublicationService.Authority {
        final QuestPublicationStore store;
        final LinkedBlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
        QuestCampaignSnapshot candidate = snapshot("Initial");
        boolean permitted = true;
        long generation = 7;
        QuestPublicationStore.PublicationCommit committed;

        Authority(QuestPublicationStore store) { this.store = store; }
        QuestCampaignSnapshot snapshot(String title) {
            var documents = QuestFixtures.documents();
            var campaign = documents.get(0).content();
            campaign.addProperty("title", title);
            documents.set(0, QuestFixtures.replace(documents.get(0), campaign));
            return new QuestDefinitionCodec().decode(documents, QuestFixtures.lookup()).snapshot().orElseThrow();
        }
        Runnable callback() throws Exception {
            var callback = callbacks.poll(5, TimeUnit.SECONDS);
            assertNotNull(callback, "Publication did not schedule its game-thread handoff.");
            return callback;
        }
        @Override public Optional<QuestPublicationService.Request> inspect(UUID operator, String hash, String base) {
            if (!permitted || !candidate.hash().equals(hash)) return Optional.empty();
            return Optional.of(new QuestPublicationService.Request(operator, candidate, base, generation, QuestFixtures.lookup(generation)));
        }
        @Override public boolean recheck(QuestPublicationService.Request request) {
            return permitted && candidate.hash().equals(request.snapshot().hash()) && generation == request.registryGeneration();
        }
        @Override public void execute(Runnable callback) { callbacks.add(callback); }
        @Override public void committed(QuestPublicationStore.PublicationCommit commit) { committed = commit; }
    }

    @Test
    void initialSeedRechecksCandidateGenerationEnablementAndEligibilityOnOwnerThread() throws Exception {
        for (String mutation : new String[]{"candidate", "generation", "disabled", "priorState"}) {
            var io = io();
            var store = QuestPublicationStore.open(world.resolve(mutation), io, QuestFixtures.lookup(), UUID.randomUUID(), boundary -> { }).toCompletableFuture().join();
            var authority = new Authority(store); var service = new QuestPublicationService(store, authority);
            var expected = authority.candidate;
            var eligible = new java.util.concurrent.atomic.AtomicBoolean(true);
            var result = service.seed(expected, QuestFixtures.lookup(), () -> eligible.get()
                    && authority.candidate.hash().equals(expected.hash()) && authority.generation == expected.registryGeneration()).toCompletableFuture();
            var callback = authority.callback();
            switch (mutation) {
                case "candidate" -> authority.candidate = authority.snapshot("Reloaded");
                case "generation" -> authority.generation++;
                default -> eligible.set(false);
            }
            callback.run();
            assertFalse(result.join().accepted(), mutation);
            assertTrue(store.activeSnapshot().isEmpty(), "Initial activation cannot bypass the commit recheck");
            service.close(); store.close().toCompletableFuture().join(); io.close();
        }
    }
}
