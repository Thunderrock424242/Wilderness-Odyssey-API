package com.thunder.wildernessodysseyapi.quest.client.workshop;

import com.thunder.wildernessodysseyapi.quest.QuestFixtures;
import com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument.Kind;
import com.thunder.wildernessodysseyapi.quest.workshop.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestWorkshopModelTest {
    private QuestWorkshopEdit title(QuestWorkshopModel model, String title) {
        var id = QuestFixtures.id("test:first"); var content = model.draft().source(Kind.QUEST, id).orElseThrow().content(); content.addProperty("title", title);
        return QuestWorkshopDocuments.replace(model.draft(), Kind.QUEST, id, content);
    }
    @Test void undoRedoAndViewportSurviveReinitializingScreenGeometry() {
        var model = new QuestWorkshopModel(QuestWorkshopDraft.create(QuestFixtures.documents())); model.panX = 120; model.panY = 80;
        model.edit(title(model, "Edited")); model.undo();
        assertEquals("Prepare for travel", model.draft().source(Kind.QUEST, QuestFixtures.id("test:first")).orElseThrow().content().get("title").getAsString());
        model.redo(); QuestWorkshopLayout.create(320, 240);
        assertEquals("Edited", model.draft().source(Kind.QUEST, QuestFixtures.id("test:first")).orElseThrow().content().get("title").getAsString()); assertEquals(120, model.panX);
    }
    @Test void editsDuringSaveWaitForTheAcknowledgedRevision() {
        var model = new QuestWorkshopModel(QuestWorkshopDraft.create(QuestFixtures.documents()));
        model.edit(title(model, "First")); var first = model.nextSave().orElseThrow(); model.edit(title(model, "Second")); assertTrue(model.nextSave().isEmpty());
        model.ack(first.request(), 1); var second = model.nextSave().orElseThrow(); assertEquals(1, second.revision());
        model.ack(second.request(), 2); assertFalse(model.dirty()); assertEquals(2, model.draft().revision());
        assertEquals("Second", model.draft().source(Kind.QUEST, QuestFixtures.id("test:first")).orElseThrow().content().get("title").getAsString());
    }
    @Test void conflictAndFailurePreserveLocalWorkAndBusyCanRetry() {
        var model = new QuestWorkshopModel(QuestWorkshopDraft.create(QuestFixtures.documents())); model.edit(title(model, "Local")); var first = model.nextSave().orElseThrow();
        model.busy(first.request()); var retried = model.nextSave().orElseThrow(); assertEquals(0, retried.revision());
        model.failure(retried.request(), "Disk unavailable"); assertTrue(model.dirty()); assertTrue(model.nextSave().isEmpty()); model.retry(); assertTrue(model.nextSave().isPresent());
        model.conflict(3); assertTrue(model.dirty()); assertTrue(model.nextSave().isEmpty());
        assertEquals("Local", model.draft().source(Kind.QUEST, QuestFixtures.id("test:first")).orElseThrow().content().get("title").getAsString());
    }
    @Test void acknowledgementWithWrongRequestOrRevisionCannotDiscardWork() {
        var model = new QuestWorkshopModel(QuestWorkshopDraft.create(QuestFixtures.documents())); model.edit(title(model, "Local")); var saving = model.nextSave().orElseThrow();
        model.ack(java.util.UUID.randomUUID(), 1); assertTrue(model.dirty()); model.ack(saving.request(), 5); assertTrue(model.dirty()); assertTrue(model.conflicted());
    }
}
