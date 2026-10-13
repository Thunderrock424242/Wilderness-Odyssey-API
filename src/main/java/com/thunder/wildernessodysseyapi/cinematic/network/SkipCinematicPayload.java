package com.thunder.wildernessodysseyapi.cinematic.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Requests a skip of the sender's current intro; the server validates the playback and scope. */
public record SkipCinematicPayload(ResourceLocation sequenceId, long sequenceStartGameTime)
        implements CustomPacketPayload {
    public static final Type<SkipCinematicPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "skip_cinematic")
    );
    public static final StreamCodec<FriendlyByteBuf, SkipCinematicPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            SkipCinematicPayload::sequenceId,
            ByteBufCodecs.VAR_LONG,
            SkipCinematicPayload::sequenceStartGameTime,
            SkipCinematicPayload::new
    );

    public SkipCinematicPayload {
        sequenceId = Objects.requireNonNull(sequenceId, "sequenceId");
        if (sequenceStartGameTime < 0L) {
            throw new DecoderException("Invalid cinematic skip timing");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
