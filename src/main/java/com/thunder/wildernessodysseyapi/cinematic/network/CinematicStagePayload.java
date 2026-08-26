package com.thunder.wildernessodysseyapi.cinematic.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Authoritative stage transition for an already-running client cinematic. */
public record CinematicStagePayload(
        ResourceLocation sequenceId,
        ResourceLocation stageId,
        long stageStartGameTime,
        int stageDurationTicks,
        boolean controlsLocked,
        boolean hideHud
) implements CustomPacketPayload {
    public static final Type<CinematicStagePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "cinematic_stage")
    );
    public static final StreamCodec<FriendlyByteBuf, CinematicStagePayload> STREAM_CODEC = StreamCodec.of(
            CinematicStagePayload::write,
            CinematicStagePayload::read
    );

    public CinematicStagePayload {
        sequenceId = Objects.requireNonNull(sequenceId, "sequenceId");
        stageId = Objects.requireNonNull(stageId, "stageId");
        if (stageStartGameTime < 0L || stageDurationTicks <= 0 || stageDurationTicks > 72_000) {
            throw new DecoderException("Invalid cinematic stage timing");
        }
    }

    private static void write(FriendlyByteBuf buffer, CinematicStagePayload payload) {
        buffer.writeResourceLocation(payload.sequenceId);
        buffer.writeResourceLocation(payload.stageId);
        buffer.writeVarLong(payload.stageStartGameTime);
        buffer.writeVarInt(payload.stageDurationTicks);
        buffer.writeBoolean(payload.controlsLocked);
        buffer.writeBoolean(payload.hideHud);
    }

    private static CinematicStagePayload read(FriendlyByteBuf buffer) {
        return new CinematicStagePayload(
                buffer.readResourceLocation(),
                buffer.readResourceLocation(),
                buffer.readVarLong(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
