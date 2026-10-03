package com.thunder.wildernessodysseyapi.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/** One bounded chunk of an authorized complete player view, never a definition/source dump. */
public record QuestViewPayload(UUID session, String hash, long publicationRevision, long playerRevision, UUID transfer,
                               int index, int chunks, int totalBytes, byte[] bytes) implements CustomPacketPayload {
    public static final Type<QuestViewPayload> TYPE = new Type<>(ResourceLocation.parse("wildernessodysseyapi:quest_view"));
    public QuestViewPayload {
        Objects.requireNonNull(session); Objects.requireNonNull(transfer); QuestWire.hash(hash, false);
        if (publicationRevision < 1 || playerRevision < 0 || totalBytes < 1 || totalBytes > QuestWire.MAX_VIEW_BYTES
                || chunks != (totalBytes + QuestWire.CHUNK_BYTES - 1) / QuestWire.CHUNK_BYTES || index < 0 || index >= chunks
                || bytes.length != Math.min(QuestWire.CHUNK_BYTES, totalBytes - index * QuestWire.CHUNK_BYTES)) throw new IllegalArgumentException("Invalid quest view bounds.");
        bytes = bytes.clone();
    }
    @Override public byte[] bytes() { return bytes.clone(); }
    public static final StreamCodec<FriendlyByteBuf, QuestViewPayload> STREAM_CODEC = StreamCodec.of((buffer, value) -> {
        buffer.writeUUID(value.session); buffer.writeUtf(value.hash, 64); buffer.writeLong(value.publicationRevision); buffer.writeLong(value.playerRevision);
        buffer.writeUUID(value.transfer); buffer.writeVarInt(value.index); buffer.writeVarInt(value.chunks); buffer.writeVarInt(value.totalBytes); QuestWire.writeChunk(buffer, value.bytes);
    }, buffer -> new QuestViewPayload(buffer.readUUID(), buffer.readUtf(64), buffer.readLong(), buffer.readLong(), buffer.readUUID(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), QuestWire.readChunk(buffer)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
