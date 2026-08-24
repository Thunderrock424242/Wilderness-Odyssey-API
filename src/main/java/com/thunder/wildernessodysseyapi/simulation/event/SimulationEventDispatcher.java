package com.thunder.wildernessodysseyapi.simulation.event;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Small synchronous dispatcher for typed simulation facts.
 *
 * <p>This is not a scheduler or a replacement for NeoForge events. Dispatch is
 * server-thread only, deterministic by listener ID, and isolates one failing
 * listener so unrelated consumers still observe the fact.</p>
 */
public final class SimulationEventDispatcher {
    private final List<ListenerRegistration<?>> listeners = new ArrayList<>();
    private final Set<ResourceLocation> listenerIds = new HashSet<>();

    /** Registers one typed listener ID exactly once. */
    public synchronized <T extends SimulationEvent> void register(
            ResourceLocation listenerId,
            Class<T> eventType,
            Consumer<T> listener
    ) {
        Objects.requireNonNull(listenerId, "Listener ID is required");
        if (!listenerIds.add(listenerId)) {
            throw new IllegalArgumentException("Simulation event listener is already registered: " + listenerId);
        }
        listeners.add(new ListenerRegistration<>(
                listenerId,
                Objects.requireNonNull(eventType, "Event type is required"),
                Objects.requireNonNull(listener, "Simulation event listener is required")
        ));
        listeners.sort(Comparator.comparing(registration -> registration.id().toString()));
    }

    /** Dispatches one immutable fact and reports isolated listener failures. */
    public DispatchResult dispatch(SimulationEvent event, FailureHandler failureHandler) {
        Objects.requireNonNull(event, "Simulation event is required");
        Objects.requireNonNull(failureHandler, "Failure handler is required");
        List<ListenerRegistration<?>> snapshot;
        synchronized (this) {
            snapshot = List.copyOf(listeners);
        }
        int delivered = 0;
        int failed = 0;
        for (ListenerRegistration<?> registration : snapshot) {
            if (!registration.eventType().isInstance(event)) {
                continue;
            }
            try {
                registration.dispatch(event);
                delivered++;
            } catch (RuntimeException exception) {
                failed++;
                failureHandler.onFailure(registration.id(), exception);
            }
        }
        return new DispatchResult(delivered, failed);
    }

    /** Returns listener IDs in deterministic dispatch order. */
    public synchronized List<ResourceLocation> listenerIds() {
        return listeners.stream().map(ListenerRegistration::id).toList();
    }

    /** Counts successful and failed matching listeners for one event. */
    public record DispatchResult(int delivered, int failed) {
    }

    /** Receives one isolated listener failure for logging and metrics. */
    @FunctionalInterface
    public interface FailureHandler {
        void onFailure(ResourceLocation listenerId, RuntimeException exception);
    }

    private record ListenerRegistration<T extends SimulationEvent>(
            ResourceLocation id,
            Class<T> eventType,
            Consumer<T> listener
    ) {
        private void dispatch(SimulationEvent event) {
            listener.accept(eventType.cast(event));
        }
    }
}
