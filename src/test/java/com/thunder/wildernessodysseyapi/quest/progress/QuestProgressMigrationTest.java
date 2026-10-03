package com.thunder.wildernessodysseyapi.quest.progress;

import com.google.gson.JsonArray;
import com.thunder.wildernessodysseyapi.quest.QuestFixtures;
import com.thunder.wildernessodysseyapi.quest.definition.QuestCampaignSnapshot;
import com.thunder.wildernessodysseyapi.quest.definition.QuestDefinitionCodec;
import com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QuestProgressMigrationTest {
    private QuestCampaignSnapshot snapshot(List<QuestSourceDocument> documents) {
        var report = new QuestDefinitionCodec().decode(documents, QuestFixtures.lookup());
        assertTrue(report.accepted(), () -> report.findings().toString());
        return report.snapshot().orElseThrow();
    }

    @Test
    void presentationEditsKeepCountsCompletedRunsAndOriginalEarnedValues() {
        var documents = QuestFixtures.documents();
        var previous = snapshot(documents);
        var json = documents.get(2).content();
        json.addProperty("title", "Edited title");
        json.addProperty("description", "Edited description");
        json.addProperty("definitionVersion", 2);
        json.getAsJsonArray("rewards").get(0).getAsJsonObject().addProperty("count", 2);
        documents.set(2, QuestFixtures.replace(documents.get(2), json));
        var candidate = snapshot(documents);
        var migration = new QuestProgressMigration();
        var report = migration.check(previous, candidate);
        assertTrue(report.compatible(), () -> report.changes().toString());
        var original = QuestProgressCodecTest.progress();
        var result = migration.apply(original, report.preservePlan());
        assertTrue(result.accepted(), result.message());
        var restored = result.progress().orElseThrow();
        assertEquals(original.runs(), restored.runs());
        assertEquals(original.earned(), restored.earned());
    }

    @Test
    void raisedGoalsChangedPredicatesAndRemovedObjectivesNeedExplicitMapping() {
        for (String mutation : List.of("goal", "predicate", "removed")) {
            var documents = QuestFixtures.documents();
            var previous = snapshot(documents);
            var json = documents.get(2).content();
            var objectives = json.getAsJsonArray("objectives");
            switch (mutation) {
                case "goal" -> objectives.get(0).getAsJsonObject().addProperty("goal", 8);
                case "predicate" -> objectives.get(0).getAsJsonObject().getAsJsonArray("items").add("minecraft:spruce_log");
                case "removed" -> objectives.remove(0);
                default -> fail("Unknown mutation");
            }
            documents.set(2, QuestFixtures.replace(documents.get(2), json));
            var report = new QuestProgressMigration().check(previous, snapshot(documents));
            assertFalse(report.compatible(), mutation);
            assertThrows(IllegalStateException.class, report::preservePlan, mutation);
        }
    }

    @Test
    void explicitArchiveRetainsDeletedQuestAndItsEntitlements() {
        var documents = QuestFixtures.documents();
        var previous = snapshot(documents);
        documents.remove(2);
        var chapter = documents.get(1).content();
        chapter.add("quests", new JsonArray());
        documents.set(1, QuestFixtures.replace(documents.get(1), chapter));
        var candidate = snapshot(documents);
        var migration = new QuestProgressMigration();
        var report = migration.check(previous, candidate);
        assertFalse(report.compatible());
        var original = QuestProgressCodecTest.progress();
        var result = migration.apply(original, new QuestProgressMigration.MigrationPlan(previous, candidate,
                Map.of(), Map.of(), report.changes()));
        assertTrue(result.accepted(), result.message());
        var restored = result.progress().orElseThrow();
        assertTrue(restored.runs().values().stream().allMatch(QuestPlayerProgress.Run::archived));
        assertEquals(original.earned(), restored.earned());
        assertNull(restored.tracking());
    }

    @Test
    void approvingAChangedMeaningStillRequiresAMappingAndNeverReopensCompletedRewards() {
        var documents = QuestFixtures.documents();
        var previous = snapshot(documents);
        var json = documents.get(2).content();
        json.getAsJsonArray("objectives").get(0).getAsJsonObject().addProperty("goal", 8);
        documents.set(2, QuestFixtures.replace(documents.get(2), json));
        var candidate = snapshot(documents);
        var migration = new QuestProgressMigration();
        var report = migration.check(previous, candidate);
        var original = QuestProgressCodecTest.progress();
        assertFalse(migration.apply(original, new QuestProgressMigration.MigrationPlan(previous, candidate,
                Map.of(), Map.of(), report.changes())).accepted());
        var key = new QuestProgressMigration.ObjectiveKey(QuestFixtures.id("test:first"), QuestFixtures.id("test:supplies"));
        var result = migration.apply(original, new QuestProgressMigration.MigrationPlan(previous, candidate,
                Map.of(), Map.of(key, key), report.changes()));
        assertTrue(result.accepted(), result.message());
        var restored = result.progress().orElseThrow();
        assertTrue(restored.runs().get(new QuestPlayerProgress.RunKey(QuestFixtures.id("test:first"), 0)).completed());
        assertEquals(2, restored.count(QuestFixtures.id("test:first"), QuestFixtures.id("test:supplies")));
        assertEquals(original.earned(), restored.earned());
    }
}
