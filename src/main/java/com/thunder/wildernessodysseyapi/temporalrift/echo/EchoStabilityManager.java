package com.thunder.wildernessodysseyapi.temporalrift.echo;

import com.thunder.wildernessodysseyapi.temporalrift.TemporalRiftSavedData;
import com.thunder.wildernessodysseyapi.temporalrift.config.TemporalRiftConfig;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server query service combining deterministic regions and known fracture pressure.
 * Queries never load chunks or simulate weather. A bounded one-second cache samples
 * fracture distance at chunk centers so consumers agree within a chunk.
 */
public final class EchoStabilityManager {
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();
    private static final int MAX_CACHED_CHUNKS = 1024;
    private static final int MAX_OVERRIDES = 128;

    private EchoStabilityManager() { }

    /** Returns a neutral result outside Echo Earth or while the system is disabled. */
    public static EchoRegionState getStabilityAt(ServerLevel level, BlockPos position) {
        long region = EchoRegionModel.regionId(position.getX(), position.getZ());
        if (!level.dimension().equals(TemporalRiftDimensions.THE_ECHO_KEY)
                || !TemporalRiftConfig.ENABLE_ECHO_STABILITY_SYSTEM.get()) {
            return new EchoRegionState(region, EchoStabilityLevel.STABLE, 0, null, -1, 0, false);
        }
        State state = STATES.computeIfAbsent(level.getServer(), ignored -> new State());
        long epoch = level.getGameTime() / 20;
        if (state.epoch != epoch) {
            state.cache.clear();
            state.epoch = epoch;
        }
        long chunk = ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4);
        EchoRegionState cached = state.cache.get(chunk);
        if (cached != null) return cached;
        BlockPos center = new BlockPos((position.getX() & ~15) + 8, 0, (position.getZ() & ~15) + 8);
        EchoStabilityLevel base = baseLevel(level.getSeed(), region);
        BlockPos nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        double influence = 0;
        if (TemporalRiftConfig.ENABLE_ECHO_FRACTURE_INFLUENCE.get()) {
            double radius = TemporalRiftConfig.ECHO_FRACTURE_INFLUENCE_RADIUS.get();
            for (BlockPos site : EchoFractureSavedData.get(level.getServer()).sites()) {
                double distance = horizontalDistance(center, site);
                if (distance < nearestDistance) {
                    nearest = site;
                    nearestDistance = distance;
                }
                influence = Math.max(influence, EchoRegionModel.fractureInfluence(distance, radius, 0.8));
            }
            TemporalRiftSavedData rift = TemporalRiftSavedData.get(level.getServer());
            if (TemporalRiftConfig.ENABLE_RIFT_SYSTEM.get() && rift.isRiftOpen() && rift.getRiftPosition() != null) {
                double distance = horizontalDistance(center, rift.getRiftPosition());
                if (distance <= nearestDistance) {
                    nearest = rift.getRiftPosition();
                    nearestDistance = distance;
                }
                influence = Math.max(influence, EchoRegionModel.fractureInfluence(distance, radius, 1));
            }
        }
        double intensity = base.intensity() + (1 - base.intensity()) * influence;
        EchoStabilityLevel override = state.overrides.get(region);
        if (override != null) intensity = override.intensity();
        EchoRegionState result = new EchoRegionState(region, EchoStabilityLevel.fromIntensity(intensity),
                intensity, nearest, nearest == null ? -1 : nearestDistance, influence, override != null);
        if (state.cache.size() >= MAX_CACHED_CHUNKS) state.cache.remove(state.cache.keySet().iterator().next());
        state.cache.put(chunk, result);
        return result;
    }

    /** Generation baseline; live fractures and overrides never redecorate existing chunks. */
    public static EchoStabilityLevel baseLevel(long seed, long region) {
        return EchoRegionModel.level(seed, region, TemporalRiftConfig.ECHO_STABLE_REGION_WEIGHT.get(),
                TemporalRiftConfig.ECHO_DESYNCED_REGION_WEIGHT.get(), TemporalRiftConfig.ECHO_FRACTURED_REGION_WEIGHT.get());
    }

    /** Bounded operator override, cleared at shutdown. Null restores normal calculation. */
    public static void setOverride(ServerLevel level, BlockPos position, EchoStabilityLevel override) {
        State state = STATES.computeIfAbsent(level.getServer(), ignored -> new State());
        long region = EchoRegionModel.regionId(position.getX(), position.getZ());
        if (override == null) {
            state.overrides.remove(region);
        } else {
            if (!state.overrides.containsKey(region) && state.overrides.size() >= MAX_OVERRIDES) {
                state.overrides.remove(state.overrides.keySet().iterator().next());
            }
            state.overrides.put(region, override);
        }
        state.cache.clear();
    }

    /** Content API for a confirmed major fracture; no terrain mutation or chunk tickets. */
    public static void recordMajorFracture(ServerLevel level, BlockPos position) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                && !level.dimension().equals(TemporalRiftDimensions.THE_ECHO_KEY)) return;
        EchoFractureSavedData.get(level.getServer()).record(position);
        State state = STATES.get(level.getServer());
        if (state != null) state.cache.clear();
    }

    /** Scales existing Riftfall effects without creating another weather clock. */
    public static double riftfallIntensity(ServerLevel level, BlockPos position) {
        return TemporalRiftConfig.ENABLE_ECHO_STABILITY_SYSTEM.get()
                ? getStabilityAt(level, position).intensity() : 1;
    }

    /** Releases caches and temporary debug overrides at server/level unload. */
    public static void clear(MinecraftServer server) {
        STATES.remove(server);
    }

    private static double horizontalDistance(BlockPos a, BlockPos b) {
        return Math.hypot((double) a.getX() - b.getX(), (double) a.getZ() - b.getZ());
    }

    private static final class State {
        private long epoch = Long.MIN_VALUE;
        private final Map<Long, EchoRegionState> cache = new LinkedHashMap<>();
        private final Map<Long, EchoStabilityLevel> overrides = new LinkedHashMap<>();
    }
}
