package com.thunder.wildernessodysseyapi.opc.async;

import net.minecraft.server.MinecraftServer;

/**
 * A task that must be executed on the logical server thread.
 */
@FunctionalInterface
public interface MainThreadTask {
    void run(MinecraftServer server);
}
