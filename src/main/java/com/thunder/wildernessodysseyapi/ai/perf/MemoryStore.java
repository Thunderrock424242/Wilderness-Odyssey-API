package com.thunder.wildernessodysseyapi.ai.perf;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal per-player and per-world conversation history.
 */
public class MemoryStore {

    private static final int MAX_HISTORY = 20;
    private final Map<String, Map<String, Deque<ConversationMessage>>> worldMessages = new HashMap<>();

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
    public synchronized void addPlayerMessage(String world, String player, String message) {
        addMessage(world, player, new ConversationMessage(Role.PLAYER, "Player", safeText(message)));
    }

    /**
     * Stores an AI reply.
     *
     * @param world   world or save identifier
     * @param player  player name
     * @param speaker AI display name
     * @param message message text
     */
    public synchronized void addAiMessage(String world, String player, String speaker, String message) {
        String name = speaker == null || speaker.isBlank() ? "Atlas" : speaker.trim();
        addMessage(world, player, new ConversationMessage(Role.ASSISTANT, name, safeText(message)));
    }

    /**
     * Stores an AI reply.
     *
     * @param world   world or save identifier
     * @param player  player name
     * @param message message text
     */
    public synchronized void addAiMessage(String world, String player, String message) {
        addAiMessage(world, player, "Atlas", message);
    }

    /**
     * Stores a player message.
     *
     * @param world   world or save identifier
     * @param player  player name
     * @param message message text
     */
    public synchronized void addMessage(String world, String player, String message) {
        addPlayerMessage(world, player, message);
    }

    /**
     * Returns a newline-separated view of recent messages.
     *
     * @param world  world or save identifier
     * @param player player name
     * @return context string or empty if none
     */
    public synchronized String getRecentContext(String world, String player) {
        List<String> lines = new ArrayList<>();
        for (ConversationMessage message : getRecentMessages(world, player, MAX_HISTORY)) {
            lines.add(message.displayLine());
        }
        return String.join("\n", lines);
    }

    /**
     * Returns at most {@code limit} recent messages in chronological order.
     *
     * <p>The returned copy can be safely handed to an asynchronous model
     * provider without exposing this store's mutable deques.</p>
     */
    public synchronized List<ConversationMessage> getRecentMessages(String world, String player, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        String worldKey = normalizeWorld(world);
        Map<String, Deque<ConversationMessage>> worldBucket = worldMessages.get(worldKey);
        if (worldBucket == null) {
            return List.of();
        }
        String playerKey = player == null || player.isBlank() ? "player" : player;
        Deque<ConversationMessage> deque = worldBucket.get(playerKey);
        if (deque == null) {
            return List.of();
        }
        int skip = Math.max(0, deque.size() - Math.min(limit, MAX_HISTORY));
        List<ConversationMessage> result = new ArrayList<>(Math.min(limit, deque.size()));
        int index = 0;
        for (ConversationMessage message : deque) {
            if (index++ >= skip) {
                result.add(message);
            }
        }
        return List.copyOf(result);
    }

    private void addMessage(String world, String player, ConversationMessage message) {
        String worldKey = normalizeWorld(world);
        String playerKey = player == null || player.isBlank() ? "player" : player;
        Map<String, Deque<ConversationMessage>> worldBucket = worldMessages.computeIfAbsent(worldKey, ignored -> new HashMap<>());
        Deque<ConversationMessage> deque = worldBucket.computeIfAbsent(playerKey, ignored -> new ArrayDeque<>());
        if (deque.size() >= MAX_HISTORY) {
            deque.removeFirst();
        }
        deque.addLast(message);
    }

    private static String safeText(String message) {
        return message == null ? "" : message.trim();
    }

    /** Identifies whether a stored line came from the player or A.E.T.H.E.R. */
    public enum Role {
        PLAYER,
        ASSISTANT
    }

    /** Immutable conversation data suitable for a background model request. */
    public record ConversationMessage(Role role, String speaker, String text) {
        public ConversationMessage {
            role = role == null ? Role.PLAYER : role;
            speaker = speaker == null || speaker.isBlank() ? "Player" : speaker.trim();
            text = text == null ? "" : text.trim();
        }

        /** Returns the legacy readable transcript representation. */
        public String displayLine() {
            return speaker + ": " + text;
        }
    }
}
