package com.thunder.wildernessodysseyapi.quest.runtime;

import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.quest.QuestFixtures;
import com.thunder.wildernessodysseyapi.quest.definition.QuestDefinitionCodec;
import com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument;
import com.thunder.wildernessodysseyapi.quest.objective.ItemObservation;
import com.thunder.wildernessodysseyapi.quest.objective.QuestEvent;
import com.thunder.wildernessodysseyapi.quest.progress.QuestPlayerProgress;
import com.thunder.wildernessodysseyapi.quest.world.QuestWorldState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuestObjectiveEngineTest {
    private final UUID actor = UUID.randomUUID();
    private final UUID session = UUID.randomUUID();
    private final QuestWorldState world = QuestWorldState.empty(UUID.randomUUID());

    private QuestEvent event(QuestEvent.Evidence evidence) {
        return new QuestEvent(session, UUID.randomUUID(), actor, 100, evidence);
    }

    private QuestObjectiveEngine engine() {
        return QuestObjectiveEngine.compile(new QuestDefinitionCodec().decode(QuestFixtures.documents(), QuestFixtures.lookup()).snapshot().orElseThrow());
    }

    private ItemObservation inventory(long count) {
        return new ItemObservation(List.of(new ItemObservation.Stack(QuestFixtures.id("minecraft:oak_log"), count,
                Set.of(QuestFixtures.id("minecraft:logs")))));
    }

    @Test
    void unionPossessionCountsEachStackOnceAndLatchesAfterItemsAreSpent() {
        var engine = engine();
        var state = QuestPlayerProgress.empty(actor);
        state = engine.apply(state, world, event(new QuestEvent.Possession(inventory(3)))).player();
        assertEquals(3, state.count(QuestFixtures.id("test:first"), QuestFixtures.id("test:supplies")));
        state = engine.apply(state, world, event(new QuestEvent.Possession(inventory(4)))).player();
        assertEquals(4, state.count(QuestFixtures.id("test:first"), QuestFixtures.id("test:supplies")));
        state = engine.apply(state, world, event(new QuestEvent.Possession(inventory(0)))).player();
        assertEquals(4, state.count(QuestFixtures.id("test:first"), QuestFixtures.id("test:supplies")));
    }

    @Test
    void sequentialCustomEvidenceDeduplicatesOccurrencesButCountsDistinctSameTickEvents() {
        var engine = engine();
        var custom = new QuestEvent.CustomEvent(QuestFixtures.id("test:beacon"), 1);
        var state = engine.apply(QuestPlayerProgress.empty(actor), world, event(custom)).player();
        assertEquals(0, state.count(QuestFixtures.id("test:first"), QuestFixtures.id("test:signal")));
        state = engine.apply(state, world, event(new QuestEvent.Lore(QuestFixtures.id("wildernessodysseyapi:lore_001")))).player();
        var occurrence = event(custom);
        state = engine.apply(state, world, occurrence).player();
        assertEquals(1, state.count(QuestFixtures.id("test:first"), QuestFixtures.id("test:signal")));
        assertSame(state, engine.apply(state, world, occurrence).player());
        state = engine.apply(state, world, event(new QuestEvent.CustomEvent(QuestFixtures.id("test:beacon"), Long.MAX_VALUE))).player();
        assertEquals(2, state.count(QuestFixtures.id("test:first"), QuestFixtures.id("test:signal")));
        assertEquals(2, engine.apply(state, world, event(new QuestEvent.CustomEvent(QuestFixtures.id("test:beacon"), Long.MAX_VALUE)))
                .player().count(QuestFixtures.id("test:first"), QuestFixtures.id("test:signal")));
    }

    @Test
    void confirmedPresenceCompletesOnceAndEarnsOriginalRewardsOnce() {
        var engine = engine();
        var state = QuestPlayerProgress.empty(actor);
        for (var evidence : List.<QuestEvent.Evidence>of(new QuestEvent.Possession(inventory(4)),
                new QuestEvent.Dimension(QuestFixtures.id("minecraft:the_nether")),
                new QuestEvent.Lore(QuestFixtures.id("wildernessodysseyapi:lore_001")))) {
            state = engine.apply(state, world, event(evidence)).player();
        }
        var completed = engine.apply(state, world, event(new QuestEvent.CustomEvent(QuestFixtures.id("test:beacon"), Long.MAX_VALUE)));
        assertTrue(completed.player().completed(QuestFixtures.id("test:first")));
        assertEquals(3, completed.earned().size());
        assertEquals(1, completed.questCompletions().size());
        var retry = engine.apply(completed.player(), world, event(new QuestEvent.CustomEvent(QuestFixtures.id("test:beacon"), 2)));
        assertTrue(retry.earned().isEmpty());
        assertTrue(retry.questCompletions().isEmpty());
        assertEquals(completed.player().earned(), retry.player().earned());
    }

    @Test
    void prerequisitesUseAllOrAnyAndSeedCurrentPossessionWhenEligibilityChanges() {
        for (String mode : List.of("all", "any")) {
            var engine = prerequisiteEngine(mode);
            var state = engine.apply(QuestPlayerProgress.empty(actor), world, event(new QuestEvent.Possession(inventory(4)))).player();
            assertEquals(0, state.count(QuestFixtures.id("test:second"), QuestFixtures.id("test:supplies")));
            state = engine.apply(state, world, event(new QuestEvent.CustomEvent(QuestFixtures.id("test:beacon"), 1))).player();
            assertTrue(state.completed(QuestFixtures.id("test:first")));
            assertFalse(state.completed(QuestFixtures.id("test:second")), "A non-inventory event must not reuse a previous possession sample");
            state = engine.apply(state, world, event(new QuestEvent.Possession(inventory(4)))).player();
            assertEquals(mode.equals("any"), state.completed(QuestFixtures.id("test:second")));
        }
    }

    @Test
    void requeuedAnyPrerequisiteQuestConsumesOneCustomOccurrenceAtMostOnce() {
        for (int variant = 0; variant < 32; variant++) {
            var documents = QuestFixtures.documents();
            String dependentId = "test:dependent_" + variant;
            var first = documents.get(2).content();
            first.add("objectives", JsonParser.parseString("[{\"id\":\"test:event\",\"type\":\"custom_event\",\"producer\":\"test:beacon\"}]"));
            documents.set(2, QuestFixtures.replace(documents.get(2), first));
            var completedPrerequisite = first.deepCopy();
            documents.add(new QuestSourceDocument(QuestSourceDocument.Kind.QUEST, QuestFixtures.id("test:previous"), completedPrerequisite, "fixture"));
            var dependent = first.deepCopy();
            dependent.add("objectives", JsonParser.parseString("[{\"id\":\"test:event\",\"type\":\"custom_event\",\"producer\":\"test:beacon\",\"goal\":3}]"));
            dependent.add("prerequisites", JsonParser.parseString("{\"mode\":\"any\",\"quests\":[\"test:first\",\"test:previous\"]}"));
            documents.add(new QuestSourceDocument(QuestSourceDocument.Kind.QUEST, QuestFixtures.id(dependentId), dependent, "fixture"));
            var chapter = documents.get(1).content();
            chapter.add("quests", JsonParser.parseString("[\"test:first\",\"test:previous\",\"" + dependentId + "\"]"));
            documents.set(1, QuestFixtures.replace(documents.get(1), chapter));
            var snapshot = new QuestDefinitionCodec().decode(documents, QuestFixtures.lookup()).snapshot().orElseThrow();
            var initial = new QuestPlayerProgress(actor, 1, java.util.Map.of(
                    new QuestPlayerProgress.RunKey(QuestFixtures.id("test:previous"), 0),
                    new QuestPlayerProgress.Run(1, snapshot.hash(), java.util.Map.of(QuestFixtures.id("test:event"), 1L), true, 0, false)),
                    java.util.Map.of(), List.of(), null, List.of(), QuestPlayerProgress.Observed.empty());
            var engine = QuestObjectiveEngine.compile(snapshot);
            var once = engine.apply(initial, world, event(new QuestEvent.CustomEvent(QuestFixtures.id("test:beacon"), 1))).player();
            assertEquals(1, once.count(QuestFixtures.id(dependentId), QuestFixtures.id("test:event")), dependentId);
            assertEquals(2, engine.apply(once, world, event(new QuestEvent.CustomEvent(QuestFixtures.id("test:beacon"), 1)))
                    .player().count(QuestFixtures.id(dependentId), QuestFixtures.id("test:event")), dependentId);
        }
    }

    @Test
    void playersAndActorlessWorldFactsCannotCreditOneAnother() {
        var engine = engine();
        var other = QuestPlayerProgress.empty(UUID.randomUUID());
        assertSame(other, engine.apply(other, world, event(new QuestEvent.Possession(inventory(4)))).player());
        var state = QuestPlayerProgress.empty(actor);
        var shared = new QuestEvent(session, UUID.randomUUID(), null, 100,
                new QuestEvent.SharedFact(QuestFixtures.id("test:portal_open")));
        var result = engine.apply(state, world, shared);
        assertSame(state, result.player());
        assertTrue(result.world().facts().contains(QuestFixtures.id("test:portal_open")));
        assertTrue(result.earned().isEmpty());
        assertEquals(0, other.count(QuestFixtures.id("test:first"), QuestFixtures.id("test:supplies")));
    }

    @Test
    void publicationCannotResetACompletedRunAndOnlyExplicitRepeatChangesItsIdentity() {
        var documents = QuestFixtures.documents();
        var quest = documents.get(2).content();
        quest.addProperty("repeatable", true);
        quest.addProperty("cooldownTicks", 20);
        quest.add("objectives", JsonParser.parseString("[{\"id\":\"test:signal\",\"type\":\"custom_event\",\"producer\":\"test:beacon\"}]"));
        documents.set(2, QuestFixtures.replace(documents.get(2), quest));
        var snapshot = new QuestDefinitionCodec().decode(documents, QuestFixtures.lookup()).snapshot().orElseThrow();
        var engine = QuestObjectiveEngine.compile(snapshot);
        var completed = engine.apply(QuestPlayerProgress.empty(actor), world,
                event(new QuestEvent.CustomEvent(QuestFixtures.id("test:beacon"), 1))).player();
        assertEquals(0, completed.currentRun(QuestFixtures.id("test:first")));
        assertSame(completed, engine.beginRepeat(completed, QuestFixtures.id("test:first"), 119));
        quest.addProperty("title", "A new title");
        quest.addProperty("definitionVersion", 2);
        documents.set(2, QuestFixtures.replace(documents.get(2), quest));
        var republished = QuestObjectiveEngine.compile(new QuestDefinitionCodec().decode(documents, QuestFixtures.lookup()).snapshot().orElseThrow());
        assertTrue(republished.apply(completed, world, event(new QuestEvent.CustomEvent(QuestFixtures.id("test:beacon"), 1))).earned().isEmpty());
        var repeated = republished.beginRepeat(completed, QuestFixtures.id("test:first"), 120);
        assertEquals(1, repeated.currentRun(QuestFixtures.id("test:first")));
        assertFalse(repeated.completed(QuestFixtures.id("test:first")));
        assertEquals(completed.earned(), repeated.earned());
    }

    private QuestObjectiveEngine prerequisiteEngine(String mode) {
        var documents = QuestFixtures.documents();
        var first = documents.get(2).content();
        first.add("objectives", JsonParser.parseString("[{\"id\":\"test:event\",\"type\":\"custom_event\",\"producer\":\"test:beacon\"}]"));
        documents.set(2, QuestFixtures.replace(documents.get(2), first));
        var blocking = first.deepCopy();
        blocking.add("objectives", JsonParser.parseString("[{\"id\":\"test:end\",\"type\":\"dimension\",\"dimension\":\"minecraft:the_end\"}]"));
        documents.add(new QuestSourceDocument(QuestSourceDocument.Kind.QUEST, QuestFixtures.id("test:blocking"), blocking, "fixture"));
        var second = first.deepCopy();
        second.add("prerequisites", JsonParser.parseString("{\"mode\":\"" + mode + "\",\"quests\":[\"test:first\",\"test:blocking\"]}"));
        second.add("objectives", JsonParser.parseString("[{\"id\":\"test:supplies\",\"type\":\"possession\",\"goal\":4,\"items\":[\"minecraft:oak_log\"]}]"));
        documents.add(new QuestSourceDocument(QuestSourceDocument.Kind.QUEST, QuestFixtures.id("test:second"), second, "fixture"));
        var chapter = documents.get(1).content();
        chapter.add("quests", JsonParser.parseString("[\"test:first\",\"test:blocking\",\"test:second\"]"));
        documents.set(1, QuestFixtures.replace(documents.get(1), chapter));
        return QuestObjectiveEngine.compile(new QuestDefinitionCodec().decode(documents, QuestFixtures.lookup()).snapshot().orElseThrow());
    }

    @Test
    void aDependencyCompletedInTheSameInventoryObservationRevisitsAlreadyQueuedDependents() {
        String dependentId = "test:second";
            var documents = QuestFixtures.documents();
            var first = documents.get(2).content();
            first.add("objectives", JsonParser.parseString("[{\"id\":\"test:supplies\",\"type\":\"possession\",\"goal\":4,\"items\":[\"minecraft:spruce_log\"]}]"));
            documents.set(2, QuestFixtures.replace(documents.get(2), first));
            var dependent = first.deepCopy();
            dependent.add("objectives", JsonParser.parseString("[{\"id\":\"test:supplies\",\"type\":\"possession\",\"goal\":4,\"items\":[\"minecraft:oak_log\"]}]"));
            dependent.add("prerequisites", JsonParser.parseString("{\"mode\":\"all\",\"quests\":[\"test:first\"]}"));
            documents.add(new QuestSourceDocument(QuestSourceDocument.Kind.QUEST, QuestFixtures.id(dependentId), dependent, "fixture"));
            var chapter = documents.get(1).content();
            chapter.add("quests", JsonParser.parseString("[\"test:first\",\"" + dependentId + "\"]"));
            documents.set(1, QuestFixtures.replace(documents.get(1), chapter));
            var engine = QuestObjectiveEngine.compile(new QuestDefinitionCodec().decode(documents, QuestFixtures.lookup()).snapshot().orElseThrow());
            var observation = new ItemObservation(List.of(
                    new ItemObservation.Stack(QuestFixtures.id("minecraft:oak_log"), 4, Set.of()),
                    new ItemObservation.Stack(QuestFixtures.id("minecraft:spruce_log"), 4, Set.of())));
            var result = engine.apply(QuestPlayerProgress.empty(actor), world, event(new QuestEvent.Possession(observation)));
            assertTrue(result.player().completed(QuestFixtures.id(dependentId)), dependentId);
            assertEquals(2, result.questCompletions().size());
    }
}
