package com.thunder.wildernessodysseyapi.cinematic.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Starts one client presentation from authoritative server state.
 *
 * <p>Subsequent synchronization occurs only at stage boundaries.</p>
 */
public record StartCinematicPayload(
        ResourceLocation sequenceId,
        ResourceLocation stageId,
        long stageStartGameTime,
        int stageDurationTicks,
        boolean controlsLocked,
        boolean hideHud,
        BlockPos anchor,
        float baseYaw,
        float basePitch
) implements CustomPacketPayload {
    public static final Type<StartCinematicPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "start_cinematic")
    );
    public static final StreamCodec<FriendlyByteBuf, StartCinematicPayload> STREAM_CODEC = StreamCodec.of(
            StartCinematicPayload::write,
            StartCinematicPayload::read
    );

    public StartCinematicPayload {
        sequenceId = Objects.requireNonNull(sequenceId, "sequenceId");
        stageId = Objects.requireNonNull(stageId, "stageId");
        anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        validate(stageStartGameTime, stageDurationTicks, baseYaw, basePitch);
    }

    private static void write(FriendlyByteBuf buffer, StartCinematicPayload payload) {
        buffer.writeResourceLocation(payload.sequenceId);
        buffer.writeResourceLocation(payload.stageId);
        buffer.writeVarLong(payload.stageStartGameTime);
        buffer.writeVarInt(payload.stageDurationTicks);
        buffer.writeBoolean(payload.controlsLocked);
        buffer.writeBoolean(payload.hideHud);
        buffer.writeBlockPos(payload.anchor);
        buffer.writeFloat(payload.baseYaw);
        buffer.writeFloat(payload.basePitch);
    }

    private static StartCinematicPayload read(FriendlyByteBuf buffer) {
        return new StartCinematicPayload(
                buffer.readResourceLocation(),
                buffer.readResourceLocation(),
                buffer.readVarLong(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBlockPos(),
                buffer.readFloat(),
                buffer.readFloat()
        );
    }

    private static void validate(long startTime, int duration, float yaw, float pitch) {
        if (startTime < 0L || duration <= 0 || duration > 72_000) {
            throw new DecoderException("Invalid cinematic stage timing");
        }
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || pitch < -90.0F || pitch > 90.0F) {
            throw new DecoderException("Invalid cinematic camera orientation");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
