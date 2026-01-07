package com.thunder.wildernessodysseyapi.AI.AI_perf;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal per-player and per-world conversation history.
 */
public class MemoryStore {

    private static final int MAX_HISTORY = 20;
    private final Map<String, Map<String, Deque<String>>> worldMessages = new HashMap<>();

    private static String normalizeWorld(String world) {
        return world == null || world.isBlank() ? "default" : world;
    }

    /**
     * Stores a player message.
     *
     * @param world   world or save identifier
     * @param player  player name
     * @param message message text
     */
    public void addPlayerMessage(String world, String player, String message) {
        String worldKey = normalizeWorld(world);
        Map<String, Deque<String>> worldBucket = worldMessages.computeIfAbsent(worldKey, w -> new HashMap<>());
        Deque<String> deque = worldBucket.computeIfAbsent(player, p -> new ArrayDeque<>());
        if (deque.size() >= MAX_HISTORY) {
            deque.removeFirst();
        }
        deque.addLast("Player: " + message);
    }

    /**
     * Stores an AI reply.
     *
     * @param world   world or save identifier
     * @param player  player name
     * @param speaker AI display name
     * @param message message text
     */
    public void addAiMessage(String world, String player, String speaker, String message) {
        String worldKey = normalizeWorld(world);
        Map<String, Deque<String>> worldBucket = worldMessages.computeIfAbsent(worldKey, w -> new HashMap<>());
        Deque<String> deque = worldBucket.computeIfAbsent(player, p -> new ArrayDeque<>());
        if (deque.size() >= MAX_HISTORY) {
            deque.removeFirst();
        }
        String name = speaker == null || speaker.isBlank() ? "Atlas" : speaker.trim();
        deque.addLast(name + ": " + message);
    }

    /**
     * Stores an AI reply.
     *
     * @param world   world or save identifier
     * @param player  player name
     * @param message message text
     */
    public void addAiMessage(String world, String player, String message) {
        addAiMessage(world, player, "Atlas", message);
    }

    /**
     * Stores a player message.
     *
     * @param world   world or save identifier
     * @param player  player name
     * @param message message text
     */
    public void addMessage(String world, String player, String message) {
        addPlayerMessage(world, player, message);
    }

    /**
     * Returns a newline-separated view of recent messages.
     *
     * @param world  world or save identifier
     * @param player player name
     * @return context string or empty if none
     */
    public String getRecentContext(String world, String player) {
        String worldKey = normalizeWorld(world);
        Map<String, Deque<String>> worldBucket = worldMessages.get(worldKey);
        if (worldBucket == null) {
            return "";
        }
        Deque<String> deque = worldBucket.get(player);
        if (deque == null) {
            return "";
        }
        return String.join("\n", deque);
    }
}
