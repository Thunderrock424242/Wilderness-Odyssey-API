package com.thunder.wildernessodysseyapi.cinematic.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Synchronizes one authored narration cue without trusting arbitrary server text. */
public record CinematicNarrationPayload(
        ResourceLocation sequenceId,
        ResourceLocation cueId,
        int durationTicks
) implements CustomPacketPayload {
    public static final Type<CinematicNarrationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "cinematic_narration")
    );
    public static final StreamCodec<FriendlyByteBuf, CinematicNarrationPayload> STREAM_CODEC = StreamCodec.of(
            CinematicNarrationPayload::write,
            CinematicNarrationPayload::read
    );

    public CinematicNarrationPayload {
        sequenceId = Objects.requireNonNull(sequenceId, "sequenceId");
        cueId = Objects.requireNonNull(cueId, "cueId");
        if (durationTicks <= 0 || durationTicks > 1_200) {
            throw new DecoderException("Invalid cinematic narration duration");
        }
    }

    private static void write(FriendlyByteBuf buffer, CinematicNarrationPayload payload) {
        buffer.writeResourceLocation(payload.sequenceId);
        buffer.writeResourceLocation(payload.cueId);
        buffer.writeVarInt(payload.durationTicks);
    }

    private static CinematicNarrationPayload read(FriendlyByteBuf buffer) {
        return new CinematicNarrationPayload(
                buffer.readResourceLocation(),
                buffer.readResourceLocation(),
                buffer.readVarInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
