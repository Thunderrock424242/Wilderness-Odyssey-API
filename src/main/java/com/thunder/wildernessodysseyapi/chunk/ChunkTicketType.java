package com.thunder.wildernessodysseyapi.chunk;

/**
 * Ticket types for keeping chunks alive.
 */
public enum ChunkTicketType {
    PLAYER(4),
    ENTITY(3),
    STRUCTURE(2),
    REDSTONE(1);

    private final int priority;

    ChunkTicketType(int priority) {
        this.priority = priority;
    }

    public int resolveTtl(ChunkStreamingConfig.ChunkConfigValues config) {
        return switch (this) {
            case PLAYER -> config.playerTicketTtl();
            case ENTITY -> config.entityTicketTtl();
            case REDSTONE -> config.redstoneTicketTtl();
            case STRUCTURE -> config.structureTicketTtl();
        };
    }

    public int priority() {
        return priority;
    }
}
