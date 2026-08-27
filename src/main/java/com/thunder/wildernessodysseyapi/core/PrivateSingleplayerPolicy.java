package com.thunder.wildernessodysseyapi.core;

import net.minecraft.server.MinecraftServer;

/**
 * Shared authority for features that are intentionally private to an
 * unpublished integrated world.
 */
public final class PrivateSingleplayerPolicy {
    private PrivateSingleplayerPolicy() {
    }

    /** Returns whether the server is an integrated world that is not published to LAN. */
    public static boolean permits(MinecraftServer server) {
        return server != null && permits(server.isSingleplayer(), server.isPublished());
    }

    static boolean permits(boolean singleplayer, boolean publishedToLan) {
        return singleplayer && !publishedToLan;
    }
}
