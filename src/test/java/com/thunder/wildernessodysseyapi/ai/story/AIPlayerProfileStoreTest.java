package com.thunder.wildernessodysseyapi.ai.story;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies bounded, private, player-scoped conversational profile memory. */
class AIPlayerProfileStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void learnsNaturalDisclosuresPerPlayerAndPersistsThem() {
        Path profileFile = temporaryDirectory.resolve("profiles.yaml");
        AIPlayerProfileStore store = new AIPlayerProfileStore(profileFile);

        AIPlayerProfileStore.LearningResult first = store.learn(
                "player-one", "I like building old observatories. How are you?", true, 12);
        store.learn("player-two", "I prefer short answers.", true, 12);

        assertTrue(first.changed());
        assertEquals(List.of("Likes: building old observatories"), store.getMemories("player-one", 12));
        assertEquals(List.of("Prefers: short answers"), store.getMemories("player-two", 12));

        AIPlayerProfileStore reloaded = new AIPlayerProfileStore(profileFile);
        assertEquals(List.of("Likes: building old observatories"), reloaded.getMemories("player-one", 12));
        assertFalse(reloaded.getContextSnippet("player-one", 12).contains("player-two"));
    }

    @Test
    void explicitMemoryCanSetAndReplacePreferredName() {
        AIPlayerProfileStore store = new AIPlayerProfileStore(temporaryDirectory.resolve("profiles.yaml"));

        AIPlayerProfileStore.LearningResult first = store.learn(
                "player", "remember that my name is Mason", true, 12);
        AIPlayerProfileStore.LearningResult replacement = store.learn(
                "player", "call me Pathfinder", true, 12);

        assertTrue(first.explicitRequest());
        assertTrue(first.accepted());
        assertEquals("Preferred name: Mason", first.memory());
        assertEquals("Preferred name: Pathfinder", replacement.memory());
        assertEquals(List.of("Preferred name: Pathfinder"), store.getMemories("player", 12));
    }

    @Test
    void rejectsSensitiveExplicitMemoryAndDoesNotLearnItNaturally() {
        AIPlayerProfileStore store = new AIPlayerProfileStore(temporaryDirectory.resolve("profiles.yaml"));

        AIPlayerProfileStore.LearningResult explicit = store.learn(
                "player", "remember that my password is swordfish", true, 12);
        AIPlayerProfileStore.LearningResult natural = store.learn(
                "player", "I prefer using my access token abc123", true, 12);
        AIPlayerProfileStore.LearningResult contact = store.learn(
                "player", "remember that contact me at mason@example.com", true, 12);

        assertTrue(explicit.explicitRequest());
        assertFalse(explicit.accepted());
        assertFalse(natural.changed());
        assertFalse(contact.accepted());
        assertTrue(store.getMemories("player", 12).isEmpty());
    }

    @Test
    void respectsNaturalLearningToggleAndConfiguredBound() {
        AIPlayerProfileStore store = new AIPlayerProfileStore(temporaryDirectory.resolve("profiles.yaml"));

        store.learn("player", "I like redstone", false, 2);
        store.learn("player", "remember that I like exploration", false, 2);
        store.learn("player", "remember that I prefer detailed answers", false, 2);
        store.learn("player", "remember that my goal is restoring the bunker", false, 2);

        assertEquals(
                List.of("Prefers: detailed answers", "Current goal: restoring the bunker"),
                store.getMemories("player", 2)
        );
    }

    @Test
    void recallAndForgetRequestsAreExplicitAndPlayerScoped() {
        AIPlayerProfileStore store = new AIPlayerProfileStore(temporaryDirectory.resolve("profiles.yaml"));
        store.learn("player-one", "I love exploration", true, 12);
        store.learn("player-two", "I love redstone", true, 12);

        assertTrue(AIPlayerProfileStore.isRecallRequest("What do you remember about me?"));
        assertTrue(AIPlayerProfileStore.isForgetRequest("Forget what you know about me"));
        assertTrue(store.clear("player-one"));
        assertTrue(store.getMemories("player-one", 12).isEmpty());
        assertEquals(List.of("Likes: redstone"), store.getMemories("player-two", 12));
    }
}
