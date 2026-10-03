package com.thunder.wildernessodysseyapi.quest.workshop;

import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.quest.QuestFixtures;
import com.thunder.wildernessodysseyapi.quest.definition.QuestDefinitionCodec;
import com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument.Kind;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuestWorkshopDraftTest {
    private QuestWorkshopDraft draft() { return QuestWorkshopDraft.create(QuestFixtures.documents()); }

    @Test
    void createsAndDeletesReciprocalMembershipWithoutEditingTheOriginal() {
        var original = draft();
        var chapter = QuestFixtures.id("test:second");
        var quest = QuestFixtures.id("test:next");
        var edited = original.apply(QuestWorkshopDocuments.createChapter(original, chapter, "Second"));
        edited = edited.apply(QuestWorkshopDocuments.createQuest(edited, chapter, quest, "Next"));
        assertEquals(5, edited.sources().size());
        assertEquals(3, original.sources().size());
        assertTrue(new QuestDefinitionCodec().decode(edited.sources(), QuestFixtures.lookup()).accepted());
        edited = edited.apply(QuestWorkshopDocuments.delete(edited, Kind.CHAPTER, chapter));
        assertEquals(original.sources(), edited.sources());
        assertFalse(edited.positions().containsKey(quest));
    }

    @Test
    void deletingAQuestRepairsPrerequisitesAndMembership() {
        var value = draft(); var dependent = QuestFixtures.id("test:dependent");
        value = value.apply(QuestWorkshopDocuments.createQuest(value, QuestFixtures.id("test:start"), dependent, "Dependent"));
        value = value.apply(QuestWorkshopDocuments.link(value, QuestFixtures.id("test:first"), dependent, true));
        value = value.apply(QuestWorkshopDocuments.delete(value, Kind.QUEST, QuestFixtures.id("test:first")));
        assertEquals(0, value.source(Kind.QUEST, dependent).orElseThrow().content().getAsJsonObject("prerequisites").getAsJsonArray("quests").size());
        assertTrue(new QuestDefinitionCodec().decode(value.sources(), QuestFixtures.lookup()).accepted());
    }

    @Test
    void duplicatingAChapterCreatesNewQuestIdentitiesAndRemapsInternalEdges() {
        var value = draft(); var dependent = QuestFixtures.id("test:dependent");
        value = value.apply(QuestWorkshopDocuments.createQuest(value, QuestFixtures.id("test:start"), dependent, "Dependent"));
        value = value.apply(QuestWorkshopDocuments.link(value, QuestFixtures.id("test:first"), dependent, true));
        var copy = value.apply(QuestWorkshopDocuments.duplicate(value, Kind.CHAPTER, QuestFixtures.id("test:start"), QuestFixtures.id("test:copy")));
        var copied = copy.source(Kind.CHAPTER, QuestFixtures.id("test:copy")).orElseThrow().content().getAsJsonArray("quests");
        assertEquals(2, copied.size());
        String first = copied.get(0).getAsString(); String second = copied.get(1).getAsString();
        assertNotEquals("test:first", first); assertNotEquals(dependent.toString(), second);
        assertEquals(first, copy.source(Kind.QUEST, QuestFixtures.id(second)).orElseThrow().content().getAsJsonObject("prerequisites").getAsJsonArray("quests").get(0).getAsString());
        assertTrue(new QuestDefinitionCodec().decode(copy.sources(), QuestFixtures.lookup()).accepted());
    }

    @Test
    void roundTripsIdentityRevisionAndLayoutWhilePreservingIncompleteDrafts() throws Exception {
        var value = draft();
        var source = value.source(Kind.QUEST, QuestFixtures.id("test:first")).orElseThrow().content();
        source.addProperty("title", "");
        value = value.apply(QuestWorkshopDocuments.replace(value, Kind.QUEST, QuestFixtures.id("test:first"), source));
        value = value.apply(QuestWorkshopDocuments.move(value, QuestFixtures.id("test:first"), -250, 500)).withRevision(7);
        var restored = QuestWorkshopDraft.decode(value.encode());
        assertEquals(value, restored); assertEquals(7, restored.revision());
        assertFalse(new QuestDefinitionCodec().decode(restored.sources(), QuestFixtures.lookup()).accepted());
    }

    @Test
    void rejectsOversizedSourcesDuplicateIdentitiesAndOutOfRangePositions() {
        var value = draft(); var source = value.sources().getLast(); var content = source.content();
        content.addProperty("description", "x".repeat(QuestDefinitionCodec.MAX_SOURCE_BYTES));
        assertThrows(IllegalArgumentException.class, () -> value.apply(QuestWorkshopDocuments.replace(value, Kind.QUEST, source.id(), content)));
        assertThrows(IllegalArgumentException.class, () -> new QuestWorkshopDraft(UUID.randomUUID(), 0, List.of(source, source), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> QuestWorkshopDocuments.move(value, source.id(), 100_001, 0));
    }

    @Test
    void rejectsChecksumChangesAndUnsupportedVersions() throws Exception {
        var value = draft(); var json = JsonParser.parseString(new String(value.encode(), StandardCharsets.UTF_8)).getAsJsonObject();
        json.addProperty("revision", 3);
        assertThrows(java.io.IOException.class, () -> QuestWorkshopDraft.decode(json.toString().getBytes(StandardCharsets.UTF_8)));
        json.addProperty("formatVersion", 999);
        assertThrows(java.io.IOException.class, () -> QuestWorkshopDraft.decode(json.toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void patchRoundTripKeepsDefensiveCopiesAndCannotRenameAnIdentity() throws Exception {
        var value = draft(); var id = QuestFixtures.id("test:first");
        var content = value.source(Kind.QUEST, id).orElseThrow().content(); content.addProperty("title", "Updated");
        var edit = QuestWorkshopDocuments.replace(value, Kind.QUEST, id, content);
        content.addProperty("title", "Mutated");
        var decoded = QuestWorkshopEdit.decode(edit.encode());
        assertEquals("Updated", value.apply(decoded).source(Kind.QUEST, id).orElseThrow().content().get("title").getAsString());
        assertEquals(value.id(), value.apply(decoded).id()); assertEquals(value.revision(), value.apply(decoded).revision());
    }
}
