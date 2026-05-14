package com.thunder.wildernessodysseyapi.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenCodexPayload(boolean open) implements CustomPacketPayload {
    public static final Type<OpenCodexPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "open_codex"));

    public static final StreamCodec<FriendlyByteBuf, OpenCodexPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    OpenCodexPayload::open,
                    OpenCodexPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
