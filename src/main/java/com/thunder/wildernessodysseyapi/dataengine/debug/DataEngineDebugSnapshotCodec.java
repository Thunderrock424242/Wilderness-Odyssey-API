package com.thunder.wildernessodysseyapi.dataengine.debug;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

/** Compact binary codec for the debug-only Data Engine metric delta body. */
public final class DataEngineDebugSnapshotCodec {
    private static final int MAXIMUM_BODY_BYTES = 512;

    private DataEngineDebugSnapshotCodec() {
    }

    public static byte[] encode(DataEngineDebugSnapshot snapshot) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer(256));
        try {
            writeNonNegative(buffer, snapshot.serverTick());
            writeNonNegative(buffer, snapshot.tickBudgetNanos());
            writeNonNegative(buffer, snapshot.lastMainThreadNanos());
            writeNonNegative(buffer, snapshot.dirtyEntries());
            writeNonNegative(buffer, snapshot.queuedWork());
            writeNonNegative(buffer, snapshot.processedPerSecond());
            writeNonNegative(buffer, snapshot.coalescedPerSecond());
            writeNonNegative(buffer, snapshot.networkBatches());
            writeNonNegative(buffer, snapshot.networkEntries());
            writeNonNegative(buffer, snapshot.estimatedBytesSent());
            writeNonNegative(buffer, snapshot.cacheHits());
            writeNonNegative(buffer, snapshot.cacheMisses());
            writeNonNegative(buffer, snapshot.cacheEntries());
            writeNonNegative(buffer, snapshot.asyncTasksSubmitted());
            writeNonNegative(buffer, snapshot.asyncTasksCompleted());
            writeNonNegative(buffer, snapshot.asyncTasksRejected());
            buffer.writeVarInt(Math.max(0, snapshot.workerThreads()));
            buffer.writeVarInt(Math.max(0, snapshot.workerQueueLength()));
            writeNonNegative(buffer, snapshot.interestFilteredUpdates());
            writeNonNegative(buffer, snapshot.droppedOrSupersededUpdates());
            buffer.writeBoolean(snapshot.backpressureActive());
            if (buffer.readableBytes() > MAXIMUM_BODY_BYTES) {
                throw new IllegalArgumentException("Data Engine debug snapshot exceeds its body bound");
            }
            byte[] encoded = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), encoded);
            return encoded;
        } finally {
            buffer.release();
        }
    }

    public static DataEngineDebugSnapshot decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0 || encoded.length > MAXIMUM_BODY_BYTES) {
            throw new IllegalArgumentException("Invalid Data Engine debug snapshot body");
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(encoded));
        try {
            DataEngineDebugSnapshot snapshot = new DataEngineDebugSnapshot(
                    readNonNegative(buffer),
                    readNonNegative(buffer),
                    readNonNegative(buffer),
                    readNonNegative(buffer),
                    readNonNegative(buffer),
                    readNonNegative(buffer),
                    readNonNegative(buffer),
                    readNonNegative(buffer),
                    readNonNegative(buffer),
                    readNonNegative(buffer),
                    readNonNegative(buffer),
                    readNonNegative(buffer),
                    readNonNegative(buffer),
                    readNonNegative(buffer),
                    readNonNegative(buffer),
                    readNonNegative(buffer),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    readNonNegative(buffer),
                    readNonNegative(buffer),
                    buffer.readBoolean()
            );
            if (buffer.isReadable()) {
                throw new IllegalArgumentException("Trailing bytes in Data Engine debug snapshot");
            }
            return snapshot;
        } finally {
            buffer.release();
        }
    }

    private static void writeNonNegative(FriendlyByteBuf buffer, long value) {
        buffer.writeVarLong(Math.max(0L, value));
    }

    private static long readNonNegative(FriendlyByteBuf buffer) {
        long value = buffer.readVarLong();
        if (value < 0L) {
            throw new IllegalArgumentException("Negative value in Data Engine debug snapshot");
        }
        return value;
    }
}
