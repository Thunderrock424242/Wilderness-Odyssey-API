package com.thunder.wildernessodysseyapi.diagnostics.performance;

import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Executes the original operation exactly once; optional diagnostics cannot replace its result or exception. */
public final class PerformanceMeasurement {
    private PerformanceMeasurement() { }

    public static <T> T call(LongSupplier clock, Supplier<T> original, BiConsumer<Long, Boolean> sink) {
        long start;
        try {
            start = clock.getAsLong();
        } catch (RuntimeException | LinkageError unavailable) {
            return original.get();
        }
        boolean successful = false;
        try {
            T result = original.get();
            successful = true;
            return result;
        } finally {
            try {
                sink.accept(Math.max(0, clock.getAsLong() - start), successful);
            } catch (RuntimeException | LinkageError unavailable) {
                // Instrumentation must not mask an original persistence/chunk failure.
            }
        }
    }
}
