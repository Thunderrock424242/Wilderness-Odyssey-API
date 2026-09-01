package com.thunder.wildernessodysseyapi.environment.glacial.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.environment.glacial.GlacialSeason;
import com.thunder.wildernessodysseyapi.environment.glacial.GlacialSeasonSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Dimension-aware client presentation snapshot for glacial season effects. */
public record GlacialSeasonSyncPayload(
        ResourceLocation dimension,
        long serverTick,
        GlacialSeason season,
        float meltFraction,
        float freezeFraction,
        boolean calendarAvailable,
        boolean debugOverride
) implements CustomPacketPayload {

    public static final Type<GlacialSeasonSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "glacial_season_sync")
    );
    public static final StreamCodec<FriendlyByteBuf, GlacialSeasonSyncPayload> STREAM_CODEC =
            StreamCodec.of(GlacialSeasonSyncPayload::encode, GlacialSeasonSyncPayload::decode);

    public GlacialSeasonSyncPayload {
        dimension = Objects.requireNonNull(dimension, "dimension");
        serverTick = Math.max(0L, serverTick);
        season = Objects.requireNonNullElse(season, GlacialSeason.POLAR_COLD);
        meltFraction = unit(meltFraction);
        freezeFraction = unit(freezeFraction);
    }

    /** Builds a compact wire value from the server authority snapshot. */
    public static GlacialSeasonSyncPayload from(
            ResourceLocation dimension,
            long serverTick,
            GlacialSeasonSnapshot snapshot
    ) {
        GlacialSeasonSnapshot safe = snapshot == null ? GlacialSeasonSnapshot.POLAR_COLD : snapshot;
        return new GlacialSeasonSyncPayload(
                dimension,
                serverTick,
                safe.season(),
                (float) safe.meltFraction(),
                (float) safe.freezeFraction(),
                safe.calendarAvailable(),
                safe.debugOverride()
        );
    }

    /** Reconstructs the immutable client-facing snapshot. */
    public GlacialSeasonSnapshot snapshot() {
        return new GlacialSeasonSnapshot(
                season, meltFraction, freezeFraction, calendarAvailable, debugOverride);
    }

    private static void encode(FriendlyByteBuf buffer, GlacialSeasonSyncPayload payload) {
        buffer.writeResourceLocation(payload.dimension);
        buffer.writeVarLong(payload.serverTick);
        buffer.writeByte(payload.season.ordinal());
        buffer.writeFloat(payload.meltFraction);
        buffer.writeFloat(payload.freezeFraction);
        buffer.writeBoolean(payload.calendarAvailable);
        buffer.writeBoolean(payload.debugOverride);
    }

    private static GlacialSeasonSyncPayload decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        long serverTick = buffer.readVarLong();
        int ordinal = buffer.readUnsignedByte();
        GlacialSeason[] seasons = GlacialSeason.values();
        GlacialSeason season = ordinal < seasons.length ? seasons[ordinal] : GlacialSeason.POLAR_COLD;
        return new GlacialSeasonSyncPayload(
                dimension,
                serverTick,
                season,
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readBoolean(),
                buffer.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static float unit(float value) {
        return Math.max(0.0F, Math.min(1.0F, Float.isFinite(value) ? value : 0.0F));
    }
}
