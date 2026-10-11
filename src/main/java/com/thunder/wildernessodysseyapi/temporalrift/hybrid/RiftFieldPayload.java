package com.thunder.wildernessodysseyapi.temporalrift.hybrid;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Nearby client presentation summary. Clients send no gameplay requests through this payload. */
public record RiftFieldPayload(ResourceLocation dimension, BlockPos anchor, Direction facing,
                               RiftState state, float radius, boolean impact) implements CustomPacketPayload {
    public static final Type<RiftFieldPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "rift_field"));
    public static final StreamCodec<FriendlyByteBuf, RiftFieldPayload> STREAM_CODEC = StreamCodec.of(RiftFieldPayload::encode, RiftFieldPayload::decode);

    public RiftFieldPayload {
        anchor = anchor.immutable();
        facing = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        radius = Float.isFinite(radius) ? Math.clamp(radius, 10, 48) : 30;
    }

    private static void encode(FriendlyByteBuf buf, RiftFieldPayload value) {
        buf.writeResourceLocation(value.dimension);
        buf.writeBlockPos(value.anchor);
        buf.writeEnum(value.facing);
        buf.writeEnum(value.state);
        buf.writeFloat(value.radius);
        buf.writeBoolean(value.impact);
    }

    private static RiftFieldPayload decode(FriendlyByteBuf buf) {
        return new RiftFieldPayload(buf.readResourceLocation(), buf.readBlockPos(), buf.readEnum(Direction.class),
                buf.readEnum(RiftState.class), buf.readFloat(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
