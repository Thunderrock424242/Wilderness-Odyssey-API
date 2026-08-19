package com.thunder.wildernessodysseyapi.dataengine.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.dataengine.queue.UpdatePriority;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Bounded client-bound packet carrying multiple compact Wilderness deltas. */
public record DataPacketBatch(List<DataDelta> entries) implements CustomPacketPayload {
    private static final int HARD_MAXIMUM_ENTRIES = 512;
    private static final int HARD_MAXIMUM_BATCH_BYTES = 1_048_576;

    public static final Type<DataPacketBatch> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "data_engine_batch")
    );
    public static final StreamCodec<FriendlyByteBuf, DataPacketBatch> STREAM_CODEC =
            StreamCodec.of(DataPacketBatch::encode, DataPacketBatch::decode);

    public DataPacketBatch {
        entries = List.copyOf(entries);
        if (entries.isEmpty() || entries.size() > HARD_MAXIMUM_ENTRIES) {
            throw new IllegalArgumentException("Data Engine batch entry count is outside its safety bound");
        }
        int estimatedBytes = 0;
        for (DataDelta entry : entries) {
            estimatedBytes = Math.addExact(estimatedBytes, entry.approximateEncodedBytes());
        }
        if (estimatedBytes > HARD_MAXIMUM_BATCH_BYTES) {
            throw new IllegalArgumentException("Data Engine batch exceeds its hard byte bound");
        }
    }

    public int approximateEncodedBytes() {
        int total = 5;
        for (DataDelta entry : entries) {
            total += entry.approximateEncodedBytes();
        }
        return total;
    }

    private static void encode(FriendlyByteBuf buffer, DataPacketBatch batch) {
        buffer.writeVarInt(batch.entries.size());
        for (DataDelta delta : batch.entries) {
            buffer.writeResourceLocation(delta.systemId());
            buffer.writeVarLong(delta.targetKey());
            buffer.writeVarLong(delta.changedFields());
            buffer.writeByte(delta.priority().ordinal());
            byte[] body = delta.bodyUnsafe();
            buffer.writeVarInt(body.length);
            buffer.writeBytes(body);
        }
    }

    private static DataPacketBatch decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 1 || count > HARD_MAXIMUM_ENTRIES) {
            throw new IllegalArgumentException("Data Engine batch entry count exceeds its bound");
        }
        List<DataDelta> entries = new ArrayList<>(count);
        int totalBodyBytes = 0;
        for (int index = 0; index < count; index++) {
            ResourceLocation systemId = buffer.readResourceLocation();
            long targetKey = buffer.readVarLong();
            long changedFields = buffer.readVarLong();
            int priorityOrdinal = buffer.readUnsignedByte();
            if (priorityOrdinal >= UpdatePriority.values().length) {
                throw new IllegalArgumentException("Unknown Data Engine priority id");
            }
            int bodyLength = buffer.readVarInt();
            if (bodyLength < 0 || bodyLength > DataDelta.MAXIMUM_BODY_BYTES) {
                throw new IllegalArgumentException("Data Engine delta body exceeds its bound");
            }
            totalBodyBytes = Math.addExact(totalBodyBytes, bodyLength);
            if (totalBodyBytes > HARD_MAXIMUM_BATCH_BYTES) {
                throw new IllegalArgumentException("Data Engine batch bodies exceed their combined bound");
            }
            byte[] body = new byte[bodyLength];
            buffer.readBytes(body);
            entries.add(new DataDelta(
                    systemId,
                    targetKey,
                    changedFields,
                    UpdatePriority.values()[priorityOrdinal],
                    body
            ));
        }
        return new DataPacketBatch(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
