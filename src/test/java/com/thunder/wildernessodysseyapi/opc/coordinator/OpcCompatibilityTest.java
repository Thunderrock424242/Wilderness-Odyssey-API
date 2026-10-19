package com.thunder.wildernessodysseyapi.opc.coordinator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpcCompatibilityTest {
    @Test
    void opcProvidesThePolicyOwnerWithoutRemovingTheLegacyEntryPoint() {
        assertDoesNotThrow(() -> Class.forName(
                "com.thunder.wildernessodysseyapi.opc.core.OdysseyPerformanceCore"));
        assertDoesNotThrow(() -> PerformanceCoordinator.class.getMethod("constrainBudget", long.class));
        assertDoesNotThrow(() -> Class.forName(
                "com.thunder.wildernessodysseyapi.performance.coordinator.PerformanceCoordinator")
                .getMethod("constrainBudget", long.class));
    }

    @Test
    void legacyAliasSharesTheCanonicalOpcSession() throws Exception {
        Class<?> legacy = Class.forName("com.thunder.wildernessodysseyapi.performance.coordinator.PerformanceCoordinator");
        var mode = com.thunder.wildernessodysseyapi.opc.diagnostics.PerformanceDiagnosticsConfig.Mode.AUTO;
        try {
            PerformanceCoordinator.start(new Object(), new CoordinatorConfig.Values(mode, mode, 2, .1, 100, 200, 600, 100, 4096));
            assertEquals("ENTRY", legacy.getMethod("state").invoke(null));
            legacy.getMethod("clear").invoke(null);
            assertEquals("DISABLED", PerformanceCoordinator.state());
        } finally {
            PerformanceCoordinator.clear();
        }
    }
}
