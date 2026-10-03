package com.thunder.wildernessodysseyapi.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.UUID;

/** Private authoring frame; server and operator sessions never confer permission without rechecking the sender. */
public record QuestWorkshopPayload(UUID server, UUID editor, UUID request, Kind kind, long revision,
                                   int index, int totalBytes, byte[] bytes) implements CustomPacketPayload {
    public enum Kind { OPEN, SNAPSHOT, EDIT, ACK, CONFLICT, BUSY, DENIED, ERROR, STALE, VALIDATE, VALIDATION, CLOSE }
    public static final int CHUNK = 16 * 1024, MAX_BYTES = 16 * 1024 * 1024;
    public static final Type<QuestWorkshopPayload> TYPE = new Type<>(ResourceLocation.parse("wildernessodysseyapi:quest_workshop"));
    public QuestWorkshopPayload {
        Objects.requireNonNull(server); Objects.requireNonNull(editor); Objects.requireNonNull(request); Objects.requireNonNull(kind);
        int chunks = Math.max(1, (totalBytes + CHUNK - 1) / CHUNK);
        if (revision < 0 || totalBytes < 0 || totalBytes > MAX_BYTES || index < 0 || index >= chunks
                || bytes.length != Math.min(CHUNK, totalBytes - index * CHUNK)) throw new IllegalArgumentException("Invalid Workshop frame bounds.");
        bytes = bytes.clone();
    }
    @Override public byte[] bytes() { return bytes.clone(); }
    public static final StreamCodec<FriendlyByteBuf, QuestWorkshopPayload> STREAM_CODEC = StreamCodec.of((buffer, value) -> {
        buffer.writeUUID(value.server); buffer.writeUUID(value.editor); buffer.writeUUID(value.request); buffer.writeEnum(value.kind);
        buffer.writeLong(value.revision); buffer.writeVarInt(value.index); buffer.writeVarInt(value.totalBytes); buffer.writeByteArray(value.bytes);
    }, buffer -> new QuestWorkshopPayload(buffer.readUUID(), buffer.readUUID(), buffer.readUUID(), buffer.readEnum(Kind.class),
            buffer.readLong(), buffer.readVarInt(), buffer.readVarInt(), buffer.readByteArray(CHUNK)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
