package com.thunder.wildernessodysseyapi.quest.network;

import com.thunder.wildernessodysseyapi.quest.QuestFixtures;
import com.thunder.wildernessodysseyapi.quest.definition.QuestDefinitionCodec;
import com.thunder.wildernessodysseyapi.quest.progress.QuestPlayerProgress;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuestProtocolTest {
    @Test
    void viewCodecRoundTripsAndRejectsOversizedChunksAndAdvertisedTotals() {
        var view = new QuestViewPayload(UUID.randomUUID(), "a".repeat(64), 1, 2, UUID.randomUUID(), 0, 1, 3, new byte[]{1, 2, 3});
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            QuestViewPayload.STREAM_CODEC.encode(buffer, view);
            assertArrayEquals(view.bytes(), QuestViewPayload.STREAM_CODEC.decode(buffer).bytes());
        } finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class, () -> new QuestViewPayload(view.session(), view.hash(), 1, 2, view.transfer(), 0, 513, 16 * 1024 * 1024 + 1, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new QuestViewPayload(view.session(), view.hash(), 1, 2, view.transfer(), 0, 1, 32769, new byte[32769]));
        var malicious = new FriendlyByteBuf(Unpooled.buffer());
        try {
            malicious.writeUUID(view.session()); malicious.writeUtf(view.hash(), 64); malicious.writeLong(1); malicious.writeLong(2);
            malicious.writeUUID(view.transfer()); malicious.writeVarInt(0); malicious.writeVarInt(1); malicious.writeVarInt(32769); malicious.writeVarInt(Integer.MAX_VALUE);
            assertThrows(RuntimeException.class, () -> QuestViewPayload.STREAM_CODEC.decode(malicious));
        } finally { malicious.release(); }
    }

    @Test
    void filteredProjectionCannotExposeAHiddenQuestBeforeEligibility() {
        var documents = QuestFixtures.documents();
        var json = documents.get(2).content(); json.addProperty("hidden", true);
        // A hidden quest with no prerequisites is already eligible and intentionally discoverable.
        documents.set(2, QuestFixtures.replace(documents.get(2), json));
        var snapshot = new QuestDefinitionCodec().decode(documents, QuestFixtures.lookup()).snapshot().orElseThrow();
        var view = QuestViewService.project(snapshot, QuestPlayerProgress.empty(UUID.randomUUID()), Map.of());
        assertFalse(view.has("sources")); assertFalse(view.has("sourcePack")); assertFalse(view.has("worldId"));
        assertFalse(view.toString().contains("test:beacon"), "Private custom producer bindings belong on the server");
        assertEquals(1, view.getAsJsonArray("quests").size());
    }

    @Test
    void requestsHaveNoCompletionOrPlayerTargetOperationAndBoundTheirText() {
        assertArrayEquals(new QuestPlayerRequestPayload.Operation[]{QuestPlayerRequestPayload.Operation.VIEW, QuestPlayerRequestPayload.Operation.TRACK,
                QuestPlayerRequestPayload.Operation.UNTRACK, QuestPlayerRequestPayload.Operation.CLAIM, QuestPlayerRequestPayload.Operation.RESYNC}, QuestPlayerRequestPayload.Operation.values());
        assertThrows(IllegalArgumentException.class, () -> new QuestPlayerRequestPayload(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(65), 0,
                QuestPlayerRequestPayload.Operation.CLAIM, "test:quest", "test:reward", 0));
    }
    @Test
    void lockedHiddenTextNeverAppearsInProjectionOrTrackingAuthorization() {
        var documents = QuestFixtures.documents();
        var hidden = documents.get(2).content(); hidden.addProperty("hidden", true); hidden.addProperty("title", "Unrevealed secret");
        hidden.add("prerequisites", com.google.gson.JsonParser.parseString("{\"mode\":\"all\",\"quests\":[\"test:prerequisite\"]}"));
        documents.set(2, QuestFixtures.replace(documents.get(2), hidden));
        var prerequisite = hidden.deepCopy(); prerequisite.addProperty("hidden", false); prerequisite.addProperty("title", "Public prerequisite");
        prerequisite.add("prerequisites", com.google.gson.JsonParser.parseString("{\"mode\":\"all\",\"quests\":[]}"));
        documents.add(new com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument(
                com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument.Kind.QUEST, QuestFixtures.id("test:prerequisite"), prerequisite, "fixture"));
        var chapter = documents.get(1).content(); chapter.add("quests", com.google.gson.JsonParser.parseString("[\"test:first\",\"test:prerequisite\"]"));
        documents.set(1, QuestFixtures.replace(documents.get(1), chapter));
        var snapshot = new QuestDefinitionCodec().decode(documents, QuestFixtures.lookup()).snapshot().orElseThrow();
        var progress = QuestPlayerProgress.empty(UUID.randomUUID());
        assertFalse(QuestViewService.disclosed(snapshot, progress, QuestFixtures.id("test:first")));
        var view = QuestViewService.project(snapshot, progress, Map.of());
        assertEquals(1, view.getAsJsonArray("quests").size()); assertFalse(view.toString().contains("Unrevealed secret"));
    }
}
