package com.thunder.wildernessodysseyapi.vegetation.network;

import com.thunder.wildernessodysseyapi.dataengine.DataEngine;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/**
 * Selects the bounded Data Engine path for initial vegetation snapshots.
 *
 * <p>This adapter deliberately receives an already selected player and an
 * immutable payload. It does not observe or control chunk lifecycle; the
 * existing synchronization service retains that NeoForge event boundary.</p>
 */
final class ReactiveVegetationSnapshotTransport {
    private ReactiveVegetationSnapshotTransport() {
    }

    /** Queues one initial snapshot, returning false when the caller must send directly. */
    static boolean queueInitial(ServerPlayer player, ReactiveVegetationSyncPayload payload) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(payload, "payload");
        DataEngine engine = DataEngine.get();
        if (!batchingAvailable(
                engine.isRunning(),
                engine.isEnabled(),
                engine.config().networkBatching()
        )) {
            return false;
        }
        return engine.sendDelta(player, ReactiveVegetationDataDeltaCodec.encode(payload));
    }

    static boolean batchingAvailable(
            boolean running,
            boolean engineEnabled,
            boolean networkBatchingEnabled
    ) {
        return running && engineEnabled && networkBatchingEnabled;
    }
}
