package com.thunder.wildernessodysseyapi.opc.async;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class ExecutorShutdownTest {
    @Test
    void allPoolsStartDrainingBeforeWaitingAndShareOneDeadline() {
        AtomicLong clock = new AtomicLong();
        var pools = List.of(new Pool(clock), new Pool(clock), new Pool(clock));
        pools.forEach(pool -> pool.beforeWait = () -> assertTrue(pools.stream().allMatch(Pool::isShutdown)));
        ExecutorShutdown.drain(pools, 4_000_000_000L, clock::get);
        assertEquals(4_000_000_000L, clock.get());
        assertEquals(4_000_000_000L, pools.getFirst().waitNanos);
        assertEquals(0, pools.get(1).waitNanos);
        assertTrue(pools.stream().allMatch(pool -> pool.interrupted));
    }

    private static class Pool extends AbstractExecutorService {
        private final AtomicLong clock;
        private boolean shutdown;
        private boolean interrupted;
        private long waitNanos;
        private Runnable beforeWait = () -> { };
        private Pool(AtomicLong clock) { this.clock = clock; }
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() { interrupted = true; return List.of(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) {
            beforeWait.run(); waitNanos = unit.toNanos(timeout); clock.addAndGet(waitNanos); return false;
        }
        @Override public void execute(Runnable command) { throw new UnsupportedOperationException(); }
    }
}
