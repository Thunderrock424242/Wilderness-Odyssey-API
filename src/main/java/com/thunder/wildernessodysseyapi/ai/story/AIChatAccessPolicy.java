package com.thunder.wildernessodysseyapi.ai.story;

import net.minecraft.server.MinecraftServer;

/**
 * Defines the hard gameplay boundary for A.E.T.H.E.R chat availability.
 *
 * <p>A.E.T.H.E.R is a personal single-player companion. An integrated world
 * that has been published to LAN is treated as multiplayer immediately, even
 * before another player connects.</p>
 */
public final class AIChatAccessPolicy {

    private AIChatAccessPolicy() {
    }

    /** Returns whether A.E.T.H.E.R may listen to chat on the supplied server. */
    public static boolean isAvailable(MinecraftServer server) {
        return server != null && isAvailable(server.isSingleplayer(), server.isPublished());
    }

    static boolean isAvailable(boolean singleplayer, boolean publishedToLan) {
        return singleplayer && !publishedToLan;
    }
}
