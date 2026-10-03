package com.thunder.wildernessodysseyapi.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/** Authenticated sender requests only. There is no target player UUID or objective-completion operation. */
public record QuestPlayerRequestPayload(UUID request, UUID session, String hash, long revision, Operation operation,
                                        String quest, String reward, long run) implements CustomPacketPayload {
    public enum Operation { VIEW, TRACK, UNTRACK, CLAIM, RESYNC }
    public static final Type<QuestPlayerRequestPayload> TYPE = new Type<>(ResourceLocation.parse("wildernessodysseyapi:quest_request"));
    public QuestPlayerRequestPayload {
        Objects.requireNonNull(request); Objects.requireNonNull(session); Objects.requireNonNull(operation);
        QuestWire.hash(hash, true); QuestWire.id(quest, true); QuestWire.id(reward, true);
        if (revision < 0 || run < 0 || run > 100_000 || operation == Operation.TRACK && quest.isEmpty()
                || operation == Operation.CLAIM && (quest.isEmpty() || reward.isEmpty())) throw new IllegalArgumentException("Invalid quest request.");
    }
    public static final StreamCodec<FriendlyByteBuf, QuestPlayerRequestPayload> STREAM_CODEC = StreamCodec.of((buffer, value) -> {
        buffer.writeUUID(value.request); buffer.writeUUID(value.session); buffer.writeUtf(value.hash, 64); buffer.writeLong(value.revision);
        buffer.writeEnum(value.operation); buffer.writeUtf(value.quest, 256); buffer.writeUtf(value.reward, 256); buffer.writeLong(value.run);
    }, buffer -> new QuestPlayerRequestPayload(buffer.readUUID(), buffer.readUUID(), buffer.readUtf(64), buffer.readLong(),
            buffer.readEnum(Operation.class), buffer.readUtf(256), buffer.readUtf(256), buffer.readLong()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
