package com.thunder.wildernessodysseyapi.weather.storage;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemStorageCodec;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemTracker;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/** Dimension-scoped persistent owner of moving storm and front identities. */
public final class WeatherSystemsSavedData extends SavedData {
    private static final String DATA_NAME = ModConstants.MOD_ID + "_weather_systems";

    private final WeatherSystemTracker tracker = new WeatherSystemTracker();
    private int maximumSystems;

    private WeatherSystemsSavedData(WeatherConfig.FeatureSettings settings) {
        maximumSystems = settings.maximumWeatherSystems();
    }

    private WeatherSystemsSavedData(
            WeatherSystemStorageCodec.DecodeResult decoded,
            WeatherConfig.FeatureSettings settings
    ) {
        maximumSystems = settings.maximumWeatherSystems();
        tracker.restore(decoded.nextId(), decoded.systems());
        if (decoded.recovered()) {
            setDirty();
        }
    }

    /** Returns the saved tracker for this dimension. */
    public static WeatherSystemsSavedData get(ServerLevel level, WeatherConfig.FeatureSettings settings) {
        WeatherConfig.FeatureSettings safe = settings == null
                ? WeatherConfig.FeatureSettings.DEFAULT : settings;
        WeatherSystemsSavedData data = level.getDataStorage().computeIfAbsent(
                new Factory<>(
                        () -> new WeatherSystemsSavedData(safe),
                        (tag, registries) -> new WeatherSystemsSavedData(
                                WeatherSystemStorageCodec.decode(tag, safe.maximumWeatherSystems()), safe
                        )
                ),
                DATA_NAME
        );
        data.maximumSystems = safe.maximumWeatherSystems();
        return data;
    }

    /** Returns the mutable server-owned tracker. */
    public WeatherSystemTracker tracker() {
        return tracker;
    }

    /** Marks a lifecycle update for persistence. */
    public void markChanged() {
        setDirty();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        return WeatherSystemStorageCodec.encode(tracker, maximumSystems);
    }
}
