package com.thunder.wildernessodysseyapi.temporalrift.echo;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Player-local conclusions only; no world seed or undiscovered fracture coordinates cross the wire. */
public record EchoStabilityPayload(ResourceLocation dimension, long gameTime, long regionId,
                                   int intensity, int riftfallIntensity, boolean atmosphere, boolean timeAnomalies)
        implements CustomPacketPayload {
    public static final Type<EchoStabilityPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "echo_stability"));
    public static final StreamCodec<FriendlyByteBuf, EchoStabilityPayload> STREAM_CODEC =
            StreamCodec.of(EchoStabilityPayload::encode, EchoStabilityPayload::decode);

    public EchoStabilityPayload {
        java.util.Objects.requireNonNull(dimension);
        intensity = Math.max(0, Math.min(100, intensity));
        riftfallIntensity = Math.max(0, Math.min(100, riftfallIntensity));
    }

    private static void encode(FriendlyByteBuf buffer, EchoStabilityPayload value) {
        buffer.writeResourceLocation(value.dimension);
        buffer.writeLong(value.gameTime);
        buffer.writeLong(value.regionId);
        buffer.writeByte(value.intensity);
        buffer.writeByte(value.riftfallIntensity);
        buffer.writeBoolean(value.atmosphere);
        buffer.writeBoolean(value.timeAnomalies);
    }

    private static EchoStabilityPayload decode(FriendlyByteBuf buffer) {
        return new EchoStabilityPayload(buffer.readResourceLocation(), buffer.readLong(), buffer.readLong(),
                buffer.readUnsignedByte(), buffer.readUnsignedByte(), buffer.readBoolean(), buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
