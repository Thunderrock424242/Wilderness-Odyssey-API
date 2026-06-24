package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Synchronizes server-owned wind and wave energy to one client world. */
public record OceanSeaStatePayload(
        float strength,
        float windDirectionX,
        float windDirectionZ,
        float windSpeed,
        float swellScale,
        float chopScale,
        float directionBlend,
        float breakingStrength
) implements CustomPacketPayload {

    /** Payload identifier used by NeoForge's play protocol. */
    public static final Type<OceanSeaStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "ocean_sea_state")
    );
    /** Fixed-size codec for the bounded environmental snapshot. */
    public static final StreamCodec<FriendlyByteBuf, OceanSeaStatePayload> STREAM_CODEC =
            StreamCodec.of(OceanSeaStatePayload::encode, OceanSeaStatePayload::decode);

    public OceanSeaStatePayload {
        OceanSeaState.Sample sanitized = new OceanSeaState.Sample(
                strength,
                windDirectionX,
                windDirectionZ,
                windSpeed,
                swellScale,
                chopScale,
                directionBlend,
                breakingStrength
        );
        strength = sanitized.strength();
        windDirectionX = sanitized.windDirectionX();
        windDirectionZ = sanitized.windDirectionZ();
        windSpeed = sanitized.windSpeed();
        swellScale = sanitized.swellScale();
        chopScale = sanitized.chopScale();
        directionBlend = sanitized.directionBlend();
        breakingStrength = sanitized.breakingStrength();
    }

    /** Creates a network snapshot from the server's current model. */
    public static OceanSeaStatePayload fromSample(OceanSeaState.Sample sample) {
        return new OceanSeaStatePayload(
                sample.strength(),
                sample.windDirectionX(),
                sample.windDirectionZ(),
                sample.windSpeed(),
                sample.swellScale(),
                sample.chopScale(),
                sample.directionBlend(),
                sample.breakingStrength()
        );
    }

    /** Converts decoded values back to the shared bounded model. */
    public OceanSeaState.Sample toSample() {
        return new OceanSeaState.Sample(
                strength,
                windDirectionX,
                windDirectionZ,
                windSpeed,
                swellScale,
                chopScale,
                directionBlend,
                breakingStrength
        );
    }

    private static void encode(FriendlyByteBuf buffer, OceanSeaStatePayload payload) {
        buffer.writeFloat(payload.strength);
        buffer.writeFloat(payload.windDirectionX);
        buffer.writeFloat(payload.windDirectionZ);
        buffer.writeFloat(payload.windSpeed);
        buffer.writeFloat(payload.swellScale);
        buffer.writeFloat(payload.chopScale);
        buffer.writeFloat(payload.directionBlend);
        buffer.writeFloat(payload.breakingStrength);
    }

    private static OceanSeaStatePayload decode(FriendlyByteBuf buffer) {
        return new OceanSeaStatePayload(
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
