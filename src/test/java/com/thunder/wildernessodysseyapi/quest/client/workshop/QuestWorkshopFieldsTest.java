package com.thunder.wildernessodysseyapi.quest.client.workshop;

import com.thunder.wildernessodysseyapi.quest.QuestFixtures;
import com.thunder.wildernessodysseyapi.quest.definition.QuestDefinitionCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestWorkshopFieldsTest {
    @Test void malformedEntriesRemainVisibleAndCanBeRemovedWithoutChangingTheModel() {
        for (var collection : java.util.List.of("objectives", "rewards")) {
            var original = new com.google.gson.JsonObject(); var entries = new com.google.gson.JsonArray();
            entries.add(com.google.gson.JsonNull.INSTANCE); entries.add("bad"); entries.add(new com.google.gson.JsonArray()); original.add(collection, entries);
            for (int index = 0; index < 3; index++) {
                assertTrue(QuestWorkshopFields.entry(original, collection, index).isEmpty());
                var repaired = QuestWorkshopFields.removeEntry(original, collection, index);
                assertEquals(2, repaired.getAsJsonArray(collection).size()); assertEquals(3, original.getAsJsonArray(collection).size());
            }
        }
        var original = new com.google.gson.JsonObject(); var entries = new com.google.gson.JsonArray();
        var removed = QuestWorkshopFields.objective("test:removed", "possession", "1", "minecraft:book", ""); entries.add(removed);
        var dependent = QuestWorkshopFields.objective("test:dependent", "possession", "1", "minecraft:book", "test:removed");
        dependent.getAsJsonArray("dependsOn").add(com.google.gson.JsonNull.INSTANCE); dependent.getAsJsonArray("dependsOn").add(new com.google.gson.JsonArray()); entries.add(dependent); original.add("objectives", entries);
        var view = QuestWorkshopFields.entry(original, "objectives", 0).orElseThrow(); view.addProperty("id", "test:edited");
        assertEquals("test:removed", original.getAsJsonArray("objectives").get(0).getAsJsonObject().get("id").getAsString());
        var repaired = QuestWorkshopFields.removeEntry(original, "objectives", 0);
        assertEquals(2, repaired.getAsJsonArray("objectives").get(0).getAsJsonObject().getAsJsonArray("dependsOn").size());
        assertEquals(3, original.getAsJsonArray("objectives").get(1).getAsJsonObject().getAsJsonArray("dependsOn").size());
    }
    @Test void basicChapterAndCampaignFormsPreserveTheirDifferentSchemasAndMemberships() {
        var sources = QuestFixtures.documents();
        for (int index = 0; index < sources.size(); index++) {
            var source = sources.get(index);
            var content = QuestWorkshopFields.basics(source.kind(), source.content(), "Edited title", "Edited description", "minecraft:book");
            sources.set(index, QuestFixtures.replace(source, content));
            if (source.kind() == com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument.Kind.CHAPTER) { assertFalse(content.has("description")); assertFalse(content.has("icon")); }
            if (source.kind() == com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument.Kind.CAMPAIGN) assertFalse(content.has("icon"));
        }
        var report = new QuestDefinitionCodec().decode(sources, QuestFixtures.lookup()); assertTrue(report.accepted(), report.findings().toString());
    }
    @Test void possessionSeparatesItemsAndTagsAndKeepsObjectiveDependencies() {
        var value = QuestWorkshopFields.objective("test:step", "possession", "4", "minecraft:oak_log, #minecraft:logs", "test:previous");
        assertEquals("minecraft:oak_log", value.getAsJsonArray("items").get(0).getAsString());
        assertEquals("minecraft:logs", value.getAsJsonArray("tags").get(0).getAsString());
        assertEquals("test:previous", value.getAsJsonArray("dependsOn").get(0).getAsString());
        assertThrows(IllegalArgumentException.class, () -> QuestWorkshopFields.objective("test:step", "dimension", "2", "minecraft:overworld", ""));
        assertThrows(IllegalArgumentException.class, () -> QuestWorkshopFields.objective("test:step", "possession", "1", "invalid ID", ""));
    }
    @Test void everyTypedFormProducesDefinitionCodecCompatibleContent() {
        var sources = QuestFixtures.documents(); var quest = sources.get(2).content(); var objectives = new com.google.gson.JsonArray();
        objectives.add(QuestWorkshopFields.objective("test:a", "possession", "2", "#minecraft:logs", ""));
        objectives.add(QuestWorkshopFields.objective("test:b", "dimension", "1", "minecraft:the_nether", "test:a"));
        objectives.add(QuestWorkshopFields.objective("test:c", "lore", "1", "wildernessodysseyapi:lore_001", ""));
        objectives.add(QuestWorkshopFields.objective("test:d", "custom_event", "5", "test:beacon", "")); quest.add("objectives", objectives);
        var rewards = new com.google.gson.JsonArray();
        rewards.add(QuestWorkshopFields.reward("test:item", "item", "minecraft:compass", "3"));
        rewards.add(QuestWorkshopFields.reward("test:xp", "xp", "", "50"));
        rewards.add(QuestWorkshopFields.reward("test:lore", "lore", "wildernessodysseyapi:lore_002", "1")); quest.add("rewards", rewards);
        sources.set(2, QuestFixtures.replace(sources.get(2), quest));
        var report = new QuestDefinitionCodec().decode(sources, QuestFixtures.lookup()); assertTrue(report.accepted(), report.findings().toString());
        assertThrows(IllegalArgumentException.class, () -> QuestWorkshopFields.reward("test:r", "item", "minecraft:book", "65"));
        assertThrows(IllegalArgumentException.class, () -> QuestWorkshopFields.reward("test:r", "xp", "", "0"));
    }
}
