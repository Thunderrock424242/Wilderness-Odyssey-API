package com.thunder.wildernessodysseyapi.vegetation.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationClimateState;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationSeasonState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Dimension-aware, clientbound vegetation climate for one tracked chunk.
 *
 * <p>The packet omits server-only processing diagnostics and carries a
 * monotonic transport revision so late packets cannot replace newer state.</p>
 */
public record ReactiveVegetationSyncPayload(
        ResourceLocation dimension,
        int chunkX,
        int chunkZ,
        long revision,
        float moisture,
        float recentRainfall,
        float droughtLevel,
        float stormIntensity,
        VegetationSeasonState seasonState,
        long climateTick
) implements CustomPacketPayload {

    /** Payload identifier used by NeoForge's clientbound play protocol. */
    public static final Type<ReactiveVegetationSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "reactive_vegetation_sync")
    );
    /** Compact fixed-shape codec for one chunk climate snapshot. */
    public static final StreamCodec<FriendlyByteBuf, ReactiveVegetationSyncPayload> STREAM_CODEC =
            StreamCodec.of(ReactiveVegetationSyncPayload::encode, ReactiveVegetationSyncPayload::decode);

    public ReactiveVegetationSyncPayload {
        dimension = Objects.requireNonNull(dimension, "dimension");
        revision = Math.max(0L, revision);
        moisture = unit(moisture);
        recentRainfall = unit(recentRainfall);
        droughtLevel = unit(droughtLevel);
        stormIntensity = unit(stormIntensity);
        seasonState = Objects.requireNonNullElse(seasonState, VegetationSeasonState.UNKNOWN);
        climateTick = Math.max(0L, climateTick);
    }

    /** Creates a client-only snapshot without server processing counters or timings. */
    public static ReactiveVegetationSyncPayload from(
            ResourceLocation dimension,
            int chunkX,
            int chunkZ,
            long revision,
            VegetationClimateState state
    ) {
        VegetationClimateState safe = state == null ? VegetationClimateState.DEFAULT : state;
        return new ReactiveVegetationSyncPayload(
                dimension,
                chunkX,
                chunkZ,
                revision,
                (float) safe.moisture(),
                (float) safe.recentRainfall(),
                (float) safe.droughtLevel(),
                (float) safe.stormIntensity(),
                safe.seasonState(),
                safe.lastClimateUpdateTick()
        );
    }

    /** Reconstructs the immutable API state used by client tint and compatibility queries. */
    public VegetationClimateState climateState() {
        return new VegetationClimateState(
                moisture,
                recentRainfall,
                droughtLevel,
                stormIntensity,
                seasonState,
                climateTick,
                0L,
                0,
                0.0
        );
    }

    /** Returns whether the packet belongs to the currently active client dimension. */
    public boolean matchesDimension(ResourceLocation activeDimension) {
        return dimension.equals(activeDimension);
    }

    private static void encode(FriendlyByteBuf buffer, ReactiveVegetationSyncPayload payload) {
        buffer.writeResourceLocation(payload.dimension);
        buffer.writeInt(payload.chunkX);
        buffer.writeInt(payload.chunkZ);
        buffer.writeVarLong(payload.revision);
        buffer.writeFloat(payload.moisture);
        buffer.writeFloat(payload.recentRainfall);
        buffer.writeFloat(payload.droughtLevel);
        buffer.writeFloat(payload.stormIntensity);
        buffer.writeByte(payload.seasonState.ordinal());
        buffer.writeVarLong(payload.climateTick);
    }

    private static ReactiveVegetationSyncPayload decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        int chunkX = buffer.readInt();
        int chunkZ = buffer.readInt();
        long revision = buffer.readVarLong();
        float moisture = buffer.readFloat();
        float recentRainfall = buffer.readFloat();
        float droughtLevel = buffer.readFloat();
        float stormIntensity = buffer.readFloat();
        int seasonOrdinal = buffer.readUnsignedByte();
        VegetationSeasonState[] seasons = VegetationSeasonState.values();
        VegetationSeasonState season = seasonOrdinal < seasons.length
                ? seasons[seasonOrdinal] : VegetationSeasonState.UNKNOWN;
        long climateTick = buffer.readVarLong();
        return new ReactiveVegetationSyncPayload(
                dimension,
                chunkX,
                chunkZ,
                revision,
                moisture,
                recentRainfall,
                droughtLevel,
                stormIntensity,
                season,
                climateTick
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static float unit(float value) {
        float finite = Float.isFinite(value) ? value : 0.0F;
        return Math.max(0.0F, Math.min(1.0F, finite));
    }
}
