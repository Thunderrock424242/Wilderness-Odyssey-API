package com.thunder.wildernessodysseyapi.weather.storage;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.simulation.AtmosphereGrid;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * Dimension-scoped persistent owner of the authoritative atmosphere grid.
 *
 * <p>Only compact simulation continuity is saved. Client rendering state and
 * live environment caches are intentionally excluded. Loading never propagates
 * malformed atmosphere data into the level load path.</p>
 */
public final class AtmosphereSavedData extends SavedData {
    private static final String DATA_NAME = ModConstants.MOD_ID + "_atmosphere";

    private final ResourceKey<Level> dimension;
    private AtmosphereGrid grid;
    private int maxPersistedCells;
    private int loadedDataVersion;
    private int skippedCells;
    private boolean recovered;

    private AtmosphereSavedData(
            ResourceKey<Level> dimension,
            WeatherConfig.SchedulingSettings settings
    ) {
        this.dimension = dimension;
        this.grid = new AtmosphereGrid(settings.cellSize());
        this.maxPersistedCells = settings.maxPersistedCells();
        this.loadedDataVersion = AtmosphereStorageCodec.DATA_VERSION;
    }

    private AtmosphereSavedData(
            ResourceKey<Level> dimension,
            AtmosphereStorageCodec.DecodeResult decoded,
            WeatherConfig.SchedulingSettings settings
    ) {
        this.dimension = dimension;
        this.grid = decoded.grid();
        this.maxPersistedCells = settings.maxPersistedCells();
        this.loadedDataVersion = decoded.dataVersion();
        this.skippedCells = decoded.skippedCells();
        this.recovered = decoded.recovered();
        applySettings(settings);
        if (recovered) {
            setDirty();
        }
    }

    /** Returns the atmosphere state owned by this server dimension. */
    public static AtmosphereSavedData get(ServerLevel level) {
        return get(level, WeatherConfig.scheduling());
    }

    /** Returns the dimension state using an already captured config snapshot. */
    public static AtmosphereSavedData get(
            ServerLevel level,
            WeatherConfig.SchedulingSettings settings
    ) {
        WeatherConfig.SchedulingSettings safeSettings = settings == null
                ? WeatherConfig.SchedulingSettings.DEFAULT
                : settings;
        AtmosphereSavedData data = level.getDataStorage().computeIfAbsent(
                new Factory<>(
                        () -> new AtmosphereSavedData(level.dimension(), safeSettings),
                        (tag, registries) -> load(level.dimension(), tag, safeSettings)
                ),
                DATA_NAME
        );
        data.applySettings(safeSettings);
        return data;
    }

    /** Returns the server-owned grid; callers must still use its controlled APIs. */
    public AtmosphereGrid grid() {
        return grid;
    }

    /** Returns the dimension that owns this SavedData instance. */
    public ResourceKey<Level> dimension() {
        return dimension;
    }

    /** Marks a completed cell update, command edit, or trim for the next save. */
    public void markChanged() {
        setDirty();
    }

    /** Returns the version found during load for debug/migration reporting. */
    public int loadedDataVersion() {
        return loadedDataVersion;
    }

    /** Returns how many malformed or over-limit entries were ignored during load. */
    public int skippedCells() {
        return skippedCells;
    }

    /** Returns whether load recovered from missing, old, or malformed data. */
    public boolean recovered() {
        return recovered;
    }

    @Override
    public @NotNull CompoundTag save(
            @NotNull CompoundTag tag,
            HolderLookup.@NotNull Provider registries
    ) {
        return AtmosphereStorageCodec.encode(grid, maxPersistedCells);
    }

    private static AtmosphereSavedData load(
            ResourceKey<Level> dimension,
            CompoundTag tag,
            WeatherConfig.SchedulingSettings settings
    ) {
        AtmosphereStorageCodec.DecodeResult decoded = AtmosphereStorageCodec.decode(
                tag,
                settings.cellSize(),
                settings.maxPersistedCells()
        );
        return new AtmosphereSavedData(dimension, decoded, settings);
    }

    private void applySettings(WeatherConfig.SchedulingSettings settings) {
        boolean changed = grid.ensureCellSize(settings.cellSize());
        maxPersistedCells = settings.maxPersistedCells();
        int removed = grid.trimToLimit(maxPersistedCells, null);
        if (changed || removed > 0) {
            setDirty();
        }
    }
}
