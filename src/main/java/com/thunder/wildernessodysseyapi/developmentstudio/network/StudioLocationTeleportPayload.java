package com.thunder.wildernessodysseyapi.developmentstudio.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Requests teleport to one server-registered, available campus location id. */
public record StudioLocationTeleportPayload(ResourceLocation locationId) implements CustomPacketPayload {
    public static final Type<StudioLocationTeleportPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "studio_location_teleport")
    );
    public static final StreamCodec<FriendlyByteBuf, StudioLocationTeleportPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeResourceLocation(payload.locationId),
            buffer -> new StudioLocationTeleportPayload(buffer.readResourceLocation())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
