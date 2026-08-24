package com.thunder.wildernessodysseyapi.vegetation.network;

import com.thunder.wildernessodysseyapi.dataengine.DataEngineIds;
import com.thunder.wildernessodysseyapi.dataengine.network.DataDelta;
import com.thunder.wildernessodysseyapi.dataengine.queue.UpdatePriority;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.Objects;

/**
 * Encodes one existing vegetation snapshot inside the Data Engine batch transport.
 *
 * <p>The owning payload codec remains the only field schema. The Data Engine
 * target key combines dimension and chunk coordinates so two worlds cannot
 * coalesce each other's snapshots. Mismatched bodies are rejected before
 * reaching client state.</p>
 */
public final class ReactiveVegetationDataDeltaCodec {
    public static final long COMPLETE_SNAPSHOT_FIELDS = 1L;

    private ReactiveVegetationDataDeltaCodec() {
    }

    /** Creates an immutable, coalescible normal-priority delta for one chunk. */
    public static DataDelta encode(ReactiveVegetationSyncPayload payload) {
        ReactiveVegetationSyncPayload snapshot = Objects.requireNonNull(payload, "payload");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ReactiveVegetationSyncPayload.STREAM_CODEC.encode(buffer, snapshot);
            byte[] body = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), body);
            return new DataDelta(
                    DataEngineIds.REACTIVE_VEGETATION,
                    targetKey(snapshot.dimension(), snapshot.chunkX(), snapshot.chunkZ()),
                    COMPLETE_SNAPSHOT_FIELDS,
                    UpdatePriority.NORMAL,
                    body
            );
        } finally {
            buffer.release();
        }
    }

    /** Decodes and validates one complete vegetation snapshot delta. */
    public static ReactiveVegetationSyncPayload decode(DataDelta delta) {
        DataDelta encoded = Objects.requireNonNull(delta, "delta");
        if (!DataEngineIds.REACTIVE_VEGETATION.equals(encoded.systemId())) {
            throw new IllegalArgumentException("Delta does not belong to reactive vegetation");
        }
        if (encoded.changedFields() != COMPLETE_SNAPSHOT_FIELDS) {
            throw new IllegalArgumentException("Reactive vegetation delta has an unknown field mask");
        }

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(encoded.body()));
        try {
            ReactiveVegetationSyncPayload payload =
                    ReactiveVegetationSyncPayload.STREAM_CODEC.decode(buffer);
            if (buffer.isReadable()) {
                throw new IllegalArgumentException("Reactive vegetation delta contains trailing bytes");
            }
            if (encoded.targetKey() != targetKey(payload.dimension(), payload.chunkX(), payload.chunkZ())) {
                throw new IllegalArgumentException("Reactive vegetation delta target does not match its body");
            }
            return payload;
        } finally {
            buffer.release();
        }
    }

    static long targetKey(ResourceLocation dimension, int chunkX, int chunkZ) {
        long hash = 0xcbf29ce484222325L;
        String dimensionId = dimension.toString();
        for (int index = 0; index < dimensionId.length(); index++) {
            hash ^= dimensionId.charAt(index);
            hash *= 0x100000001b3L;
        }
        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        hash ^= chunkKey;
        hash *= 0x9e3779b97f4a7c15L;
        hash ^= hash >>> 32;
        return hash;
    }
}
