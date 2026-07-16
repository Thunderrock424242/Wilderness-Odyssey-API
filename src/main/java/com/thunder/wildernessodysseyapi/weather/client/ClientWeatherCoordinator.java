package com.thunder.wildernessodysseyapi.weather.client;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.client.cloud.CloudFieldSample;
import com.thunder.wildernessodysseyapi.weather.networking.WeatherRegionSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Owns the active client's immutable, server-authored atmospheric snapshots.
 *
 * <p>Network DTOs are validated and copied into a complete replacement value
 * before one volatile publication. Rendering therefore observes either the old
 * region or the new region, never a partially applied delta. The coordinator
 * retains a displayed previous snapshot so spatial interpolation can also be
 * blended over time without mutating networking state.</p>
 */
public final class ClientWeatherCoordinator {

    private static final int MIN_CELL_SIZE = 16;
    private static final int MAX_CELL_SIZE = 4_096;
    private static final long TRANSITION_NANOS = 2_000_000_000L;
    private static final double PRECIPITATION_EPSILON = 1.0E-4D;
    private static final Object UPDATE_LOCK = new Object();
    private static final Map<ResourceLocation, Long> SEQUENCE_WATERMARKS = new HashMap<>();

    private static volatile State activeState;

    private ClientWeatherCoordinator() {
    }

    /**
     * Validates and atomically accepts one server-to-client regional update.
     *
     * @return {@code true} when the payload became the newest state or explicit reset
     */
    public static boolean accept(WeatherRegionSyncPayload payload) {
        if (payload == null
                || payload.dataVersion() != WeatherRegionSyncPayload.CURRENT_DATA_VERSION
                || payload.sequence() < 0L) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !level.dimension().location().equals(payload.dimension())) {
            return false;
        }

        synchronized (UPDATE_LOCK) {
            long watermark = SEQUENCE_WATERMARKS.getOrDefault(payload.dimension(), -1L);
            if (payload.sequence() <= watermark) {
                return false;
            }

            if (!payload.enabled()) {
                // Disabled snapshots are explicit compatibility resets. They
                // release localized rendering immediately instead of fading.
                SEQUENCE_WATERMARKS.put(payload.dimension(), payload.sequence());
                activeState = null;
                return true;
            }
            if (!isValidEnabledPayload(payload)) {
                return false;
            }

            State oldState = activeState;
            WeatherSnapshot oldTarget = matchingTarget(oldState, payload.dimension(), payload.cellSize());
            Map<Long, WeatherSnapshot.SnapshotCell> nextCells = payload.replaceRegion()
                    || oldTarget == null
                    ? new HashMap<>(payload.cells().size() * 2)
                    : oldTarget.mutableCellCopy();

            for (WeatherRegionSyncPayload.CellSnapshot cell : payload.cells()) {
                long key = WeatherSnapshot.packCell(cell.cellX(), cell.cellZ());
                WeatherSnapshot.SnapshotCell existing = nextCells.get(key);
                if (existing != null && existing.revision() >= cell.revision()) {
                    continue;
                }
                nextCells.put(key, new WeatherSnapshot.SnapshotCell(
                        cell.cellX(),
                        cell.cellZ(),
                        cell.revision(),
                        cell.sample()
                ));
            }

            WeatherSnapshot next = new WeatherSnapshot(
                    payload.dimension(),
                    payload.dataVersion(),
                    payload.sequence(),
                    payload.cellSize(),
                    nextCells
            );
            long now = System.nanoTime();
            WeatherSnapshot previous = transitionSource(oldState, next, now);

            SEQUENCE_WATERMARKS.put(payload.dimension(), payload.sequence());
            activeState = new State(previous, next, now);
            return true;
        }
    }

    /** Returns whether localized weather currently owns rendering for this level. */
    public static boolean controls(ClientLevel level) {
        State state = activeState;
        return level != null
                && state != null
                && state.current().dimension().equals(level.dimension().location());
    }

    /** Returns the temporally and spatially interpolated sample at a block. */
    public static WeatherSample sampleAt(ClientLevel level, BlockPos pos) {
        return pos == null ? WeatherSample.CLEAR : sampleAt(level, pos.getX(), pos.getZ());
    }

    /** Returns the temporally and spatially interpolated sample at a vector position. */
    public static WeatherSample sampleAt(ClientLevel level, Vec3 pos) {
        return pos == null ? WeatherSample.CLEAR : sampleAt(level, pos.x, pos.z);
    }

    /** Returns the support-aware cloud field at a vector position. */
    public static CloudFieldSample cloudFieldAt(ClientLevel level, Vec3 pos) {
        return pos == null ? CloudFieldSample.CLEAR : cloudFieldAt(level, pos.x, pos.z);
    }

    /**
     * Returns the spatially and temporally interpolated cloud field at world coordinates.
     *
     * <p>This query deliberately exposes synchronized-region support so cloud
     * geometry can fade at its data boundary. General weather queries retain
     * their nearest-cell edge behavior for precipitation stability.</p>
     */
    public static CloudFieldSample cloudFieldAt(ClientLevel level, double blockX, double blockZ) {
        State state = matchingState(level);
        if (state == null) {
            return CloudFieldSample.CLEAR;
        }
        double amount = state.progress(System.nanoTime());
        if (amount >= 1.0D) {
            return state.current().cloudField(blockX, blockZ);
        }
        return CloudFieldSample.interpolate(
                state.previous().cloudField(blockX, blockZ),
                state.current().cloudField(blockX, blockZ),
                amount
        );
    }

    /** Returns the local precipitation intensity in the canonical {@code [0, 1]} range. */
    public static float precipitationIntensityAt(ClientLevel level, BlockPos pos) {
        return pos == null ? 0.0F : (float) precipitationIntensityAt(level, pos.getX(), pos.getZ());
    }

    /**
     * Returns the interpolated local rain/snow classification at a block.
     *
     * <p>This is the allocation-light path used for each precipitation render
     * column. Full immutable samples remain available to lower-frequency
     * consumers through {@link #sampleAt(ClientLevel, BlockPos)}.</p>
     */
    public static PrecipitationType precipitationTypeAt(ClientLevel level, BlockPos pos) {
        if (pos == null) {
            return PrecipitationType.NONE;
        }
        double blockX = pos.getX();
        double blockZ = pos.getZ();
        State state = matchingState(level);
        if (state == null) {
            return PrecipitationType.NONE;
        }
        double amount = state.progress(System.nanoTime());
        double previousIntensity = state.previous().precipitationIntensity(blockX, blockZ);
        double currentIntensity = state.current().precipitationIntensity(blockX, blockZ);
        double intensity = lerp(previousIntensity, currentIntensity, amount);
        if (intensity <= PRECIPITATION_EPSILON) {
            return PrecipitationType.NONE;
        }
        PrecipitationType previousType = state.previous().precipitationType(blockX, blockZ);
        PrecipitationType currentType = state.current().precipitationType(blockX, blockZ);
        if (previousType == currentType) {
            return currentType;
        }
        if (previousType == PrecipitationType.NONE) {
            return currentType;
        }
        if (currentType == PrecipitationType.NONE) {
            return previousType;
        }
        double previousTemperature = state.previous().temperature(blockX, blockZ);
        double currentTemperature = state.current().temperature(blockX, blockZ);
        return precipitationTypeForTemperature(lerp(previousTemperature, currentTemperature, amount));
    }

    /** Returns the interpolated weather sample at the local player or camera. */
    public static WeatherSample localSample(ClientLevel level) {
        BlockPos localPosition = localPosition(level);
        return localPosition == null ? WeatherSample.CLEAR : sampleAt(level, localPosition);
    }

    /** Returns the local precipitation intensity used by vanilla weather rendering hooks. */
    public static float localPrecipitationIntensity(ClientLevel level) {
        BlockPos localPosition = localPosition(level);
        return localPosition == null ? 0.0F : precipitationIntensityAt(level, localPosition);
    }

    /** Returns overcast, precipitation, and storm darkening for sky calculations. */
    public static float localSkyDarkening(ClientLevel level) {
        return (float) localSample(level).skyDarkening();
    }

    /**
     * Returns a smooth local thunder contribution derived from authoritative storm state.
     *
     * <p>The shared immutable sample owns this formula so client sky rendering
     * and future localized gameplay integrations read the same result.</p>
     */
    public static float localThunderLevel(ClientLevel level) {
        return thunderContribution(localSample(level));
    }

    /** Returns a smooth air-fog contribution derived from local humidity and precipitation. */
    public static float localFogContribution(ClientLevel level) {
        return fogContribution(localSample(level));
    }

    /** Calculates the renderer-facing thunder contribution for any immutable sample. */
    public static float thunderContribution(WeatherSample sample) {
        return (float) sample.thunderIntensity();
    }

    /** Calculates the renderer-facing air-fog contribution for any immutable sample. */
    public static float fogContribution(WeatherSample sample) {
        return (float) sample.fogContribution();
    }

    /** Returns compact immutable metadata for F3 diagnostics, or {@code null} when inactive. */
    public static ClientStateView stateView(ClientLevel level) {
        State state = activeState;
        if (level == null
                || state == null
                || !state.current().dimension().equals(level.dimension().location())) {
            return null;
        }
        return new ClientStateView(
                state.current().dimension(),
                state.current().sequence(),
                state.current().cellSize(),
                state.current().cellCount(),
                state.progress(System.nanoTime())
        );
    }

    /** Clears render state for a dimension unload while retaining connection sequence watermarks. */
    public static void clearLevel(ClientLevel level) {
        if (level == null) {
            return;
        }
        synchronized (UPDATE_LOCK) {
            State state = activeState;
            if (state != null && state.current().dimension().equals(level.dimension().location())) {
                activeState = null;
            }
        }
    }

    /** Clears all snapshots and watermarks when a client connection starts or ends. */
    public static void clearAll() {
        synchronized (UPDATE_LOCK) {
            activeState = null;
            SEQUENCE_WATERMARKS.clear();
        }
    }

    private static WeatherSample sampleAt(ClientLevel level, double blockX, double blockZ) {
        State state = activeState;
        if (level == null
                || state == null
                || !state.current().dimension().equals(level.dimension().location())) {
            return WeatherSample.CLEAR;
        }

        double amount = state.progress(System.nanoTime());
        if (amount >= 1.0D) {
            return state.current().sample(blockX, blockZ);
        }
        return WeatherSnapshot.interpolateSamples(
                state.previous().sample(blockX, blockZ),
                state.current().sample(blockX, blockZ),
                amount
        );
    }

    private static double precipitationIntensityAt(ClientLevel level, double blockX, double blockZ) {
        State state = matchingState(level);
        if (state == null) {
            return 0.0D;
        }
        double amount = state.progress(System.nanoTime());
        return lerp(
                state.previous().precipitationIntensity(blockX, blockZ),
                state.current().precipitationIntensity(blockX, blockZ),
                amount
        );
    }

    private static State matchingState(ClientLevel level) {
        State state = activeState;
        return level != null
                && state != null
                && state.current().dimension().equals(level.dimension().location())
                ? state
                : null;
    }

    private static BlockPos localPosition(ClientLevel level) {
        if (level == null) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.level() == level) {
            return minecraft.player.blockPosition();
        }
        return BlockPos.containing(minecraft.gameRenderer.getMainCamera().getPosition());
    }

    private static boolean isValidEnabledPayload(WeatherRegionSyncPayload payload) {
        return payload.cellSize() >= MIN_CELL_SIZE
                && payload.cellSize() <= MAX_CELL_SIZE
                && payload.cells().size() <= WeatherRegionSyncPayload.MAX_CELLS;
    }

    private static WeatherSnapshot matchingTarget(
            State state,
            ResourceLocation dimension,
            int cellSize
    ) {
        if (state == null
                || !state.current().dimension().equals(dimension)
                || state.current().cellSize() != cellSize) {
            return null;
        }
        return state.current();
    }

    private static WeatherSnapshot transitionSource(State state, WeatherSnapshot next, long now) {
        if (state == null
                || !state.current().dimension().equals(next.dimension())
                || state.current().cellSize() != next.cellSize()) {
            return new WeatherSnapshot(
                    next.dimension(),
                    next.dataVersion(),
                    next.sequence(),
                    next.cellSize(),
                    Map.of()
            );
        }
        return state.displayedSnapshot(now);
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * clamp01(amount);
    }

    private static PrecipitationType precipitationTypeForTemperature(double temperature) {
        return temperature <= WeatherSample.SNOW_MAX_TEMPERATURE
                ? PrecipitationType.SNOW
                : PrecipitationType.RAIN;
    }

    private record State(WeatherSnapshot previous, WeatherSnapshot current, long transitionStartNanos) {
        double progress(long now) {
            return clamp01((double) (now - transitionStartNanos) / TRANSITION_NANOS);
        }

        WeatherSnapshot displayedSnapshot(long now) {
            return WeatherSnapshot.blend(previous, current, progress(now));
        }
    }

    /** Immutable metadata exposed to the debug overlay without exposing cell maps. */
    public record ClientStateView(
            ResourceLocation dimension,
            long sequence,
            int cellSize,
            int cellCount,
            double interpolationProgress
    ) {
    }
}
