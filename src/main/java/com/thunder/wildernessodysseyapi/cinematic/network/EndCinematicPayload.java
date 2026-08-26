package com.thunder.wildernessodysseyapi.cinematic.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Ends client presentation and restores every client-owned control setting. */
public record EndCinematicPayload(ResourceLocation sequenceId, boolean completedNormally)
        implements CustomPacketPayload {
    public static final Type<EndCinematicPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "end_cinematic")
    );
    public static final StreamCodec<FriendlyByteBuf, EndCinematicPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            EndCinematicPayload::sequenceId,
            ByteBufCodecs.BOOL,
            EndCinematicPayload::completedNormally,
            EndCinematicPayload::new
    );

    public EndCinematicPayload {
        sequenceId = Objects.requireNonNull(sequenceId, "sequenceId");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
