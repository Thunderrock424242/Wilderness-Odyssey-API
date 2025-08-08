package com.thunder.wildernessodysseyapi.api;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Fired when a player enters a cryo tube to begin sleeping.
 * Other mods can listen for this event to trigger cutscenes
 * before the player is awakened.
 */
public class CryoSleepEvent extends Event {
    private final ServerPlayer player;

    public CryoSleepEvent(ServerPlayer player) {
        this.player = player;
    }

    /**
     * The player currently sleeping in the cryo tube.
     */
    public ServerPlayer getPlayer() {
        return player;
    }
}
