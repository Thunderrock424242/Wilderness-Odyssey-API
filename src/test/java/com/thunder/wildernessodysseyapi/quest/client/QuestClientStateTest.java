package com.thunder.wildernessodysseyapi.quest.client;

import com.thunder.wildernessodysseyapi.quest.network.QuestProgressDeltaPayload;
import com.thunder.wildernessodysseyapi.quest.network.QuestViewPayload;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuestClientStateTest {
    private byte[] view() { return "{\"quests\":[],\"earned\":[],\"tracking\":\"\"}".getBytes(StandardCharsets.UTF_8); }
    @Test void replayAndRevisionGapsCannotApplyOrNotifyTwice() {
        var state = new QuestClientState(); UUID session = UUID.randomUUID();
        state.accept(new QuestViewPayload(session, "a".repeat(64), 1, 2, UUID.randomUUID(), 0, 1, view().length, view()));
        var delta = new QuestProgressDeltaPayload(session, "a".repeat(64), 1, 2, 3, UUID.randomUUID(), view());
        assertEquals(QuestClientState.ApplyResult.APPLIED, state.accept(delta));
        assertEquals(QuestClientState.ApplyResult.IGNORED, state.accept(delta));
        assertEquals(QuestClientState.ApplyResult.RESYNC, state.accept(new QuestProgressDeltaPayload(session, delta.hash(), 1, 5, 6, UUID.randomUUID(), view())));
        assertEquals(3, state.revision());
        state.clear(); assertTrue(state.view().isEmpty());
        assertEquals(QuestClientState.ApplyResult.RESYNC, state.accept(delta));
    }
    @Test void incompleteTransfersNeverReplaceThePriorCompleteView() {
        var state = new QuestClientState(); UUID session = UUID.randomUUID();
        state.accept(new QuestViewPayload(session, "a".repeat(64), 1, 2, UUID.randomUUID(), 0, 1, view().length, view()));
        state.accept(new QuestViewPayload(session, "b".repeat(64), 2, 2, UUID.randomUUID(), 0, 2, 32769, new byte[32768]));
        assertEquals("a".repeat(64), state.hash());
        assertTrue(state.view().isPresent());
    }
    @Test void nestedProjectionTextAndObjectiveCapsPreserveThePreviousCompleteView() {
        for (String invalid : new String[]{"title", "description", "objectives", "numericString"}) {
            var state = new QuestClientState(); UUID session = UUID.randomUUID();
            state.accept(new QuestViewPayload(session, "a".repeat(64), 1, 2, UUID.randomUUID(), 0, 1, view().length, view()));
            var json = com.google.gson.JsonParser.parseString("""
                    {"quests":[{"id":"test:quest","title":"Title","description":"Description","icon":"minecraft:book","completed":false,"run":0,
                    "objectives":[{"id":"test:objective","goal":4,"count":1}]}],"earned":[],"tracking":""}
                    """).getAsJsonObject();
            var quest = json.getAsJsonArray("quests").get(0).getAsJsonObject();
            switch (invalid) {
                case "title" -> quest.addProperty("title", "x".repeat(257));
                case "description" -> quest.addProperty("description", "x".repeat(4097));
                case "objectives" -> { var objectives = quest.getAsJsonArray("objectives"); for (int i = 0; i < 32; i++) objectives.add(objectives.get(0).deepCopy()); }
                default -> quest.addProperty("run", "1");
            }
            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
            assertEquals(QuestClientState.ApplyResult.RESYNC, state.accept(new QuestProgressDeltaPayload(session, "a".repeat(64), 1, 2, 3, UUID.randomUUID(), bytes)), invalid);
            assertEquals(2, state.revision()); assertTrue(state.view().orElseThrow().getAsJsonArray("quests").isEmpty());
        }
    }
}
