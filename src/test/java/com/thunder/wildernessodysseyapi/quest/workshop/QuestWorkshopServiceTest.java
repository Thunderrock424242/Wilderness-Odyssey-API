package com.thunder.wildernessodysseyapi.quest.workshop;

import com.thunder.wildernessodysseyapi.quest.QuestFixtures;
import com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;

class QuestWorkshopServiceTest {
    @TempDir Path world;
    private QuestIoDispatcher io() {
        return new QuestIoDispatcher(new QuestIoDispatcher.Backend() {
            public boolean enabled() { return false; }
            public boolean submit(Runnable work) { throw new AssertionError("Disabled shared workers must not be used."); }
        });
    }
    private QuestPublicationStore publication(QuestIoDispatcher io) {
        return QuestPublicationStore.open(world, io, QuestFixtures.lookup(), UUID.randomUUID(), boundary -> { }).toCompletableFuture().join();
    }
    private QuestWorkshopEdit title(QuestWorkshopDraft draft, String title) {
        var id = QuestFixtures.id("test:first"); var content = draft.source(Kind.QUEST, id).orElseThrow().content(); content.addProperty("title", title);
        return QuestWorkshopDocuments.replace(draft, Kind.QUEST, id, content);
    }
    private static final class Authority implements QuestWorkshopService.Authority {
        boolean allowed = true; Object identity = new Object(); ConcurrentLinkedQueue<Runnable> callbacks = new ConcurrentLinkedQueue<>();
        public boolean allowed(UUID actor, Object incarnation) { return allowed && identity == incarnation; }
        public void execute(Runnable callback) { callbacks.add(callback); }
        <T> T await(java.util.concurrent.CompletionStage<T> stage) throws Exception {
            var future = stage.toCompletableFuture(); long deadline = System.nanoTime() + 5_000_000_000L;
            while (!future.isDone() && System.nanoTime() < deadline) { Runnable callback; while ((callback = callbacks.poll()) != null) callback.run(); Thread.sleep(1); }
            assertTrue(future.isDone(), "Owner callback did not complete."); return future.join();
        }
    }

    @Test
    void storageEnvelopeBoundsCannotReplaceTheLastRestorableDraft() throws Exception {
        var io = io(); var publication = publication(io);
        try {
            var store = QuestWorkshopStore.open(publication, QuestFixtures.documents()).toCompletableFuture().join();
            Path file = world.resolve("wildernessodysseyapi/quests/drafts/workshop.json"); var before = Files.readAllBytes(file);
            var id = QuestFixtures.id("test:first"); var content = store.draft().source(Kind.QUEST, id).orElseThrow().content();
            com.google.gson.JsonElement value = new com.google.gson.JsonPrimitive("leaf");
            for (int level = 0; level < 60; level++) { var array = new com.google.gson.JsonArray(); array.add(value); value = array; }
            content.add("incomplete", value); var edit = QuestWorkshopDocuments.replace(store.draft(), Kind.QUEST, id, content);
            assertDoesNotThrow(() -> QuestWorkshopEdit.decode(edit.encode()), "Patch is within its parser bound.");
            var next = store.draft().apply(edit).withRevision(1);
            assertDoesNotThrow(() -> QuestWorkshopDraft.decode(next.encode()), "Draft is within its parser bound.");
            assertThrows(java.util.concurrent.CompletionException.class, () -> store.save(0, next).toCompletableFuture().join());
            assertArrayEquals(before, Files.readAllBytes(file)); assertEquals(0, store.draft().revision());
            var saved = store.save(0, store.draft().apply(title(store.draft(), "Normal retry")).withRevision(1)).toCompletableFuture().join();
            var restored = QuestWorkshopStore.open(publication, java.util.List.of()).toCompletableFuture().join();
            assertEquals(saved, restored.draft());
        } finally { publication.close().toCompletableFuture().join(); io.close(); }
    }

    @Test
    void acknowledgesDurableDraftsAndRestoresIdentityWithoutChangingPublication() throws Exception {
        var io = io(); var publication = publication(io);
        var store = QuestWorkshopStore.open(publication, QuestFixtures.documents()).toCompletableFuture().join();
        var identity = store.draft().id(); var active = publication.activeSnapshot();
        var saved = store.save(0, store.draft().apply(title(store.draft(), "Saved")).withRevision(1));
        publication.close().toCompletableFuture().join(); saved.toCompletableFuture().join(); io.close();
        var restartIo = io(); var restart = publication(restartIo);
        var restored = QuestWorkshopStore.open(restart, java.util.List.of()).toCompletableFuture().join();
        assertEquals(identity, restored.draft().id()); assertEquals(1, restored.draft().revision());
        assertEquals("Saved", restored.draft().source(Kind.QUEST, QuestFixtures.id("test:first")).orElseThrow().content().get("title").getAsString());
        assertEquals(active, restart.activeSnapshot());
        restart.close().toCompletableFuture().join(); restartIo.close();
    }

    @Test
    void staleSecondOperatorCannotOverwriteTheFirstAcknowledgedSave() throws Exception {
        var io = io(); var publication = publication(io); var store = QuestWorkshopStore.open(publication, QuestFixtures.documents()).toCompletableFuture().join();
        var authority = new Authority(); var service = new QuestWorkshopService(store.draft(), store::save, authority); var actor = UUID.randomUUID();
        var first = service.edit(actor, authority.identity, 0, title(service.draft(), "First"));
        assertEquals(QuestWorkshopService.Status.BUSY, service.edit(UUID.randomUUID(), authority.identity, 0, title(service.draft(), "Second")).toCompletableFuture().join().status());
        assertEquals(QuestWorkshopService.Status.ACK, authority.await(first).status());
        assertEquals(QuestWorkshopService.Status.CONFLICT, service.edit(UUID.randomUUID(), authority.identity, 0, title(service.draft(), "Second")).toCompletableFuture().join().status());
        assertEquals("First", service.draft().source(Kind.QUEST, QuestFixtures.id("test:first")).orElseThrow().content().get("title").getAsString());
        publication.close().toCompletableFuture().join(); io.close();
    }

    @Test
    void permissionLossAndReplacementRejectNewWritesButAcceptedSaveCanFinish() throws Exception {
        var io = io(); var publication = publication(io); var store = QuestWorkshopStore.open(publication, QuestFixtures.documents()).toCompletableFuture().join();
        var authority = new Authority(); var service = new QuestWorkshopService(store.draft(), store::save, authority); var actor = UUID.randomUUID(); var old = authority.identity;
        authority.identity = new Object();
        assertEquals(QuestWorkshopService.Status.DENIED, service.edit(actor, old, 0, title(service.draft(), "Old")).toCompletableFuture().join().status());
        var accepted = service.edit(actor, authority.identity, 0, title(service.draft(), "Accepted")); authority.allowed = false;
        assertEquals(QuestWorkshopService.Status.ACK, authority.await(accepted).status());
        assertEquals(QuestWorkshopService.Status.DENIED, service.edit(actor, authority.identity, 1, title(service.draft(), "Denied")).toCompletableFuture().join().status());
        assertEquals(1, service.draft().revision()); publication.close().toCompletableFuture().join(); io.close();
    }

    @Test
    void failedWriterKeepsTheAcknowledgedRevisionAndPermitsRetry() throws Exception {
        var draft = QuestWorkshopDraft.create(QuestFixtures.documents()); var authority = new Authority();
        var service = new QuestWorkshopService(draft, (revision, next) -> CompletableFuture.failedFuture(new java.io.IOException("Injected write failure")), authority);
        assertEquals(QuestWorkshopService.Status.FAILED, authority.await(service.edit(UUID.randomUUID(), authority.identity, 0, title(draft, "Unsaved"))).status());
        assertEquals(draft, service.draft());
        assertEquals(QuestWorkshopService.Status.FAILED, authority.await(service.edit(UUID.randomUUID(), authority.identity, 0, title(draft, "Retry"))).status());
    }

    @Test
    void damagedOrUnsupportedDraftIsPreservedWithoutBreakingThePublicationStore() throws Exception {
        var io = io(); var publication = publication(io); QuestWorkshopStore.open(publication, QuestFixtures.documents()).toCompletableFuture().join();
        publication.close().toCompletableFuture().join(); io.close();
        Path file = world.resolve("wildernessodysseyapi/quests/drafts/workshop.json"); byte[] damaged = "{\"formatVersion\":999}".getBytes(java.nio.charset.StandardCharsets.UTF_8); Files.write(file, damaged);
        var restartIo = io(); var restart = publication(restartIo);
        assertThrows(java.util.concurrent.CompletionException.class, () -> QuestWorkshopStore.open(restart, java.util.List.of()).toCompletableFuture().join());
        assertArrayEquals(damaged, Files.readAllBytes(file)); assertTrue(restart.activeSnapshot().isEmpty());
        restart.close().toCompletableFuture().join(); restartIo.close();
    }
}
