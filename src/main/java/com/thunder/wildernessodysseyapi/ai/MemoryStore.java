package com.thunder.wildernessodysseyapi.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal per-player conversation history.
 */
public class MemoryStore {

    private static final int MAX_HISTORY = 20;
    private final Map<String, Deque<String>> messages = new HashMap<>();

    /**
     * Stores a player message.
     *
     * @param player  player name
     * @param message message text
     */
    public void addMessage(String player, String message) {
        Deque<String> deque = messages.computeIfAbsent(player, p -> new ArrayDeque<>());
        if (deque.size() >= MAX_HISTORY) {
            deque.removeFirst();
        }
        deque.addLast(message);
    }

    /**
     * Returns a newline-separated view of recent messages.
     *
     * @param player player name
     * @return context string or empty if none
     */
    public String getRecentContext(String player) {
        Deque<String> deque = messages.get(player);
        if (deque == null) {
            return "";
        }
        return String.join("\n", deque);
    }
}

