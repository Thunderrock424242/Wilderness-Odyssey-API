package com.thunder.wildernessodysseyapi.simulation.core;

import com.thunder.wildernessodysseyapi.simulation.api.SimulationContext;
import com.thunder.wildernessodysseyapi.simulation.api.SimulationSystem;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimulationRegistryTest {

    @Test
    void rejectsDuplicateIdsAndOrdersSystemsDeterministically() {
        SimulationRegistry registry = new SimulationRegistry();
        registry.register(system("zeta", true));
        registry.register(system("alpha", true));

        assertEquals(List.of("alpha", "zeta"), registry.systems().stream()
                .map(system -> system.id().getPath())
                .toList());
        assertThrows(IllegalArgumentException.class, () -> registry.register(system("alpha", true)));
    }

    @Test
    void excludesDisabledSystemsWithoutRemovingTheirRegistration() {
        SimulationRegistry registry = new SimulationRegistry();
        registry.register(system("enabled", true));
        registry.register(system("disabled", false));

        assertEquals(2, registry.systems().size());
        assertEquals(1, registry.enabledSystemCount());
    }

    @Test
    void configurationReloadNotifiesEverySystemAndIsolatesFailure() {
        SimulationRegistry registry = new SimulationRegistry();
        AtomicInteger successfulReloads = new AtomicInteger();
        registry.register(new TestSystem("broken", true) {
            @Override
            public void onConfigurationReload() {
                throw new IllegalStateException("expected");
            }
        });
        registry.register(new TestSystem("healthy", true) {
            @Override
            public void onConfigurationReload() {
                successfulReloads.incrementAndGet();
            }
        });
        List<String> failures = new ArrayList<>();

        registry.onConfigurationReload((id, phase, exception) -> failures.add(id.getPath() + ":" + phase));

        assertEquals(1, successfulReloads.get());
        assertEquals(List.of("broken:configuration reload"), failures);
    }

    private static SimulationSystem system(String path, boolean enabled) {
        return new TestSystem(path, enabled);
    }

    private static class TestSystem implements SimulationSystem {
        private final ResourceLocation id;
        private final boolean enabled;

        private TestSystem(String path, boolean enabled) {
            this.id = ResourceLocation.fromNamespaceAndPath("test", path);
            this.enabled = enabled;
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
        public void update(SimulationContext context) {
        }
    }
}
