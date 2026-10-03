package com.thunder.wildernessodysseyapi.quest.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.quest.QuestFixtures;
import com.thunder.wildernessodysseyapi.quest.definition.QuestDefinitionCodec;
import com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuestValidatorTest {
    private final QuestDefinitionCodec codec = new QuestDefinitionCodec();

    @Test
    void rejectsDanglingReferencesAndBothDependencyCycles() {
        for (String mutation : new String[]{"chapter", "prerequisite", "questCycle", "objectiveCycle", "duplicateObjective", "duplicateReward"}) {
            var documents = QuestFixtures.documents();
            var quest = documents.get(2).content();
            switch (mutation) {
                case "chapter" -> quest.addProperty("chapter", "test:missing");
                case "prerequisite" -> quest.getAsJsonObject("prerequisites").add("quests", JsonParser.parseString("[\"test:missing\"]"));
                case "questCycle" -> quest.getAsJsonObject("prerequisites").add("quests", JsonParser.parseString("[\"test:first\"]"));
                case "objectiveCycle" -> quest.getAsJsonArray("objectives").get(2).getAsJsonObject().add("dependsOn", JsonParser.parseString("[\"test:signal\"]"));
                case "duplicateObjective" -> quest.getAsJsonArray("objectives").add(quest.getAsJsonArray("objectives").get(0).deepCopy());
                case "duplicateReward" -> quest.getAsJsonArray("rewards").add(quest.getAsJsonArray("rewards").get(0).deepCopy());
                default -> fail("Unknown fixture mutation");
            }
            documents.set(2, QuestFixtures.replace(documents.get(2), quest));
            var report = codec.decode(documents, QuestFixtures.lookup());
            assertFalse(report.accepted(), mutation);
            assertTrue(report.findings().stream().anyMatch(finding -> finding.severity() == QuestValidationReport.Severity.ERROR), mutation);
        }
    }

    @Test
    void rejectsEmptyTagsAndUnavailableRequiredContent() {
        var documents = QuestFixtures.documents();
        var quest = documents.get(2).content();
        var objective = quest.getAsJsonArray("objectives").get(0).getAsJsonObject();
        objective.add("items", new JsonArray());
        objective.add("tags", JsonParser.parseString("[\"test:missing_tag\"]"));
        documents.set(2, QuestFixtures.replace(documents.get(2), quest));
        assertFalse(codec.decode(documents, QuestFixtures.lookup()).accepted());
    }

    @Test
    void unavailableOptionalChapterIsDisabledWithoutDiscardingItsDefinitions() {
        var documents = QuestFixtures.documents();
        var chapter = documents.get(1).content();
        chapter.add("optionalMods", JsonParser.parseString("[\"missing_mod\"]"));
        documents.set(1, QuestFixtures.replace(documents.get(1), chapter));
        var report = codec.decode(documents, QuestFixtures.lookup());
        assertTrue(report.accepted(), () -> report.findings().toString());
        assertTrue(report.snapshot().orElseThrow().chapters().get(QuestFixtures.id("test:start")).disabled());
        assertTrue(report.snapshot().orElseThrow().quests().containsKey(QuestFixtures.id("test:first")));
        chapter.remove("optionalMods");
        chapter.add("requiredMods", JsonParser.parseString("[\"missing_mod\"]"));
        documents.set(1, QuestFixtures.replace(documents.get(1), chapter));
        assertFalse(codec.decode(documents, QuestFixtures.lookup()).accepted());
    }

    @Test
    void differentKindsCanShareAnIdButCannotHideBrokenMembership() {
        var documents = QuestFixtures.documents();
        var chapter = documents.get(1).content();
        chapter.addProperty("campaign", "test:start");
        var campaign = documents.get(0);
        documents.set(0, new QuestSourceDocument(campaign.kind(), QuestFixtures.id("test:start"), campaign.content(), "fixture"));
        documents.set(1, QuestFixtures.replace(documents.get(1), chapter));
        assertTrue(codec.decode(documents, QuestFixtures.lookup()).accepted());
        chapter.add("quests", new JsonArray());
        documents.set(1, QuestFixtures.replace(documents.get(1), chapter));
        assertFalse(codec.decode(documents, QuestFixtures.lookup()).accepted());
    }
}
