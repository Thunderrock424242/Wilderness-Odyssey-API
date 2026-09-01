package com.thunder.wildernessodysseyapi.environment.glacial;

import com.thunder.wildernessodysseyapi.environment.glacial.config.GlacialConfig;
import com.thunder.wildernessodysseyapi.weather.api.SeasonalClimateState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherServices;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Server authority for cached glacial season state and temporary development overrides. */
public final class GlacialSeasonManager {

    private static final long CACHE_TICKS = 100L;
    private static final Map<ServerLevel, CachedState> STATES = new ConcurrentHashMap<>();
    private static final Map<ServerLevel, GlacialSeason> OVERRIDES = new ConcurrentHashMap<>();

    private GlacialSeasonManager() {
    }

    /** Samples at most once per cache window while preserving exact debug overrides. */
    public static GlacialSeasonSnapshot sample(ServerLevel level, BlockPos position) {
        if (level == null) {
            return GlacialSeasonSnapshot.POLAR_COLD;
        }
        GlacialSeason override = OVERRIDES.get(level);
        if (override != null) {
            return GlacialSeasonModel.override(override);
        }
        if (!GlacialConfig.ENABLE_SEASONAL_GLACIAL_EFFECTS.get()) {
            return GlacialSeasonSnapshot.POLAR_COLD;
        }

        long gameTime = level.getGameTime();
        CachedState cached = STATES.get(level);
        if (cached != null && gameTime >= cached.sampledAtTick()
                && gameTime - cached.sampledAtTick() < CACHE_TICKS) {
            return cached.snapshot();
        }
        BlockPos samplePosition = position == null
                ? new BlockPos(0, level.getSeaLevel(), 0) : position;
        SeasonalClimateState climate = WeatherServices.query().seasonalClimateAt(level, samplePosition);
        GlacialSeasonSnapshot snapshot = GlacialSeasonModel.evaluate(climate);
        STATES.put(level, new CachedState(gameTime, snapshot));
        return snapshot;
    }

    /** Sets a non-persistent Wilderness-only simulation override. */
    public static void setDebugOverride(ServerLevel level, GlacialSeason season) {
        if (level == null || season == null) {
            return;
        }
        OVERRIDES.put(level, season);
        STATES.remove(level);
    }

    /** Clears a Wilderness-only override without changing Ecliptic or Serene state. */
    public static boolean clearDebugOverride(ServerLevel level) {
        if (level == null) {
            return false;
        }
        STATES.remove(level);
        return OVERRIDES.remove(level) != null;
    }

    /** Returns the selected external calendar name for diagnostics. */
    public static String calendarSource() {
        ModList mods = ModList.get();
        if (mods.isLoaded("eclipticseasons")) {
            return "Ecliptic Seasons";
        }
        if (mods.isLoaded("sereneseasons")) {
            return "Serene Seasons";
        }
        return "static polar fallback";
    }

    /** Releases one dimension's ephemeral cache and override. */
    public static void clearLevel(ServerLevel level) {
        STATES.remove(level);
        OVERRIDES.remove(level);
    }

    /** Releases all ephemeral state after the server saves and stops. */
    public static void clearAll() {
        STATES.clear();
        OVERRIDES.clear();
    }

    private record CachedState(long sampledAtTick, GlacialSeasonSnapshot snapshot) {
    }
}
