package com.thunder.wildernessodysseyapi.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CloakInputPayload(boolean altDown) implements CustomPacketPayload {
    public static final Type<CloakInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "cloak_input"));

    public static final StreamCodec<FriendlyByteBuf, CloakInputPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    CloakInputPayload::altDown,
                    CloakInputPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
