package com.thunder.wildernessodysseyapi.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/** Revision transition with a small complete projection. Larger projections use bounded view chunks. */
public record QuestProgressDeltaPayload(UUID session, String hash, long publicationRevision, long baseRevision,
                                        long playerRevision, UUID transition, byte[] bytes) implements CustomPacketPayload {
    public static final Type<QuestProgressDeltaPayload> TYPE = new Type<>(ResourceLocation.parse("wildernessodysseyapi:quest_delta"));
    public QuestProgressDeltaPayload {
        Objects.requireNonNull(session); Objects.requireNonNull(transition); QuestWire.hash(hash, false);
        if (publicationRevision < 1 || baseRevision < 0 || playerRevision <= baseRevision || bytes.length < 1 || bytes.length > QuestWire.CHUNK_BYTES) throw new IllegalArgumentException("Invalid quest delta bounds.");
        bytes = bytes.clone();
    }
    @Override public byte[] bytes() { return bytes.clone(); }
    public static final StreamCodec<FriendlyByteBuf, QuestProgressDeltaPayload> STREAM_CODEC = StreamCodec.of((buffer, value) -> {
        buffer.writeUUID(value.session); buffer.writeUtf(value.hash, 64); buffer.writeLong(value.publicationRevision); buffer.writeLong(value.baseRevision);
        buffer.writeLong(value.playerRevision); buffer.writeUUID(value.transition); QuestWire.writeChunk(buffer, value.bytes);
    }, buffer -> new QuestProgressDeltaPayload(buffer.readUUID(), buffer.readUtf(64), buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readUUID(), QuestWire.readChunk(buffer)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
