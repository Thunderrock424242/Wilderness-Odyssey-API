package com.thunder.wildernessodysseyapi.ecosystem.debug.map;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Empty, permission-checked request for a fresh bounded ecosystem map. */
public record EcosystemDebugMapRequestPayload() implements CustomPacketPayload {
    public static final Type<EcosystemDebugMapRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "ecosystem_debug_map_request")
    );
    public static final StreamCodec<FriendlyByteBuf, EcosystemDebugMapRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                    },
                    buffer -> new EcosystemDebugMapRequestPayload()
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
