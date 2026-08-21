package com.thunder.wildernessodysseyapi.modconflictchecker;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Owns the lifecycle of the bounded, best-effort compatibility diagnostics.
 *
 * <p>Minecraft registries cannot contain two values with the same key after loading, so walking
 * every registry and recipe at server start only reported successful registrations. Archive-level
 * diagnostics are kept because they can expose genuinely duplicated class or shader paths.</p>
 */
@EventBusSubscriber
public final class RegistryConflictHandler {

    private RegistryConflictHandler() {
        // Event subscriber
    }

    @SubscribeEvent
    public static void onServerStart(ServerStartingEvent event) {
        DedicatedConflictDetector.start();
    }

    /**
     * Stops lifecycle-owned diagnostic threads before the server shuts down or another integrated
     * server starts in the same JVM.
     *
     * @param event server shutdown event
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DedicatedConflictDetector.stop();
    }
}
