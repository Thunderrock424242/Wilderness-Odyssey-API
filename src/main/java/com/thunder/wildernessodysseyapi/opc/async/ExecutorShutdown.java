package com.thunder.wildernessodysseyapi.opc.async;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Starts all accepted work draining together and bounds aggregate current/retired pool waits. */
final class ExecutorShutdown {
    private ExecutorShutdown() { }

    static void drain(Collection<? extends ExecutorService> pools, long graceNanos, LongSupplier clock) {
        pools.forEach(ExecutorService::shutdown);
        long deadline = clock.getAsLong() + Math.max(0, graceNanos);
        boolean interrupted = false;
        for (ExecutorService pool : pools) {
            long remaining = Math.max(0, deadline - clock.getAsLong());
            try {
                if (interrupted || remaining == 0 || !pool.awaitTermination(remaining, TimeUnit.NANOSECONDS)) pool.shutdownNow();
            } catch (InterruptedException interruption) {
                interrupted = true;
                pool.shutdownNow();
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
}
