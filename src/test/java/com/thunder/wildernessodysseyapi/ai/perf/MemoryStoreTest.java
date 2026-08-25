package com.thunder.wildernessodysseyapi.ai.perf;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies bounded, immutable conversation snapshots for local model requests. */
class MemoryStoreTest {

    @Test
    void returnsRequestedTailInChronologicalOrder() {
        MemoryStore store = new MemoryStore();
        store.addPlayerMessage("overworld", "Player", "first");
        store.addAiMessage("overworld", "Player", "Aether", "second");
        store.addPlayerMessage("overworld", "Player", "third");

        List<MemoryStore.ConversationMessage> messages =
                store.getRecentMessages("overworld", "Player", 2);

        assertEquals(2, messages.size());
        assertEquals(MemoryStore.Role.ASSISTANT, messages.get(0).role());
        assertEquals("second", messages.get(0).text());
        assertEquals(MemoryStore.Role.PLAYER, messages.get(1).role());
        assertEquals("third", messages.get(1).text());
    }

    @Test
    void retainsOnlyTwentyMostRecentMessages() {
        MemoryStore store = new MemoryStore();
        for (int index = 0; index < 25; index++) {
            store.addPlayerMessage("overworld", "Player", "message-" + index);
        }

        List<MemoryStore.ConversationMessage> messages =
                store.getRecentMessages("overworld", "Player", 20);

        assertEquals(20, messages.size());
        assertEquals("message-5", messages.get(0).text());
        assertEquals("message-24", messages.get(19).text());
    }

    @Test
    void separatesDimensionsAndPlayers() {
        MemoryStore store = new MemoryStore();
        store.addPlayerMessage("overworld", "Alex", "surface");
        store.addPlayerMessage("the_echo", "Alex", "echo");
        store.addPlayerMessage("overworld", "Steve", "other player");

        assertEquals("surface", store.getRecentMessages("overworld", "Alex", 20).getFirst().text());
        assertEquals("echo", store.getRecentMessages("the_echo", "Alex", 20).getFirst().text());
        assertEquals("other player", store.getRecentMessages("overworld", "Steve", 20).getFirst().text());
    }
}
