package com.thunder.wildernessodysseyapi.dataengine.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataEngineBenchmarkTest {
    @Test
    void controlledWorkloadProducesExpectedCoalescingAndCacheCounts() {
        DataEngineBenchmark.Result result = DataEngineBenchmark.run();

        assertEquals(512, result.submitted());
        assertEquals(448, result.coalesced());
        assertEquals(64, result.processed());
        assertEquals(512, result.cacheHits());
        assertEquals(16, result.cacheMisses());
        assertEquals(4, result.batchCount());
    }
}
