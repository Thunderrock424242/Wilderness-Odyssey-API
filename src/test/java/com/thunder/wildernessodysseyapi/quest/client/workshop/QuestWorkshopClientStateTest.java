package com.thunder.wildernessodysseyapi.quest.client.workshop;

import com.thunder.wildernessodysseyapi.quest.QuestFixtures;
import com.thunder.wildernessodysseyapi.quest.network.QuestWorkshopPayload;
import com.thunder.wildernessodysseyapi.quest.workshop.QuestWorkshopDraft;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class QuestWorkshopClientStateTest {
    private final UUID server = UUID.randomUUID(), editor = UUID.randomUUID();
    private QuestWorkshopPayload frame(QuestWorkshopPayload.Kind kind, byte[] bytes) {
        return new QuestWorkshopPayload(server, editor, UUID.randomUUID(), kind, 0, 0, bytes.length, bytes);
    }
    @Test void invalidSnapshotCannotReplaceThePreviousCompleteDraft() {
        var state = new QuestWorkshopClientState(); var draft = QuestWorkshopDraft.create(QuestFixtures.documents());
        state.accept(frame(QuestWorkshopPayload.Kind.OPEN, draft.encode())); assertEquals(draft, state.model().orElseThrow().draft());
        state.accept(frame(QuestWorkshopPayload.Kind.SNAPSHOT, "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(draft, state.model().orElseThrow().draft()); state.clear(); assertTrue(state.model().isEmpty());
    }
    @Test void staleNotificationRetainsLocalEditsAndBlocksFurtherAutosave() {
        var state = new QuestWorkshopClientState(); state.accept(frame(QuestWorkshopPayload.Kind.OPEN, QuestWorkshopDraft.create(QuestFixtures.documents()).encode()));
        var model = state.model().orElseThrow(); model.edit(com.thunder.wildernessodysseyapi.quest.workshop.QuestWorkshopDocuments.move(model.draft(), QuestFixtures.id("test:first"), 500, 500));
        state.accept(new QuestWorkshopPayload(server, editor, UUID.randomUUID(), QuestWorkshopPayload.Kind.STALE, 2, 0, 0, new byte[0]));
        assertTrue(model.dirty()); assertTrue(model.conflicted()); assertEquals(500, model.draft().positions().get(QuestFixtures.id("test:first")).x());
    }
    @Test void closingWaitsForDurableAcknowledgementThenReleasesTheOperatorSlot() {
        var sent = new java.util.ArrayList<QuestWorkshopPayload>(); var state = new QuestWorkshopClientState(sent::add);
        state.accept(frame(QuestWorkshopPayload.Kind.OPEN, QuestWorkshopDraft.create(QuestFixtures.documents()).encode()));
        var model = state.model().orElseThrow();
        model.edit(com.thunder.wildernessodysseyapi.quest.workshop.QuestWorkshopDocuments.move(model.draft(), QuestFixtures.id("test:first"), 3, 4));
        state.releaseWhenSaved(); state.tick();
        assertEquals(QuestWorkshopPayload.Kind.EDIT, sent.getFirst().kind());
        var save = sent.getFirst(); state.accept(new QuestWorkshopPayload(server, editor, save.request(), QuestWorkshopPayload.Kind.ACK, 1, 0, 0, new byte[0]));
        state.tick(); assertEquals(QuestWorkshopPayload.Kind.CLOSE, sent.getLast().kind());
        int count = sent.size(); state.tick(); assertEquals(count, sent.size()); assertFalse(model.dirty());
    }
    @Test void rejectedMultipartSaveStopsSendingAndRetainsTheEntireLocalEdit() {
        var sent = new java.util.ArrayList<QuestWorkshopPayload>(); var state = new QuestWorkshopClientState(sent::add);
        state.accept(frame(QuestWorkshopPayload.Kind.OPEN, QuestWorkshopDraft.create(QuestFixtures.documents()).encode()));
        var model = state.model().orElseThrow(); var id = QuestFixtures.id("test:first");
        var source = model.draft().source(com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument.Kind.QUEST, id).orElseThrow().content();
        source.addProperty("description", "x".repeat(150_000));
        model.edit(com.thunder.wildernessodysseyapi.quest.workshop.QuestWorkshopDocuments.replace(model.draft(), com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument.Kind.QUEST, id, source));
        model.retry(); state.tick(); assertEquals(4, sent.size()); var save = sent.getFirst();
        state.accept(new QuestWorkshopPayload(server, editor, save.request(), QuestWorkshopPayload.Kind.ERROR, 0, 0, 0, new byte[0]));
        state.tick(); assertEquals(4, sent.size()); assertTrue(model.dirty()); assertTrue(model.nextSave().isEmpty());
        assertEquals(150_000, model.draft().source(com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument.Kind.QUEST, id).orElseThrow().content().get("description").getAsString().length());
    }
}
