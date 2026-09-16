package com.thunder.wildernessodysseyapi.ai.story;

import net.minecraft.server.MinecraftServer;

/** Chat is served by the logical server for integrated, LAN and dedicated games. */
public final class AIChatAccessPolicy {
    private AIChatAccessPolicy() {
    }

    /** A client-only connection has no local logical server and cannot use backend credentials. */
    public static boolean isAvailable(MinecraftServer server) {
        return server != null;
    }

    /** Pure policy seam used by offline tests of all three hosting modes. */
    static boolean isAvailable(boolean singleplayer, boolean publishedToLan) {
        return true;
    }
}
