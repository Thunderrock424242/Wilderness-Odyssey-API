package com.thunder.wildernessodysseyapi.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/** Player-safe status only. Internal paths, draft findings and private receipt metadata are excluded. */
public record QuestRequestResultPayload(UUID request, UUID session, String message) implements CustomPacketPayload {
    public static final Type<QuestRequestResultPayload> TYPE = new Type<>(ResourceLocation.parse("wildernessodysseyapi:quest_result"));
    public QuestRequestResultPayload {
        Objects.requireNonNull(request); Objects.requireNonNull(session);
        if (message == null || message.length() > 512) throw new IllegalArgumentException("Quest result is too long.");
    }
    public static final StreamCodec<FriendlyByteBuf, QuestRequestResultPayload> STREAM_CODEC = StreamCodec.of((buffer, value) -> {
        buffer.writeUUID(value.request); buffer.writeUUID(value.session); buffer.writeUtf(value.message, 512);
    }, buffer -> new QuestRequestResultPayload(buffer.readUUID(), buffer.readUUID(), buffer.readUtf(512)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
