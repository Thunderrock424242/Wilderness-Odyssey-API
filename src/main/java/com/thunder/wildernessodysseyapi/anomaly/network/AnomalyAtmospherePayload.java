package com.thunder.wildernessodysseyapi.anomaly.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Local server conclusions only: no seed, remote biome map, or authoritative rift data. */
public record AnomalyAtmospherePayload(ResourceLocation dimension, int zone, int intensity, int riftIntensity)
        implements CustomPacketPayload {
    public static final Type<AnomalyAtmospherePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "anomaly_atmosphere"));
    public static final StreamCodec<FriendlyByteBuf, AnomalyAtmospherePayload> STREAM_CODEC =
            StreamCodec.of(AnomalyAtmospherePayload::encode, AnomalyAtmospherePayload::decode);

    public AnomalyAtmospherePayload {
        Objects.requireNonNull(dimension, "dimension");
        if (zone < -1 || zone > 4) {
            throw new IllegalArgumentException("Anomaly zone must be -1 or 0 through 4");
        }
        if (intensity < 0 || intensity > 100 || riftIntensity < 0 || riftIntensity > 100) {
            throw new IllegalArgumentException("Anomaly intensities must be percentages from 0 through 100");
        }
    }

    private static void encode(FriendlyByteBuf buffer, AnomalyAtmospherePayload payload) {
        buffer.writeResourceLocation(payload.dimension);
        buffer.writeVarInt(payload.zone);
        buffer.writeVarInt(payload.intensity);
        buffer.writeVarInt(payload.riftIntensity);
    }

    private static AnomalyAtmospherePayload decode(FriendlyByteBuf buffer) {
        return new AnomalyAtmospherePayload(buffer.readResourceLocation(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
