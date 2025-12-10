package com.thunder.wildernessodysseyapi.chunk;

/**
 * Ticket types for keeping chunks alive.
 */
public enum ChunkTicketType {
    PLAYER,
    ENTITY,
    REDSTONE,
    STRUCTURE;

    public int resolveTtl(ChunkStreamingConfig.ChunkConfigValues config) {
        return switch (this) {
            case PLAYER -> config.playerTicketTtl();
            case ENTITY -> config.entityTicketTtl();
            case REDSTONE -> config.redstoneTicketTtl();
            case STRUCTURE -> config.structureTicketTtl();
        };
    }
}
