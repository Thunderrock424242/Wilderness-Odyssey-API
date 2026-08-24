package com.thunder.wildernessodysseyapi.config;

import com.thunder.wildernessodysseyapi.dataengine.config.DataEngineConfig;
import com.thunder.wildernessodysseyapi.performance.background.config.BackgroundEfficiencyConfig;
import com.thunder.wildernessodysseyapi.performance.tickengine.config.TickEngineConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the unified spec retains the three engine-specific compatibility entry points. */
class PerformanceServerConfigTest {

    @Test
    void allPerformanceSectionsShareOneServerSpec() {
        PerformanceServerConfig.initialize();

        assertSame(PerformanceServerConfig.CONFIG_SPEC, BackgroundEfficiencyConfig.CONFIG_SPEC);
        assertSame(PerformanceServerConfig.CONFIG_SPEC, TickEngineConfig.CONFIG_SPEC);
        assertSame(PerformanceServerConfig.CONFIG_SPEC, DataEngineConfig.CONFIG_SPEC);
        assertEquals(
                List.of("performance", "backgroundEfficiency", "enabled"),
                BackgroundEfficiencyConfig.ENABLED.getPath()
        );
        assertEquals(
                List.of("performance", "tickEngine", "enabled"),
                TickEngineConfig.ENABLED.getPath()
        );
        assertEquals(
                List.of("performance", "dataEngine", "enabled"),
                DataEngineConfig.ENABLED.getPath()
        );
        assertTrue(BackgroundEfficiencyConfig.defaults().enabled());
        assertTrue(TickEngineConfig.defaults().enabled());
        assertTrue(DataEngineConfig.defaults().enabled());
    }
}
