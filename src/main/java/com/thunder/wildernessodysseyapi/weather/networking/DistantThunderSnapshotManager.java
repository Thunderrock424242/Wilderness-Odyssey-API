package com.thunder.wildernessodysseyapi.weather.networking;

import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.simulation.AtmosphereGrid;
import com.thunder.wildernessodysseyapi.weather.system.TrackedWeatherSystem;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Publishes changed persistent-storm summaries on the normal weather sync cadence.
 *
 * <p>Each storm center is sampled through {@link AtmosphereGrid}'s spatial lookup.
 * This avoids widening the expensive client cell region to several kilometers and
 * avoids any per-tick scan of atmospheric cells.</p>
 */
public final class DistantThunderSnapshotManager {

    private static final Map<ServerPlayer, PlayerState> PLAYER_STATES = new WeakHashMap<>();
    private static long nextSequence = 1L;

    private DistantThunderSnapshotManager() {
    }

    /** Synchronizes changed audio-facing storm state to every player in a level. */
    public static void syncLevel(
            ServerLevel level,
            AtmosphereGrid grid,
            List<TrackedWeatherSystem> systems
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(grid, "grid");
        List<DistantThunderSystemSyncPayload.StormSnapshot> snapshots = capture(grid, systems);
        for (ServerPlayer player : level.players()) {
            syncPlayer(player, true, snapshots);
        }
    }

    /** Sends one empty disabled state when Wilderness weather yields ownership. */
    public static void syncDisabled(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        for (ServerPlayer player : level.players()) {
            syncPlayer(player, false, List.of());
        }
    }

    /** Forces a lifecycle-changing player to receive the next storm summary. */
    public static void markPlayerDirty(ServerPlayer player) {
        PlayerState state = PLAYER_STATES.get(Objects.requireNonNull(player, "player"));
        if (state != null) {
            state.force = true;
        }
    }

    /** Releases retained comparison state when one player disconnects. */
    public static void forgetPlayer(ServerPlayer player) {
        PLAYER_STATES.remove(Objects.requireNonNull(player, "player"));
    }

    /** Releases retained comparison state when one server level unloads. */
    public static void clearLevel(ResourceKey<Level> dimension) {
        Objects.requireNonNull(dimension, "dimension");
        PLAYER_STATES.entrySet().removeIf(entry -> dimension.equals(entry.getValue().dimension));
    }

    /** Releases all process-scoped comparison state during server shutdown. */
    public static void clear() {
        PLAYER_STATES.clear();
    }

    private static List<DistantThunderSystemSyncPayload.StormSnapshot> capture(
            AtmosphereGrid grid,
            List<TrackedWeatherSystem> systems
    ) {
        if (systems == null || systems.isEmpty()) {
            return List.of();
        }
        List<TrackedWeatherSystem> ordered = systems.stream()
                .filter(system -> system != null && system.type().storm())
                .sorted(Comparator.comparingDouble(TrackedWeatherSystem::intensity)
                        .reversed()
                        .thenComparingLong(TrackedWeatherSystem::id))
                .limit(DistantThunderSystemSyncPayload.MAX_STORMS)
                .sorted(Comparator.comparingLong(TrackedWeatherSystem::id))
                .toList();
        List<DistantThunderSystemSyncPayload.StormSnapshot> snapshots = new ArrayList<>(ordered.size());
        for (TrackedWeatherSystem system : ordered) {
            // The grid query performs four bounded cell lookups and preserves the
            // same interpolation contract used by other weather consumers.
            WeatherSample sample = grid.sample(system.centerX(), system.centerZ());
            snapshots.add(DistantThunderSystemSyncPayload.StormSnapshot.fromSystem(system, sample));
        }
        return List.copyOf(snapshots);
    }

    private static void syncPlayer(
            ServerPlayer player,
            boolean enabled,
            List<DistantThunderSystemSyncPayload.StormSnapshot> snapshots
    ) {
        ResourceKey<Level> dimension = player.level().dimension();
        PlayerState state = PLAYER_STATES.computeIfAbsent(player, ignored -> new PlayerState());
        boolean dimensionChanged = !dimension.equals(state.dimension);
        if (!state.force
                && !dimensionChanged
                && state.enabled == enabled
                && state.snapshots.equals(snapshots)) {
            return;
        }

        DistantThunderSystemSyncPayload payload = enabled
                ? new DistantThunderSystemSyncPayload(
                        dimension.location(),
                        DistantThunderSystemSyncPayload.DATA_VERSION,
                        reserveSequence(),
                        true,
                        snapshots
                )
                : DistantThunderSystemSyncPayload.disabled(dimension.location(), reserveSequence());
        PacketDistributor.sendToPlayer(player, payload);
        state.dimension = dimension;
        state.enabled = enabled;
        state.snapshots = snapshots;
        state.force = false;
    }

    private static long reserveSequence() {
        if (nextSequence == Long.MAX_VALUE) {
            throw new IllegalStateException("Distant-thunder snapshot sequence space exhausted");
        }
        return nextSequence++;
    }

    private static final class PlayerState {
        private ResourceKey<Level> dimension;
        private boolean enabled;
        private boolean force = true;
        private List<DistantThunderSystemSyncPayload.StormSnapshot> snapshots = List.of();
    }
}
