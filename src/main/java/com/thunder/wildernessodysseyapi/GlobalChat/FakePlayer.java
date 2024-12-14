package com.thunder.wildernessodysseyapi.GlobalChat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FakePlayer {
    private static final Set<UUID> connectedPlayers = new HashSet<>();

    public static void connectIfNotConnected(UUID playerUUID) {
        if (connectedPlayers.add(playerUUID)) {
            System.out.println("Fake player connected: " + playerUUID);
        }
    }
}