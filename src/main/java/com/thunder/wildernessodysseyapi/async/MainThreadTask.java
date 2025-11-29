package com.thunder.wildernessodysseyapi.async;

import net.minecraft.server.MinecraftServer;

/**
 * A task that must be executed on the logical server thread.
 */
@FunctionalInterface
public interface MainThreadTask {
    void run(MinecraftServer server);
}
