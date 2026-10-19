package com.thunder.wildernessodysseyapi.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Keeps the shipped performance implementations together and detects stale classes after moves. */
class OpcPackageLayoutTest {
    private static final String ROOT = "com/thunder/wildernessodysseyapi/";
    private static final String LEGACY_COORDINATOR = ROOT + "performance/coordinator/PerformanceCoordinator.class";

    @Test
    void runtimeInfrastructureShipsUnderOpcWithoutDuplicateLegacyImplementations() throws Exception {
        try (JarFile jar = new JarFile(Path.of(System.getProperty("wildernessodysseyapi.jarPath")).toFile())) {
            Set<String> classes = jar.stream().map(entry -> entry.getName())
                    .filter(name -> name.endsWith(".class")).collect(Collectors.toSet());
            for (String implementation : List.of(
                    "opc/core/OdysseyPerformanceCore.class",
                    "opc/async/AsyncTaskManager.class",
                    "opc/dataengine/DataEngine.class",
                    "opc/config/PerformanceServerConfig.class",
                    "opc/saving/SavingCoordinator.class",
                    "opc/diagnostics/PerformanceDiagnostics.class",
                    "opc/diagnostics/gpu/client/GpuProfiler.class",
                    "opc/diagnostics/loading/client/LoadingStallDetector.class",
                    "opc/simulation/core/SimulationEngine.class")) {
                assertTrue(classes.contains(ROOT + implementation), "Missing OPC implementation: " + implementation);
            }
            for (String oldRoot : List.of("performance/", "async/", "dataengine/", "diagnostics/performance/",
                    "saving/coordinator/", "gpuprofiler/", "loading/", "simulation/core/", "simulation/api/")) {
                assertTrue(classes.stream().noneMatch(name -> name.startsWith(ROOT + oldRoot) && !name.equals(LEGACY_COORDINATOR)),
                        "A legacy implementation remains under " + oldRoot);
            }
            assertTrue(classes.contains(LEGACY_COORDINATOR), "Keep the legacy coordinator forwarding entry point");
        }
    }
}
