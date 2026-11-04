package com.thunder.wildernessodysseyapi.ai.story;

import net.minecraft.server.MinecraftServer;

/** Server-owned chat access; multiplayer requires explicit operator opt-in. */
public final class AIChatAccessPolicy {
    private AIChatAccessPolicy() {
    }

    /** A client-only connection has no local logical server and does not issue backend requests. */
    public static boolean isAvailable(MinecraftServer server) {
        return isAvailable(server, false);
    }

    /** Re-evaluated at chat and delivery time so opening a world to LAN disables default access. */
    public static boolean isAvailable(MinecraftServer server, boolean multiplayerEnabled) {
        return server != null && isAvailable(server.isSingleplayer(), server.isPublished(), multiplayerEnabled);
    }

    /** Pure policy seam used by offline tests of all three hosting modes. */
    static boolean isAvailable(boolean singleplayer, boolean publishedToLan) {
        return isAvailable(singleplayer, publishedToLan, false);
    }

    static boolean isAvailable(boolean singleplayer, boolean publishedToLan, boolean multiplayerEnabled) {
        return multiplayerEnabled || singleplayer && !publishedToLan;
    }
}
