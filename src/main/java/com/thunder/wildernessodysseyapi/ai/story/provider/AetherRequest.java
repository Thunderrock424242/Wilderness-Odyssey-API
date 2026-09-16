package com.thunder.wildernessodysseyapi.ai.story.provider;

import java.util.List;

/**
 * Immutable wire data captured by the logical server before background work.
 * No live world, player, registry, credentials, or save paths cross this boundary.
 */
public record AetherRequest(String requestId, String serverId, String world, String playerId,
                            String playerName, String speaker, String message,
                            Context context, List<HistoryMessage> history) {
    public AetherRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }

    /** Literal game observations and optionally shared profile notes, never system instructions. */
    public record Context(String dimension, String biome, boolean surface, String activationInterface,
                          List<String> tags, String playerMemory) {
        public Context {
            tags = tags == null ? List.of() : List.copyOf(tags);
            playerMemory = playerMemory == null ? "" : playerMemory;
        }
    }

    /** Prior turns; the current user message appears only in the top-level message field. */
    public record HistoryMessage(String role, String speaker, String text) {
    }
}
