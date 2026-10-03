package com.thunder.wildernessodysseyapi.quest.workshop;

import com.thunder.wildernessodysseyapi.quest.QuestFixtures;
import com.thunder.wildernessodysseyapi.quest.definition.QuestCampaignSnapshot;
import com.thunder.wildernessodysseyapi.quest.definition.QuestDefinitionCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class QuestPublicationStoreTest {
    @TempDir Path world;

    private QuestCampaignSnapshot snapshot(String title) {
        var documents = QuestFixtures.documents();
        var campaign = documents.get(0).content();
        campaign.addProperty("title", title);
        documents.set(0, QuestFixtures.replace(documents.get(0), campaign));
        return new QuestDefinitionCodec().decode(documents, QuestFixtures.lookup()).snapshot().orElseThrow();
    }

    private QuestIoDispatcher io() {
        return new QuestIoDispatcher(new QuestIoDispatcher.Backend() {
            @Override public boolean enabled() { return false; }
            @Override public boolean submit(Runnable work) { fail("Unexpected shared I/O."); return false; }
        });
    }

    private QuestPublicationStore open(QuestIoDispatcher io, QuestPublicationStore.FaultInjector faults) {
        return QuestPublicationStore.open(world, io, QuestFixtures.lookup(), UUID.randomUUID(), faults).toCompletableFuture().join();
    }

    @Test
    void provisionsStableIdentityAndHoldsAnExclusiveLeaseUntilClose() {
        var io = io();
        var store = open(io, boundary -> { });
        var identity = store.metadata().worldId();
        var other = io();
        assertThrows(CompletionException.class, () -> open(other, boundary -> { }));
        other.close();
        store.close().toCompletableFuture().join();
        io.close();
        var restartedIo = io();
        var restarted = open(restartedIo, boundary -> { });
        assertEquals(identity, restarted.metadata().worldId());
        restarted.close().toCompletableFuture().join();
        restartedIo.close();
    }

    @Test
    void recoversExactlyTheDurablePublicationAtEveryInjectedWriteBoundary() {
        for (var failure : QuestPublicationStore.Boundary.values()) {
            Path testWorld = world.resolve(failure.name());
            var io = io();
            var store = QuestPublicationStore.open(testWorld, io, QuestFixtures.lookup(), UUID.randomUUID(), boundary -> { }).toCompletableFuture().join();
            var initial = snapshot("Initial");
            var prepared = store.prepare(initial).toCompletableFuture().join();
            store.activate(prepared, new QuestPublicationStore.CommitToken("", initial.hash(), store.metadata().worldId(), store.session(), 1)).toCompletableFuture().join();
            store.close().toCompletableFuture().join();
            io.close();
            var changedIo = io();
            var changed = QuestPublicationStore.open(testWorld, changedIo, QuestFixtures.lookup(), UUID.randomUUID(), boundary -> {
                if (boundary == failure) throw new IOException("Injected " + boundary);
            }).toCompletableFuture().join();
            var next = snapshot("Changed");
            assertThrows(CompletionException.class, () -> {
                var stored = changed.prepare(next).toCompletableFuture().join();
                changed.activate(stored, new QuestPublicationStore.CommitToken(initial.hash(), next.hash(), changed.metadata().worldId(), changed.session(), 2)).toCompletableFuture().join();
            });
            changed.close().toCompletableFuture().join();
            changedIo.close();
            var recoveryIo = io();
            var recovered = QuestPublicationStore.open(testWorld, recoveryIo, QuestFixtures.lookup(), UUID.randomUUID(), boundary -> { }).toCompletableFuture().join();
            assertEquals(failure == QuestPublicationStore.Boundary.AFTER_MANIFEST_REPLACE ? next.hash() : initial.hash(),
                    recovered.activeSnapshot().orElseThrow().hash(), failure.name());
            recovered.close().toCompletableFuture().join();
            recoveryIo.close();
        }
    }

    @Test
    void missingProvisionedLedgerAndCorruptSnapshotAreNeverInterpretedAsEmptyState() throws Exception {
        var io = io();
        var store = open(io, boundary -> { });
        var initial = snapshot("Initial");
        store.activate(store.prepare(initial).toCompletableFuture().join(),
                new QuestPublicationStore.CommitToken("", initial.hash(), store.metadata().worldId(), store.session(), 1)).toCompletableFuture().join();
        store.close().toCompletableFuture().join();
        io.close();
        Path root = world.resolve("wildernessodysseyapi/quests");
        Path ledger = root.resolve("transactions/reward-ledger.jsonl");
        byte[] original = Files.readAllBytes(ledger);
        Files.delete(ledger);
        var missingIo = io();
        assertThrows(CompletionException.class, () -> open(missingIo, boundary -> { }));
        missingIo.close();
        Files.write(ledger, original);
        Files.writeString(root.resolve("published/" + initial.hash() + ".json"), "{}");
        var corruptIo = io();
        assertThrows(CompletionException.class, () -> open(corruptIo, boundary -> { }));
        corruptIo.close();
    }

    @Test
    void unsupportedStoreVersionsAndBadManifestChecksumsArePreservedAndFailClosed() throws Exception {
        for (String corruption : new String[]{"metadataVersion", "manifestVersion", "manifestChecksum"}) {
            Path testWorld = world.resolve(corruption);
            var io = io();
            var store = QuestPublicationStore.open(testWorld, io, QuestFixtures.lookup(), UUID.randomUUID(), boundary -> { }).toCompletableFuture().join();
            var initial = snapshot("Initial");
            store.activate(store.prepare(initial).toCompletableFuture().join(),
                    new QuestPublicationStore.CommitToken("", initial.hash(), store.metadata().worldId(), store.session(), 1)).toCompletableFuture().join();
            store.close().toCompletableFuture().join();
            io.close();

            Path damaged = testWorld.resolve("wildernessodysseyapi/quests/")
                    .resolve(corruption.equals("metadataVersion") ? "metadata.json" : "active.json");
            var document = com.google.gson.JsonParser.parseString(Files.readString(damaged)).getAsJsonObject();
            if (corruption.equals("manifestChecksum")) document.addProperty("checksum", "b".repeat(64));
            else document.addProperty("formatVersion", 999);
            Files.writeString(damaged, document.toString());
            byte[] preserved = Files.readAllBytes(damaged);
            var restart = io();
            try {
                assertThrows(CompletionException.class, () -> QuestPublicationStore.open(testWorld, restart,
                        QuestFixtures.lookup(), UUID.randomUUID(), boundary -> { }).toCompletableFuture().join(), corruption);
                assertArrayEquals(preserved, Files.readAllBytes(damaged), corruption);
            } finally {
                restart.close();
            }
        }
    }

    @Test
    void containedPathsRejectTraversalAndAbsolutePaths() {
        assertThrows(IOException.class, () -> QuestPublicationStore.containedPath(world, "../outside"));
        assertThrows(IOException.class, () -> QuestPublicationStore.containedPath(world, "published/../active.json"));
        assertThrows(IOException.class, () -> QuestPublicationStore.containedPath(world, world.resolve("outside").toString()));
    }

    @Test
    void containedPathsRejectSymbolicDirectories() throws Exception {
        Path outside = Files.createTempDirectory("quest-outside-");
        Path link = world.resolve("linked");
        try {
            try {
                Files.createSymbolicLink(link, outside);
            } catch (IOException | UnsupportedOperationException exception) {
                org.junit.jupiter.api.Assumptions.abort("This account cannot create a symbolic link; junction runtime evidence remains separate.");
            }
            assertThrows(IOException.class, () -> QuestPublicationStore.containedPath(world, "linked/value.json"));
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void missingActiveManifestWithHistoryCannotReseedAnExistingCampaign() throws Exception {
        var io = io(); var store = open(io, boundary -> { });
        var initial = snapshot("Original");
        store.activate(store.prepare(initial).toCompletableFuture().join(), new QuestPublicationStore.CommitToken("", initial.hash(),
                store.metadata().worldId(), store.session(), 1)).toCompletableFuture().join();
        store.close().toCompletableFuture().join(); io.close();
        Files.delete(world.resolve("wildernessodysseyapi/quests/active.json"));
        var restart = io();
        try { assertThrows(CompletionException.class, () -> open(restart, boundary -> { })); }
        finally { restart.close(); }
    }

    @Test
    void temporaryFilesArePreservedAndNeverSelectedAsActive() throws Exception {
        var io = io(); var store = open(io, boundary -> { });
        store.close().toCompletableFuture().join(); io.close();
        Path temporary = world.resolve("wildernessodysseyapi/quests/active.json.tmp-interrupted");
        Files.writeString(temporary, "{partial");
        var restart = io(); var restored = open(restart, boundary -> { });
        assertTrue(restored.activeSnapshot().isEmpty());
        assertEquals("{partial", Files.readString(temporary));
        restored.close().toCompletableFuture().join(); restart.close();
    }

    @Test
    void closeDrainsAnAlreadyAcceptedCommitBeforeReleasingTheWorldLease() throws Exception {
        var io = io();
        var store = open(io, boundary -> { });
        var candidate = snapshot("Initial");
        var prepared = store.prepare(candidate).toCompletableFuture().join();
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var blocker = io.submit(() -> {
            started.countDown();
            if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IOException("Test worker was not released.");
            return null;
        });
        assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS));
        var committed = store.activate(prepared, new QuestPublicationStore.CommitToken("", candidate.hash(),
                store.metadata().worldId(), store.session(), 1)).toCompletableFuture();
        var closing = store.close().toCompletableFuture();
        release.countDown();
        blocker.toCompletableFuture().join();
        assertEquals(candidate.hash(), committed.join().snapshot().hash());
        closing.join();
        io.close();
        var restartIo = io();
        var restart = open(restartIo, boundary -> { });
        assertEquals(candidate.hash(), restart.activeSnapshot().orElseThrow().hash());
        restart.close().toCompletableFuture().join();
        restartIo.close();
    }

    @Test
    void preparationRejectsARegistryGenerationThatWasNotCapturedByItsLookup() {
        var io = io();
        var store = open(io, boundary -> { });
        var candidate = new QuestDefinitionCodec().decode(QuestFixtures.documents(), QuestFixtures.lookup(8)).snapshot().orElseThrow();
        assertThrows(CompletionException.class, () -> store.prepare(candidate).toCompletableFuture().join());
        store.close().toCompletableFuture().join();
        io.close();
    }

    @Test
    void reloadedCandidatesUseTheirNewCapturedAvailabilityRatherThanTheStartupLookup() {
        var io = io();
        var store = open(io, boundary -> { });
        var availability = QuestFixtures.lookup(8);
        var candidate = new QuestDefinitionCodec().decode(QuestFixtures.documents(), availability).snapshot().orElseThrow();
        var prepared = store.prepare(candidate, availability).toCompletableFuture().join();
        assertEquals(8, prepared.snapshot().registryGeneration());
        assertEquals(candidate.hash(), prepared.snapshot().hash());
        store.close().toCompletableFuture().join();
        io.close();
    }

    @Test
    void aFullContentQueueCannotPreventTheTerminalLeaseRelease() throws Exception {
        var io = io();
        var store = open(io, boundary -> { });
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        io.submit(() -> {
            started.countDown();
            if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IOException("Test worker was not released.");
            return null;
        });
        assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS));
        var completed = new java.util.concurrent.atomic.AtomicInteger();
        for (int index = 0; index < 128; index++) assertTrue(io.trySubmit(completed::incrementAndGet));
        var closing = store.close().toCompletableFuture();
        release.countDown();
        closing.join();
        assertEquals(128, completed.get());
        io.close();
        var restartIo = io();
        var restart = open(restartIo, boundary -> { });
        restart.close().toCompletableFuture().join();
        restartIo.close();
    }
}
