package com.thunder.wildernessodysseyapi.simulation.core;

import com.thunder.wildernessodysseyapi.simulation.api.SimulationContext;
import com.thunder.wildernessodysseyapi.simulation.api.SimulationSystem;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulationCoordinatorTest {

    @Test
    void isolatesRelevanceAndUpdateFailuresWhileContinuingThePass() {
        AtomicInteger successfulUpdates = new AtomicInteger();
        AtomicInteger observedFailures = new AtomicInteger();
        List<SimulationSystem> systems = List.of(
                system("disabled", false, context -> { }),
                new TestSystem("relevance_failure", true, context -> { }) {
                    @Override
                    public boolean shouldUpdate(SimulationContext context) {
                        throw new IllegalStateException("expected relevance failure");
                    }
                },
                system("update_failure", true, context -> {
                    throw new IllegalStateException("expected update failure");
                }),
                system("healthy", true, context -> successfulUpdates.incrementAndGet())
        );
        SimulationCoordinator.SystemObserver observer = new SimulationCoordinator.SystemObserver() {
            @Override
            public void onSystemFailure(SimulationSystem system, String phase, Exception exception) {
                observedFailures.incrementAndGet();
            }
        };

        SimulationCoordinator.SystemDispatchReport report = SimulationCoordinator.dispatchSystems(
                systems,
                null,
                observer
        );

        assertEquals(3, report.enabled());
        assertEquals(1, report.executed());
        assertEquals(1, report.skipped());
        assertEquals(2, report.failures());
        assertEquals(1, successfulUpdates.get());
        assertEquals(2, observedFailures.get());
    }

    private static SimulationSystem system(
            String path,
            boolean enabled,
            ThrowingUpdate update
    ) {
        return new TestSystem(path, enabled, update);
    }

    private static class TestSystem implements SimulationSystem {
        private final ResourceLocation id;
        private final boolean enabled;
        private final ThrowingUpdate update;

        private TestSystem(String path, boolean enabled, ThrowingUpdate update) {
            this.id = ResourceLocation.fromNamespaceAndPath("test", path);
            this.enabled = enabled;
            this.update = update;
        }

        @Override
        public ResourceLocation id() {
            return id;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void update(SimulationContext context) throws Exception {
            update.run(context);
        }
    }

    @FunctionalInterface
    private interface ThrowingUpdate {
        void run(SimulationContext context) throws Exception;
    }
}
