package com.thunder.wildernessodysseyapi.environment.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.environment.api.RegionalEnvironmentSnapshot;
import com.thunder.wildernessodysseyapi.riftfall.RiftfallStage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Compact player-local environment summary for ambience and diagnostics.
 *
 * <p>The server sends conclusions only; clients do not infer gameplay weather,
 * hydrology, radiation, or Riftfall state from visual blocks.</p>
 */
public record EnvironmentSyncPayload(
        ResourceLocation dimension,
        long gameTime,
        float wind,
        float precipitation,
        float thunder,
        float waterAvailability,
        float habitatProductivity,
        float shelterPressure,
        float migrationPressure,
        float wildlifeActivity,
        float aquaticActivity,
        float vegetationStress,
        float overallHazard,
        float drought,
        float tideOffset,
        float tideRate,
        float radiation,
        boolean flooding,
        boolean coastal,
        boolean meteorPresent,
        int meteorX,
        int meteorY,
        int meteorZ,
        int meteorRadius,
        int riftfallStage
) implements CustomPacketPayload {

    /** Payload identifier used by NeoForge's play protocol. */
    public static final Type<EnvironmentSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "environment_summary")
    );
    /** Fixed-size codec for one player-local summary. */
    public static final StreamCodec<FriendlyByteBuf, EnvironmentSyncPayload> STREAM_CODEC =
            StreamCodec.of(EnvironmentSyncPayload::encode, EnvironmentSyncPayload::decode);

    public EnvironmentSyncPayload {
        if (dimension == null) {
            throw new IllegalArgumentException("Environment summary dimension is required");
        }
        gameTime = Math.max(0L, gameTime);
        wind = unit(wind);
        precipitation = unit(precipitation);
        thunder = unit(thunder);
        waterAvailability = unit(waterAvailability);
        habitatProductivity = unit(habitatProductivity);
        shelterPressure = unit(shelterPressure);
        migrationPressure = unit(migrationPressure);
        wildlifeActivity = unit(wildlifeActivity);
        aquaticActivity = unit(aquaticActivity);
        vegetationStress = unit(vegetationStress);
        overallHazard = unit(overallHazard);
        drought = unit(drought);
        tideOffset = finite(tideOffset);
        tideRate = finite(tideRate);
        radiation = unit(radiation);
        meteorRadius = Math.max(0, Math.min(1_024, meteorRadius));
        riftfallStage = Math.max(0, Math.min(RiftfallStage.values().length - 1, riftfallStage));
        if (!meteorPresent) {
            meteorRadius = 0;
            radiation = 0.0F;
        }
    }

    /** Creates one bounded packet from the server's combined snapshot. */
    public static EnvironmentSyncPayload from(
            ServerPlayer player,
            RegionalEnvironmentSnapshot snapshot
    ) {
        var influence = snapshot.influence();
        var meteor = snapshot.meteorSite();
        return new EnvironmentSyncPayload(
                player.level().dimension().location(),
                snapshot.gameTime(),
                (float) Math.min(1.0, snapshot.weather().wind().magnitude()),
                (float) snapshot.weather().precipitationIntensity(),
                (float) snapshot.weather().thunderIntensity(),
                (float) influence.waterAvailability(),
                (float) influence.habitatProductivity(),
                (float) influence.shelterPressure(),
                (float) influence.migrationPressure(),
                (float) influence.wildlifeActivity(),
                (float) influence.aquaticActivity(),
                (float) influence.vegetationStress(),
                (float) influence.overallHazard(),
                (float) snapshot.vegetation().droughtLevel(),
                snapshot.tide().offset(),
                snapshot.tide().rate(),
                (float) meteor.radiation(),
                snapshot.watershed().flooding(),
                snapshot.coastal(),
                meteor.present(),
                meteor.center().getX(),
                meteor.center().getY(),
                meteor.center().getZ(),
                meteor.craterRadius(),
                snapshot.riftfallStage().ordinal()
        );
    }

    /** Returns the validated synchronized Riftfall stage. */
    public RiftfallStage riftfallStageValue() {
        return RiftfallStage.values()[riftfallStage];
    }

    private static void encode(FriendlyByteBuf buffer, EnvironmentSyncPayload payload) {
        buffer.writeResourceLocation(payload.dimension);
        buffer.writeVarLong(payload.gameTime);
        buffer.writeFloat(payload.wind);
        buffer.writeFloat(payload.precipitation);
        buffer.writeFloat(payload.thunder);
        buffer.writeFloat(payload.waterAvailability);
        buffer.writeFloat(payload.habitatProductivity);
        buffer.writeFloat(payload.shelterPressure);
        buffer.writeFloat(payload.migrationPressure);
        buffer.writeFloat(payload.wildlifeActivity);
        buffer.writeFloat(payload.aquaticActivity);
        buffer.writeFloat(payload.vegetationStress);
        buffer.writeFloat(payload.overallHazard);
        buffer.writeFloat(payload.drought);
        buffer.writeFloat(payload.tideOffset);
        buffer.writeFloat(payload.tideRate);
        buffer.writeFloat(payload.radiation);
        buffer.writeBoolean(payload.flooding);
        buffer.writeBoolean(payload.coastal);
        buffer.writeBoolean(payload.meteorPresent);
        buffer.writeInt(payload.meteorX);
        buffer.writeInt(payload.meteorY);
        buffer.writeInt(payload.meteorZ);
        buffer.writeVarInt(payload.meteorRadius);
        buffer.writeByte(payload.riftfallStage);
    }

    private static EnvironmentSyncPayload decode(FriendlyByteBuf buffer) {
        return new EnvironmentSyncPayload(
                buffer.readResourceLocation(),
                buffer.readVarLong(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readVarInt(),
                buffer.readUnsignedByte()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static float unit(float value) {
        return Math.max(0.0F, Math.min(1.0F, finite(value)));
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }
}
