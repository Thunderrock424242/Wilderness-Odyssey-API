package com.thunder.wildernessodysseyapi.developmentstudio;

import com.thunder.wildernessodysseyapi.developmentstudio.campus.StudioLocationRegistry;
import com.thunder.wildernessodysseyapi.developmentstudio.module.StudioModuleRegistry;
import com.thunder.wildernessodysseyapi.developmentstudio.module.StudioModuleStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards stable extension ids without claiming deferred gameplay integrations exist. */
class StudioRegistriesTest {

    @Test
    void phaseTwoModulesExposeImplementedAndDeferredStates() {
        assertEquals(StudioModuleStatus.AVAILABLE,
                StudioModuleRegistry.get("locations").orElseThrow().status());
        assertEquals(StudioModuleStatus.AVAILABLE,
                StudioModuleRegistry.get("inspector").orElseThrow().status());
        assertEquals(StudioModuleStatus.AVAILABLE,
                StudioModuleRegistry.get("debug").orElseThrow().status());
        assertEquals(StudioModuleStatus.AVAILABLE,
                StudioModuleRegistry.get("structures").orElseThrow().status());
        assertEquals(StudioModuleStatus.AVAILABLE,
                StudioModuleRegistry.get("entities").orElseThrow().status());
        assertEquals(StudioModuleStatus.AVAILABLE,
                StudioModuleRegistry.get("water").orElseThrow().status());
        assertEquals(StudioModuleStatus.AVAILABLE,
                StudioModuleRegistry.get("ecosystem").orElseThrow().status());
        assertEquals(StudioModuleStatus.AVAILABLE,
                StudioModuleRegistry.get("weather").orElseThrow().status());
        assertEquals(StudioModuleStatus.AVAILABLE,
                StudioModuleRegistry.get("worldgen").orElseThrow().status());
        assertEquals(StudioModuleStatus.DEFERRED,
                StudioModuleRegistry.get("power").orElseThrow().status());
    }

    @Test
    void campusHasUsablePadsAndReservedFutureLocations() {
        assertTrue(StudioLocationRegistry.values().stream()
                .anyMatch(location -> "main_hub".equals(location.id().getPath()) && location.available()));
        assertTrue(StudioLocationRegistry.values().stream()
                .anyMatch(location -> "water_lab".equals(location.id().getPath()) && location.available()));
        assertTrue(StudioLocationRegistry.values().stream()
                .anyMatch(location -> "power_lab".equals(location.id().getPath()) && !location.available()));
        assertFalse(StudioLocationRegistry.values().isEmpty());
    }
}
