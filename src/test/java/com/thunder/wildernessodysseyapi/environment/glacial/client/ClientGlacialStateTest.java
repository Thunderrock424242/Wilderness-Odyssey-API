package com.thunder.wildernessodysseyapi.environment.glacial.client;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Queue;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Exercises queue work without constructing a running client world or renderer. */
class ClientGlacialStateTest {

    @AfterEach
    void clearState() {
        ClientGlacialState.clear(null);
    }

    @Test
    void staleEntriesConsumeTheSamePollingBudgetAsLoadedChunks() throws ReflectiveOperationException {
        Queue<Long> pending = queue();
        for (int x = 0; x < 100; x++) {
            pending.offer(ChunkPos.asLong(x, 0));
        }

        assertNull(ClientGlacialState.pollDirty(null));
        assertEquals(99, pending.size(), "One poll must inspect at most one queue entry");
    }

    @Test
    void loadedWorkRemainsQueuedAfterAStaleEntryConsumesOnePoll() throws ReflectiveOperationException {
        long loaded = ChunkPos.asLong(2, 3);
        ClientGlacialState.track(null, 2, 3);
        keys().add(loaded);
        queue().offer(ChunkPos.asLong(1, 3));
        queue().offer(loaded);

        assertNull(ClientGlacialState.pollDirty(null));
        assertEquals(loaded, ClientGlacialState.pollDirty(null));
        assertNull(ClientGlacialState.pollDirty(null));
    }

    @SuppressWarnings("unchecked")
    private static Queue<Long> queue() throws ReflectiveOperationException {
        return (Queue<Long>) field("DIRTY");
    }

    @SuppressWarnings("unchecked")
    private static Set<Long> keys() throws ReflectiveOperationException {
        return (Set<Long>) field("DIRTY_KEYS");
    }

    private static Object field(String name) throws ReflectiveOperationException {
        Field field = ClientGlacialState.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
}
