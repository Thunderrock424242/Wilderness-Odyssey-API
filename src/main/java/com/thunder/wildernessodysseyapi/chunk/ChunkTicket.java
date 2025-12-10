package com.thunder.wildernessodysseyapi.chunk;

/**
 * A chunk ticket with an expiry tick.
 */
public record ChunkTicket(ChunkTicketType type, long expiryTick) {
    public boolean isExpired(long currentTick) {
        return currentTick >= expiryTick;
    }
}
