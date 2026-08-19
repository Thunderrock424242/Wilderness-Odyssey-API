package com.thunder.wildernessodysseyapi.dataengine.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * CLIENT THREAD ONLY after payload dispatch. Explicit per-system delta handlers
 * avoid reflection and keep codecs owned by their gameplay/debug subsystem.
 */
public final class DataDeltaHandlerRegistry {
    private static final Map<ResourceLocation, Consumer<DataDelta>> HANDLERS = new HashMap<>();
    private static final Set<ResourceLocation> LOGGED_UNKNOWN = new HashSet<>();

    private DataDeltaHandlerRegistry() {
    }

    /** Registers one client-side handler exactly once. */
    public static synchronized void register(ResourceLocation systemId, Consumer<DataDelta> handler) {
        Objects.requireNonNull(systemId, "System id is required");
        Objects.requireNonNull(handler, "Delta handler is required");
        Consumer<DataDelta> previous = HANDLERS.putIfAbsent(systemId, handler);
        if (previous != null && previous != handler) {
            throw new IllegalArgumentException("A Data Engine delta handler is already registered for " + systemId);
        }
    }

    /** Dispatches a decoded batch on NeoForge's client main thread. */
    public static synchronized void dispatch(DataPacketBatch batch) {
        for (DataDelta delta : batch.entries()) {
            Consumer<DataDelta> handler = HANDLERS.get(delta.systemId());
            if (handler != null) {
                handler.accept(delta);
            } else if (LOGGED_UNKNOWN.add(delta.systemId())) {
                ModConstants.LOGGER.warn("[Data Engine] No client delta handler registered for {}", delta.systemId());
            }
        }
    }
}
