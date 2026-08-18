package com.thunder.wildernessodysseyapi.vegetation.state;

import com.thunder.wildernessodysseyapi.vegetation.api.VegetationClimateState;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationSeasonState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * Compact persistent and synchronized vegetation climate for one chunk.
 *
 * <p>The mutable holder exists once per participating chunk. It owns no plant
 * positions, timers, or block-entity references; scheduled work samples the
 * normal chunk heightmap and records only regional aggregates.</p>
 */
public final class ReactiveVegetationState implements INBTSerializable<CompoundTag> {

    private static final int FORMAT_VERSION = 1;
    private static final String FORMAT_KEY = "format_version";

    /** Full compact state used for initial and infrequent regional synchronization. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ReactiveVegetationState> STREAM_CODEC =
            StreamCodec.of(ReactiveVegetationState::encode, ReactiveVegetationState::decode);

    private double moisture = VegetationClimateState.DEFAULT.moisture();
    private double recentRainfall;
    private double droughtLevel;
    private double stormIntensity;
    private VegetationSeasonState seasonState = VegetationSeasonState.UNKNOWN;
    private long lastClimateUpdateTick;
    private long lastVegetationUpdateTick;
    private int plantsProcessed;
    private double averageProcessingMicros;
    private Runnable dirtyListener = () -> { };

    /** Sets the callback that marks the owning server chunk unsaved. */
    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> { } : dirtyListener;
    }

    /** Returns the immutable state used by public APIs and update behaviors. */
    public VegetationClimateState snapshot() {
        return new VegetationClimateState(
                moisture,
                recentRainfall,
                droughtLevel,
                stormIntensity,
                seasonState,
                lastClimateUpdateTick,
                lastVegetationUpdateTick,
                plantsProcessed,
                averageProcessingMicros
        );
    }

    /** Applies one completed localized-weather sample and marks chunk persistence dirty. */
    public void applyClimate(VegetationClimateState state) {
        VegetationClimateState safe = state == null ? VegetationClimateState.DEFAULT : state;
        moisture = safe.moisture();
        recentRainfall = safe.recentRainfall();
        droughtLevel = safe.droughtLevel();
        stormIntensity = safe.stormIntensity();
        seasonState = safe.seasonState();
        lastClimateUpdateTick = safe.lastClimateUpdateTick();
        dirtyListener.run();
    }

    /** Records one bounded plant pass and an exponentially smoothed processing time. */
    public void recordProcessing(long gameTime, int processed, long elapsedNanos) {
        lastVegetationUpdateTick = Math.max(0L, gameTime);
        plantsProcessed = Math.max(0, processed);
        double elapsedMicros = Math.max(0L, elapsedNanos) / 1_000.0;
        averageProcessingMicros = averageProcessingMicros <= 0.0
                ? elapsedMicros
                : averageProcessingMicros * 0.85 + elapsedMicros * 0.15;
        dirtyListener.run();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(FORMAT_KEY, FORMAT_VERSION);
        tag.putDouble("moisture", moisture);
        tag.putDouble("recent_rainfall", recentRainfall);
        tag.putDouble("drought", droughtLevel);
        tag.putDouble("storm", stormIntensity);
        tag.putString("season", seasonState.name());
        tag.putLong("last_climate_update", lastClimateUpdateTick);
        tag.putLong("last_vegetation_update", lastVegetationUpdateTick);
        tag.putInt("plants_processed", plantsProcessed);
        tag.putDouble("average_processing_micros", averageProcessingMicros);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        moisture = unit(tag.getDouble("moisture"), VegetationClimateState.DEFAULT.moisture());
        recentRainfall = unit(tag.getDouble("recent_rainfall"), 0.0);
        droughtLevel = unit(tag.getDouble("drought"), 0.0);
        stormIntensity = unit(tag.getDouble("storm"), 0.0);
        seasonState = parseSeason(tag.getString("season"));
        lastClimateUpdateTick = Math.max(0L, tag.getLong("last_climate_update"));
        lastVegetationUpdateTick = Math.max(0L, tag.getLong("last_vegetation_update"));
        plantsProcessed = Math.max(0, tag.getInt("plants_processed"));
        averageProcessingMicros = Math.max(0.0, finite(tag.getDouble("average_processing_micros"), 0.0));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ReactiveVegetationState state) {
        VegetationClimateState snapshot = state == null
                ? VegetationClimateState.DEFAULT : state.snapshot();
        buffer.writeFloat((float) snapshot.moisture());
        buffer.writeFloat((float) snapshot.recentRainfall());
        buffer.writeFloat((float) snapshot.droughtLevel());
        buffer.writeFloat((float) snapshot.stormIntensity());
        buffer.writeByte(snapshot.seasonState().ordinal());
        buffer.writeVarLong(snapshot.lastClimateUpdateTick());
        buffer.writeVarLong(snapshot.lastVegetationUpdateTick());
        buffer.writeVarInt(snapshot.plantsProcessed());
        buffer.writeFloat((float) snapshot.averageProcessingMicros());
    }

    private static ReactiveVegetationState decode(RegistryFriendlyByteBuf buffer) {
        ReactiveVegetationState state = new ReactiveVegetationState();
        state.moisture = unit(buffer.readFloat(), VegetationClimateState.DEFAULT.moisture());
        state.recentRainfall = unit(buffer.readFloat(), 0.0);
        state.droughtLevel = unit(buffer.readFloat(), 0.0);
        state.stormIntensity = unit(buffer.readFloat(), 0.0);
        state.seasonState = seasonByOrdinal(buffer.readUnsignedByte());
        state.lastClimateUpdateTick = Math.max(0L, buffer.readVarLong());
        state.lastVegetationUpdateTick = Math.max(0L, buffer.readVarLong());
        state.plantsProcessed = Math.max(0, buffer.readVarInt());
        state.averageProcessingMicros = Math.max(0.0, finite(buffer.readFloat(), 0.0));
        return state;
    }

    private static VegetationSeasonState parseSeason(String value) {
        try {
            return VegetationSeasonState.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return VegetationSeasonState.UNKNOWN;
        }
    }

    private static VegetationSeasonState seasonByOrdinal(int ordinal) {
        VegetationSeasonState[] values = VegetationSeasonState.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : VegetationSeasonState.UNKNOWN;
    }

    private static double unit(double value, double fallback) {
        return Math.max(0.0, Math.min(1.0, finite(value, fallback)));
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
