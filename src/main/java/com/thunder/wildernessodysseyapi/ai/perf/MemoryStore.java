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
    private static final int MAX_CONVERSATIONS = 256;
    private boolean closed;
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
        if (closed) {
            return;
        }
        String worldKey = normalizeWorld(world);
        String playerKey = player == null || player.isBlank() ? "player" : player;
        // Bound all retained conversations, including disconnected players and many dimensions.
        Map<String, Deque<ConversationMessage>> existing = worldMessages.get(worldKey);
        if ((existing == null || !existing.containsKey(playerKey))
                && worldMessages.values().stream().mapToInt(Map::size).sum() >= MAX_CONVERSATIONS) {
            var worlds = worldMessages.entrySet().iterator();
            if (worlds.hasNext()) {
                var oldest = worlds.next();
                var players = oldest.getValue().keySet().iterator();
                players.next();
                players.remove();
                if (oldest.getValue().isEmpty()) {
                    worlds.remove();
                }
            }
        }
        // Bound disconnected-player history as well as per-player message counts.
        if (!worldMessages.containsKey(worldKey) && worldMessages.size() >= 64) {
            worldMessages.remove(worldMessages.keySet().iterator().next());
        }
        Map<String, Deque<ConversationMessage>> worldBucket = worldMessages.computeIfAbsent(worldKey, ignored -> new HashMap<>());
        if (!worldBucket.containsKey(playerKey) && worldBucket.size() >= 256) {
            worldBucket.remove(worldBucket.keySet().iterator().next());
        }
        Deque<ConversationMessage> deque = worldBucket.computeIfAbsent(playerKey, ignored -> new ArrayDeque<>());
        if (deque.size() >= MAX_HISTORY) {
            deque.removeFirst();
        }
        deque.addLast(message);
    }

    /** Clears recent dialogue for one save/player without writing it to disk. */
    public synchronized void clearPlayer(String world, String player) {
        Map<String, Deque<ConversationMessage>> bucket = worldMessages.get(normalizeWorld(world));
        if (bucket != null) {
            bucket.remove(player);
            if (bucket.isEmpty()) {
                worldMessages.remove(normalizeWorld(world));
            }
        }
    }

    /** Clears this player's dialogue across dimensions when they request deletion. */
    public synchronized void clearPlayer(String player) {
        worldMessages.values().forEach(bucket -> bucket.remove(player));
        worldMessages.values().removeIf(Map::isEmpty);
    }

    /** Rejects late worker writes after the owning server has shut down. */
    public synchronized void close() {
        closed = true;
        worldMessages.clear();
    }

    /** Clears all transient dialogue when the owning server session stops. */
    public synchronized void clear() {
        worldMessages.clear();
    }

    private static String safeText(String message) {
        return message == null ? "" : message.trim().substring(0, Math.min(4000, message.trim().length()));
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
