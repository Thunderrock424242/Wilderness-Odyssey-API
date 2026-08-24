package com.thunder.wildernessodysseyapi.simulation.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimulationEventDispatcherTest {

    @Test
    void dispatchesInStableOrderAndIsolatesOneListenerFailure() {
        SimulationEventDispatcher dispatcher = new SimulationEventDispatcher();
        List<String> order = new ArrayList<>();
        AtomicInteger failures = new AtomicInteger();
        dispatcher.register(id("zeta"), TestEvent.class, event -> order.add("zeta"));
        dispatcher.register(id("broken"), TestEvent.class, event -> {
            order.add("broken");
            throw new IllegalStateException("expected");
        });
        dispatcher.register(id("alpha"), SimulationEvent.class, event -> order.add("alpha"));

        SimulationEventDispatcher.DispatchResult result = dispatcher.dispatch(
                new TestEvent(id("event"), ResourceLocation.withDefaultNamespace("overworld"), BlockPos.ZERO, 10L),
                (listenerId, exception) -> failures.incrementAndGet()
        );

        assertEquals(List.of("alpha", "broken", "zeta"), order);
        assertEquals(2, result.delivered());
        assertEquals(1, result.failed());
        assertEquals(1, failures.get());
    }

    @Test
    void rejectsDuplicateListenerIds() {
        SimulationEventDispatcher dispatcher = new SimulationEventDispatcher();
        dispatcher.register(id("listener"), TestEvent.class, event -> { });

        assertThrows(IllegalArgumentException.class,
                () -> dispatcher.register(id("listener"), SimulationEvent.class, event -> { }));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private record TestEvent(
            ResourceLocation type,
            ResourceLocation dimension,
            BlockPos position,
            long gameTime
    ) implements SimulationEvent {
    }
}
