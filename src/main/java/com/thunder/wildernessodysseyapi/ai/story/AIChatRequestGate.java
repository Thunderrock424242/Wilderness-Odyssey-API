package com.thunder.wildernessodysseyapi.ai.story;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Bounds pending chats per session and prevents overlapping turns for the same player. */
final class AIChatRequestGate {
    private final int capacity;
    private final Set<UUID> pending = new HashSet<>();

    AIChatRequestGate(int capacity) {
        this.capacity = capacity;
    }

    synchronized boolean acquire(UUID player) {
        return pending.size() < capacity && pending.add(player);
    }

    synchronized void release(UUID player) {
        pending.remove(player);
    }
}
