package com.thunder.wildernessodysseyapi.weather.networking;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereView;
import com.thunder.wildernessodysseyapi.weather.simulation.AtmosphereGrid;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Tracks and publishes the relevant authoritative weather region per player.
 *
 * <p>This manager is server-thread confined. It sends a complete replacement
 * after login, a dimension or atmospheric-cell move, and explicit invalidation.
 * While a player remains in the same region, only cells with newer revisions
 * are sent. No matching client-to-server payload exists.</p>
 */
public final class WeatherSnapshotManager {

    private static final Map<ServerPlayer, PlayerSyncState> PLAYER_STATES = new WeakHashMap<>();
    private static final Comparator<AtmosphereView> CELL_ORDER = Comparator
            .comparingInt((AtmosphereView view) -> view.key().x())
            .thenComparingInt(view -> view.key().z());

    // One server-wide source guarantees that a reconnecting player never sees
    // a lower sequence while the same logical server process remains active.
    private static long nextSequence = 1L;

    private WeatherSnapshotManager() {
    }

    /**
     * Synchronizes every player in a level from one immutable grid view.
     *
     * @param level authoritative level whose players should receive snapshots
     * @param grid dimension atmosphere grid; required only while enabled
     * @param enabled whether localized weather is enabled for this dimension
     * @param cellSize configured atmospheric cell width in blocks
     * @param radiusCells nearby radius including the client interpolation ring
     */
    public static void syncLevel(
            ServerLevel level,
            AtmosphereGrid grid,
            boolean enabled,
            int cellSize,
            int radiusCells
    ) {
        Objects.requireNonNull(level, "level");
        validateRegionSettings(cellSize, radiusCells);
        if (enabled) {
            Objects.requireNonNull(grid, "grid");
            if (grid.cellSize() != cellSize) {
                throw new IllegalArgumentException("Snapshot cell size does not match the atmosphere grid");
            }
        }
        for (ServerPlayer player : level.players()) {
            syncPlayer(player, grid, enabled, cellSize, radiusCells);
        }
    }

    /**
     * Synchronizes one player immediately, such as after login or teleport.
     *
     * @param player receiving server player
     * @param grid player's current dimension atmosphere grid
     * @param enabled whether localized weather is enabled for this dimension
     * @param cellSize configured atmospheric cell width in blocks
     * @param radiusCells nearby radius including the interpolation ring
     */
    public static void syncPlayer(
            ServerPlayer player,
            AtmosphereGrid grid,
            boolean enabled,
            int cellSize,
            int radiusCells
    ) {
        Objects.requireNonNull(player, "player");
        validateRegionSettings(cellSize, radiusCells);
        if (enabled) {
            Objects.requireNonNull(grid, "grid");
            if (grid.cellSize() != cellSize) {
                throw new IllegalArgumentException("Snapshot cell size does not match the atmosphere grid");
            }
        }

        ResourceKey<Level> dimension = player.level().dimension();
        AtmosphereCellKey center = AtmosphereCellKey.fromBlock(
                player.getBlockX(),
                player.getBlockZ(),
                cellSize
        );
        PlayerSyncState state = PLAYER_STATES.computeIfAbsent(player, ignored -> new PlayerSyncState());
        boolean dimensionChanged = !dimension.equals(state.dimension);
        boolean settingsChanged = state.cellSize != cellSize || state.radiusCells != radiusCells;
        if (dimensionChanged) {
            state.enterDimension(dimension);
        }
        if (settingsChanged) {
            state.forceFull = true;
        }
        state.cellSize = cellSize;
        state.radiusCells = radiusCells;

        if (!enabled) {
            syncDisabledPlayer(player, center, state);
            return;
        }

        boolean centerChanged = !state.hasCenter
                || center.x() != state.centerCellX
                || center.z() != state.centerCellZ;
        boolean replaceRegion = state.forceFull || state.disabledSent || centerChanged;

        List<AtmosphereView> views = new ArrayList<>(grid.viewsInRegion(center, radiusCells));
        if (views.size() > WeatherRegionSyncPayload.MAX_CELLS) {
            throw new IllegalStateException("Atmosphere grid returned an oversized player region: " + views.size());
        }
        views.sort(CELL_ORDER);

        Map<Long, Long> currentRevisions = new HashMap<>(views.size());
        for (AtmosphereView view : views) {
            Long duplicate = currentRevisions.put(view.key().packed(), view.revision());
            if (duplicate != null) {
                throw new IllegalStateException("Atmosphere grid returned duplicate cells for one player region");
            }
        }

        // A removed cell has no delta tombstone in schema version one. Replace
        // the region in that uncommon case so stale client cells cannot linger.
        if (!replaceRegion && state.containsCellMissingFrom(currentRevisions)) {
            replaceRegion = true;
        }

        List<WeatherRegionSyncPayload.CellSnapshot> cells = new ArrayList<>(views.size());
        for (AtmosphereView view : views) {
            long previousRevision = state.revisions.getOrDefault(view.key().packed(), Long.MIN_VALUE);
            if (replaceRegion || previousRevision != view.revision()) {
                cells.add(WeatherRegionSyncPayload.CellSnapshot.fromView(view));
            }
        }
        if (!replaceRegion && cells.isEmpty()) {
            state.rememberCenter(center);
            return;
        }

        WeatherRegionSyncPayload payload = new WeatherRegionSyncPayload(
                dimension.location(),
                WeatherRegionSyncPayload.DATA_VERSION,
                reserveSequence(),
                true,
                replaceRegion,
                cellSize,
                center.x(),
                center.z(),
                cells
        );
        PacketDistributor.sendToPlayer(player, payload);

        if (replaceRegion) {
            state.revisions.clear();
        }
        for (AtmosphereView view : views) {
            if (replaceRegion || state.revisions.getOrDefault(view.key().packed(), Long.MIN_VALUE)
                    != view.revision()) {
                state.revisions.put(view.key().packed(), view.revision());
            }
        }
        state.rememberCenter(center);
        state.forceFull = false;
        state.disabledSent = false;
    }

    /**
     * Sends one explicit empty reset per player after weather is disabled.
     *
     * <p>Further disabled synchronization passes are silent until the level is
     * dirtied, the player changes dimension, or weather becomes enabled and is
     * disabled again.</p>
     */
    public static void syncDisabled(ServerLevel level, int cellSize, int radiusCells) {
        syncLevel(level, null, false, cellSize, radiusCells);
    }

    /** Forces a complete region for one player on the next enabled sync. */
    public static void markPlayerDirty(ServerPlayer player) {
        PlayerSyncState state = PLAYER_STATES.get(Objects.requireNonNull(player, "player"));
        if (state != null) {
            state.forceFull = true;
        }
    }

    /** Forces complete regions for tracked players in one dimension. */
    public static void markLevelDirty(ResourceKey<Level> dimension) {
        Objects.requireNonNull(dimension, "dimension");
        for (PlayerSyncState state : PLAYER_STATES.values()) {
            if (dimension.equals(state.dimension)) {
                state.forceFull = true;
            }
        }
    }

    /** Removes retained synchronization state when one player disconnects. */
    public static void forgetPlayer(ServerPlayer player) {
        PLAYER_STATES.remove(Objects.requireNonNull(player, "player"));
    }

    /** Removes retained synchronization state when one server level unloads. */
    public static void clearLevel(ResourceKey<Level> dimension) {
        Objects.requireNonNull(dimension, "dimension");
        PLAYER_STATES.entrySet().removeIf(entry -> dimension.equals(entry.getValue().dimension));
    }

    /** Clears every retained player snapshot, primarily during server shutdown. */
    public static void clear() {
        PLAYER_STATES.clear();
    }

    private static void syncDisabledPlayer(
            ServerPlayer player,
            AtmosphereCellKey center,
            PlayerSyncState state
    ) {
        if (state.disabledSent && !state.forceFull) {
            state.rememberCenter(center);
            return;
        }

        PacketDistributor.sendToPlayer(player, WeatherRegionSyncPayload.disabled(
                state.dimension.location(),
                reserveSequence(),
                state.cellSize,
                center.x(),
                center.z()
        ));
        state.revisions.clear();
        state.rememberCenter(center);
        state.forceFull = false;
        state.disabledSent = true;
    }

    private static void validateRegionSettings(int cellSize, int radiusCells) {
        if (cellSize < 16 || cellSize > 4_096) {
            throw new IllegalArgumentException("Invalid atmospheric cell size: " + cellSize);
        }
        if (radiusCells < 0 || radiusCells > WeatherRegionSyncPayload.MAX_CELL_OFFSET) {
            throw new IllegalArgumentException("Invalid atmospheric synchronization radius: " + radiusCells);
        }
        int diameter = radiusCells * 2 + 1;
        if (diameter * diameter > WeatherRegionSyncPayload.MAX_CELLS) {
            throw new IllegalArgumentException("Atmospheric synchronization radius exceeds payload capacity");
        }
    }

    private static long reserveSequence() {
        if (nextSequence == Long.MAX_VALUE) {
            throw new IllegalStateException("Weather snapshot sequence space exhausted");
        }
        return nextSequence++;
    }

    private static final class PlayerSyncState {
        private ResourceKey<Level> dimension;
        private int centerCellX;
        private int centerCellZ;
        private int cellSize = -1;
        private int radiusCells = -1;
        private boolean hasCenter;
        private boolean forceFull = true;
        private boolean disabledSent;
        private final Map<Long, Long> revisions = new HashMap<>();

        private void enterDimension(ResourceKey<Level> newDimension) {
            dimension = newDimension;
            hasCenter = false;
            forceFull = true;
            disabledSent = false;
            revisions.clear();
        }

        private void rememberCenter(AtmosphereCellKey center) {
            centerCellX = center.x();
            centerCellZ = center.z();
            hasCenter = true;
        }

        private boolean containsCellMissingFrom(Map<Long, Long> currentRevisions) {
            for (long cellKey : revisions.keySet()) {
                if (!currentRevisions.containsKey(cellKey)) {
                    return true;
                }
            }
            return false;
        }
    }
}
