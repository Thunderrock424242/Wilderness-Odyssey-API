package com.thunder.wildernessodysseyapi.quest.definition;

import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.quest.QuestFixtures;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class QuestDefinitionCodecTest {
    private final QuestDefinitionCodec codec = new QuestDefinitionCodec();

    @Test
    void acceptsTypedFoundationCampaignAndRoundTripsItsCanonicalHash() {
        var report = codec.decode(QuestFixtures.documents(), QuestFixtures.lookup());
        assertTrue(report.accepted(), () -> report.findings().toString());
        var snapshot = report.snapshot().orElseThrow();
        assertEquals(4, snapshot.quests().get(QuestFixtures.id("test:first")).objectives().size());
        assertEquals(3, snapshot.quests().get(QuestFixtures.id("test:first")).rewards().size());
        var restored = codec.decode(codec.encode(snapshot), QuestFixtures.lookup());
        assertTrue(restored.accepted(), () -> restored.findings().toString());
        assertEquals(snapshot.hash(), restored.snapshot().orElseThrow().hash());
    }

    @Test
    void resourceOrderAndObjectKeyOrderDoNotChangePublicationIdentity() {
        var documents = QuestFixtures.documents();
        var first = codec.decode(documents, QuestFixtures.lookup()).snapshot().orElseThrow().hash();
        Collections.reverse(documents);
        var original = documents.getFirst();
        var reordered = JsonParser.parseString("{\"title\":\"Prepare for travel\",\"description\":\"Keep supplies, visit the Nether and recover a record.\"}").getAsJsonObject();
        original.content().entrySet().forEach(entry -> {
            if (!reordered.has(entry.getKey())) {
                reordered.add(entry.getKey(), entry.getValue());
            }
        });
        documents.set(0, QuestFixtures.replace(original, reordered));
        assertEquals(first, codec.decode(documents, QuestFixtures.lookup()).snapshot().orElseThrow().hash());
    }

    @Test
    void rejectsDuplicateSourceIdentityAndFutureSchemas() {
        var documents = QuestFixtures.documents();
        documents.add(documents.getFirst());
        assertFalse(codec.decode(documents, QuestFixtures.lookup()).accepted());
        documents = QuestFixtures.documents();
        var json = documents.getFirst().content();
        json.addProperty("schemaVersion", 2);
        documents.set(0, QuestFixtures.replace(documents.getFirst(), json));
        assertFalse(codec.decode(documents, QuestFixtures.lookup()).accepted());
    }

    @Test
    void rejectsMissingFieldsUnknownProvidersCommandRewardsAndOverflowingGoals() {
        for (String mutation : new String[]{"missingTitle", "unknownProvider", "commandReward", "overflowGoal", "fractionalGoal", "negativeGoal"}) {
            var documents = QuestFixtures.documents();
            var json = documents.get(2).content();
            switch (mutation) {
                case "missingTitle" -> json.remove("title");
                case "unknownProvider" -> json.getAsJsonArray("objectives").get(0).getAsJsonObject().addProperty("type", "client_complete");
                case "commandReward" -> json.getAsJsonArray("rewards").get(0).getAsJsonObject().addProperty("type", "command");
                case "overflowGoal" -> json.getAsJsonArray("objectives").get(0).getAsJsonObject().addProperty("goal", "9223372036854775808");
                case "fractionalGoal" -> json.getAsJsonArray("objectives").get(0).getAsJsonObject().addProperty("goal", 1.5);
                case "negativeGoal" -> json.getAsJsonArray("objectives").get(0).getAsJsonObject().addProperty("goal", -1);
                default -> fail("Unknown fixture mutation");
            }
            documents.set(2, QuestFixtures.replace(documents.get(2), json));
            var report = codec.decode(documents, QuestFixtures.lookup());
            assertFalse(report.accepted(), mutation);
            assertFalse(report.findings().isEmpty(), mutation);
        }
    }

    @Test
    void sourceDocumentsCannotBeMutatedThroughInputOrAccessor() {
        var json = JsonParser.parseString("{\"title\":\"Original\"}").getAsJsonObject();
        var source = new QuestSourceDocument(QuestSourceDocument.Kind.QUEST, QuestFixtures.id("test:q"), json, "pack");
        json.addProperty("title", "Changed");
        source.content().addProperty("title", "Changed again");
        assertEquals("Original", source.content().get("title").getAsString());
    }

    @Test
    void rejectsOversizedMalformedAndDeepInputBeforeBuildingTheModel() {
        byte[] oversized = new byte[256 * 1024 + 1];
        assertThrows(java.io.IOException.class, () -> codec.readBounded(new ByteArrayInputStream(oversized)));
        byte[] deep = ("[".repeat(65) + "0" + "]".repeat(65)).getBytes(StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class, () -> codec.readBounded(new ByteArrayInputStream(deep)));
        assertThrows(java.io.IOException.class, () -> codec.readBounded(new ByteArrayInputStream("{broken".getBytes(StandardCharsets.UTF_8))));
        assertThrows(java.io.IOException.class, () -> codec.decodeSnapshot(new byte[16 * 1024 * 1024 + 1], QuestFixtures.lookup()));
    }

    @Test
    void acceptsExactlyTheSourceByteLimitButRejectsDuplicateFieldsAndTrailingValues() throws Exception {
        String padded = "{}" + " ".repeat(256 * 1024 - 2);
        assertEquals(0, codec.readBounded(new ByteArrayInputStream(padded.getBytes(StandardCharsets.UTF_8))).size());
        for (String malformed : new String[]{"{\"title\":\"A\",\"title\":\"B\"}", "{} {}", "{\"value\":NaN}", "{\"value\":1,}"}) {
            assertThrows(java.io.IOException.class,
                    () -> codec.readBounded(new ByteArrayInputStream(malformed.getBytes(StandardCharsets.UTF_8))), malformed);
        }
    }

    @Test
    void rejectsObjectiveRewardTextAndUnknownFieldLimits() {
        for (String mutation : new String[]{"objectives", "rewards", "description", "unknownField"}) {
            var documents = QuestFixtures.documents();
            var json = documents.get(2).content();
            switch (mutation) {
                case "objectives", "rewards" -> {
                    var array = json.getAsJsonArray(mutation);
                    var value = array.get(0).deepCopy();
                    int limit = mutation.equals("objectives") ? 32 : 16;
                    while (array.size() <= limit) array.add(value.deepCopy());
                }
                case "description" -> json.addProperty("description", "x".repeat(4097));
                case "unknownField" -> json.addProperty("executeCommand", "give @s diamond");
                default -> fail("Unknown mutation");
            }
            documents.set(2, QuestFixtures.replace(documents.get(2), json));
            assertFalse(codec.decode(documents, QuestFixtures.lookup()).accepted(), mutation);
        }
    }

    @Test
    void stopsAggregateInputBeforeConstructingAnOversizedSnapshotEvenWhenSourcesAreInvalid() {
        var documents = QuestFixtures.documents();
        var json = documents.get(2).content();
        json.addProperty("description", "x".repeat(200_000));
        for (int index = 0; index < 90; index++) {
            documents.add(new QuestSourceDocument(QuestSourceDocument.Kind.QUEST,
                    QuestFixtures.id("test:large_" + index), json, "fixture"));
        }
        var report = codec.decode(documents, QuestFixtures.lookup());
        assertFalse(report.accepted());
        assertTrue(report.findings().stream().anyMatch(finding -> finding.fieldPath().equals("resources")
                && finding.message().contains("16 MiB")), () -> report.findings().toString());
    }
}
