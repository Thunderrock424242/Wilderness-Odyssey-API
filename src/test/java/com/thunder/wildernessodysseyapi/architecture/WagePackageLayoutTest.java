package com.thunder.wildernessodysseyapi.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keeps WAGE's shipped implementation together and detects stale classes after package moves. */
class WagePackageLayoutTest {
    private static final String ROOT = "com/thunder/wildernessodysseyapi/";

    @Test
    void renderingInfrastructureShipsUnderWageWithoutDuplicateLegacyImplementations() throws Exception {
        try (JarFile jar = new JarFile(Path.of(System.getProperty("wildernessodysseyapi.jarPath")).toFile())) {
            Set<String> classes = jar.stream().map(entry -> entry.getName())
                    .filter(name -> name.endsWith(".class")).collect(Collectors.toSet());
            for (String implementation : List.of(
                    "wage/EnvironmentState.class",
                    "wage/GPUCapabilities.class",
                    "wage/RenderFrameContext.class",
                    "wage/RenderingQuality.class",
                    "wage/TemporalFrameData.class",
                    "wage/UpscalingProvider.class",
                    "wage/backend/RenderBackend.class",
                    "wage/backend/RenderBackends.class",
                    "wage/backend/opengl/OpenGlRenderBackend.class",
                    "wage/backend/opengl/OpenGlGpuTimer.class",
                    "wage/client/WildernessRenderingFramework.class",
                    "wage/client/diagnostics/WageClientAcceptanceProbe.class",
                    "wage/compat/IrisShaderPackProbe.class",
                    "wage/compat/ShaderPackCompatibility.class",
                    "wage/config/RendererConfig.class",
                    "wage/performance/AdaptiveGpuController.class",
                    "wage/performance/AdaptiveQualityController.class",
                    "wage/performance/FrameTimeWindow.class",
                    "wage/performance/RenderQualityState.class",
                    "wage/performance/RenderWorkloadTimer.class")) {
                assertTrue(classes.contains(ROOT + implementation), "Missing WAGE implementation: " + implementation);
            }
            assertTrue(classes.stream().noneMatch(name -> name.startsWith(ROOT + "rendering/")),
                    "A legacy rendering implementation remains outside WAGE");
            assertFalse(classes.contains(ROOT + "client/diagnostics/WageClientAcceptanceProbe.class"),
                    "The WAGE acceptance probe must move with its feature");
        }
    }
}
