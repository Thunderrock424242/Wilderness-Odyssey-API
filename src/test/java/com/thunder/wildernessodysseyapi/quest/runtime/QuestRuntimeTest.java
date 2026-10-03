package com.thunder.wildernessodysseyapi.quest.runtime;

import com.thunder.wildernessodysseyapi.quest.QuestFixtures;
import com.thunder.wildernessodysseyapi.quest.definition.QuestCampaignSnapshot;
import com.thunder.wildernessodysseyapi.quest.definition.QuestDefinitionCodec;
import com.thunder.wildernessodysseyapi.quest.validation.QuestValidationReport;
import com.thunder.wildernessodysseyapi.quest.workshop.QuestPublicationStore;
import com.thunder.wildernessodysseyapi.quest.world.QuestWorldState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuestRuntimeTest {
    private QuestCampaignSnapshot snapshot(String title) {
        var documents = QuestFixtures.documents();
        var json = documents.get(0).content();
        json.addProperty("title", title);
        documents.set(0, QuestFixtures.replace(documents.get(0), json));
        return new QuestDefinitionCodec().decode(documents, QuestFixtures.lookup()).snapshot().orElseThrow();
    }

    @Test
    void validAndInvalidReloadCandidatesCannotReplaceTheActivePublication() {
        UUID session = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        var runtime = new QuestRuntime(session, QuestWorldState.empty(world), state -> fail("Candidate mutated world state."),
                (player, batch) -> fail("Candidate issued gameplay transitions."));
        var initial = snapshot("Initial");
        runtime.activate(new QuestPublicationStore.PublicationCommit(initial, 1, world, session));
        runtime.acceptCandidate(new QuestValidationReport(Optional.of(snapshot("Candidate")), List.of()));
        assertEquals(initial.hash(), runtime.activeSnapshot().orElseThrow().hash());
        runtime.acceptCandidate(new QuestValidationReport(Optional.empty(), List.of(
                new QuestValidationReport.Finding(QuestValidationReport.Severity.ERROR, null, "source", "Invalid candidate"))));
        assertEquals(initial.hash(), runtime.activeSnapshot().orElseThrow().hash());
        assertFalse(runtime.candidateReport().accepted());
        runtime.close();
    }

    @Test
    void wrongWorldOldSessionAndReplayedPublicationCannotActivate() {
        UUID session = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        var runtime = new QuestRuntime(session, QuestWorldState.empty(world), state -> { }, (player, batch) -> { });
        var initial = snapshot("Initial");
        runtime.activate(new QuestPublicationStore.PublicationCommit(initial, 1, UUID.randomUUID(), session));
        runtime.activate(new QuestPublicationStore.PublicationCommit(initial, 1, world, UUID.randomUUID()));
        assertTrue(runtime.activeSnapshot().isEmpty());
        runtime.activate(new QuestPublicationStore.PublicationCommit(initial, 1, world, session));
        runtime.activate(new QuestPublicationStore.PublicationCommit(snapshot("Replay"), 1, world, session));
        assertEquals(initial.hash(), runtime.activeSnapshot().orElseThrow().hash());
        runtime.close();
    }

    @Test
    void callbacksFromAClosedRuntimeCannotPopulateTheNextSession() {
        UUID session = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        var runtime = new QuestRuntime(session, QuestWorldState.empty(world), state -> { }, (player, batch) -> fail("Stopped runtime produced effects."));
        runtime.close();
        runtime.activate(new QuestPublicationStore.PublicationCommit(snapshot("Late"), 1, world, session));
        assertTrue(runtime.activeSnapshot().isEmpty());
    }

    @Test
    void currentAvailabilityControlsPresentationWithoutReplacingDurableIdentity() {
        UUID session = UUID.randomUUID(); UUID world = UUID.randomUUID();
        var runtime = new QuestRuntime(session, QuestWorldState.empty(world), state -> { }, (player, batch) -> { });
        var initial = snapshot("Initial");
        runtime.activate(new QuestPublicationStore.PublicationCommit(initial, 1, world, session));
        assertTrue(runtime.availableSnapshot().isPresent());
        runtime.refreshAvailability(new com.thunder.wildernessodysseyapi.quest.validation.QuestContentLookup() {
            public boolean contains(ContentKind kind, net.minecraft.resources.ResourceLocation id) { return false; }
            public java.util.Set<net.minecraft.resources.ResourceLocation> tagMembers(net.minecraft.resources.ResourceLocation id) { return java.util.Set.of(); }
            public boolean supportsEvent(net.minecraft.resources.ResourceLocation id) { return false; }
            public long generation() { return 8; }
        });
        assertEquals(initial.hash(), runtime.activeSnapshot().orElseThrow().hash());
        assertTrue(runtime.availableSnapshot().isEmpty(), "Unavailable definitions cannot authorize tracking or be presented as available");
        var view = com.thunder.wildernessodysseyapi.quest.network.QuestViewService.project(runtime.availableSnapshot(),
                com.thunder.wildernessodysseyapi.quest.progress.QuestPlayerProgress.empty(UUID.randomUUID()), java.util.Map.of());
        assertTrue(view.getAsJsonArray("quests").isEmpty());
        runtime.refreshAvailability(QuestFixtures.lookup(9));
        assertEquals(initial.hash(), runtime.availableSnapshot().orElseThrow().hash());
        runtime.close();
    }
}
