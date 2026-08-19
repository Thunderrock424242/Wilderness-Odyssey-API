package com.thunder.wildernessodysseyapi.dataengine.debug.client;

import com.thunder.wildernessodysseyapi.dataengine.debug.DataEngineDebugSnapshot;

import java.time.Duration;
import java.util.Optional;

/** CLIENT THREAD ONLY. Latest real server metrics received while the page is subscribed. */
public final class DataEngineDebugClientState {
    private static final long STALE_AFTER_NANOS = Duration.ofSeconds(5).toNanos();

    private static DataEngineDebugSnapshot snapshot;
    private static long receivedNanos;

    private DataEngineDebugClientState() {
    }

    public static void accept(DataEngineDebugSnapshot newSnapshot) {
        snapshot = newSnapshot;
        receivedNanos = System.nanoTime();
    }

    public static Optional<DataEngineDebugSnapshot> current() {
        if (snapshot == null || System.nanoTime() - receivedNanos > STALE_AFTER_NANOS) {
            return Optional.empty();
        }
        return Optional.of(snapshot);
    }

    public static void clear() {
        snapshot = null;
        receivedNanos = 0L;
    }
}
