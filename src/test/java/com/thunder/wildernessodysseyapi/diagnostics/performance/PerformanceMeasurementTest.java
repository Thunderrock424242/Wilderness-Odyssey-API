package com.thunder.wildernessodysseyapi.diagnostics.performance;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceMeasurementTest {
    @Test
    void preservesTheOriginalResultAndRunsTheOriginalOnce() {
        var calls = new AtomicInteger();
        long[] clock = {10};
        long[] duration = {-1};
        String result = PerformanceMeasurement.call(() -> clock[0]++, () -> {
            calls.incrementAndGet(); return "result";
        }, (nanos, success) -> { assertTrue(success); duration[0] = nanos; });
        assertEquals("result", result);
        assertEquals(1, calls.get());
        assertEquals(1, duration[0]);
    }

    @Test
    void preservesOriginalExceptionsWhenTheObserverAlsoFails() {
        var original = new IllegalArgumentException("original failure");
        var thrown = assertThrows(IllegalArgumentException.class, () -> PerformanceMeasurement.call(() -> 1,
                () -> { throw original; }, (nanos, success) -> { throw new IllegalStateException("observer failure"); }));
        assertSame(original, thrown);
    }

    @Test
    void unavailableClockDoesNotPreventTheOriginalOperation() {
        assertEquals(7, PerformanceMeasurement.call(() -> { throw new UnsupportedOperationException(); },
                () -> 7, (nanos, success) -> fail("No timing should be published")));
    }
}
