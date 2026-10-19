package com.thunder.wildernessodysseyapi.wage.config;

import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import com.thunder.wildernessodysseyapi.wage.RenderingQuality;
import com.thunder.wildernessodysseyapi.wage.performance.AdaptiveGpuController;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GpuEngineConfigTest {
    @Test
    void engineIsClientOnlyAndExperimentalBehaviorIsDisabledByDefault() {
        WildernessConfigSpecs.initialize();
        assertSame(WildernessConfigSpecs.clientSpec(), RendererConfig.CONFIG_SPEC);
        assertEquals(List.of("renderer", "gpuEngine", "enabled"), RendererConfig.GPU_ENGINE_ENABLED.getPath());
        assertFalse(RendererConfig.GPU_ENGINE_ENABLED.getDefault());
        assertFalse(RendererConfig.GPU_ENGINE_DIAGNOSTICS.getDefault());
        assertFalse(RendererConfig.gpuEngineSettings().active());
        assertFalse(new RendererConfig.GpuEngineSettings(true, AdaptiveGpuController.Mode.DISABLED, true, true).active());
        assertFalse(new RendererConfig.GpuEngineSettings(true, AdaptiveGpuController.Mode.AUTOMATIC, false, false).active());
        var legacy = new RendererConfig.Settings(false, 16.67, RenderingQuality.LOW, RenderingQuality.HIGH, 5);
        assertEquals(16_670_000, legacy.targetFrameNanos());
    }
}
