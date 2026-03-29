package com.thunder.wildernessodysseyapi.bridge;

/**
 * Shared bridge state for enabling/disabling hostile spawning around structure blocks.
 */
public interface StructureBlockHostileSpawnToggleBridge {
    boolean wildernessodysseyapi$isHostileSpawnsDisabled();

    void wildernessodysseyapi$setHostileSpawnsDisabled(boolean disabled);
}
