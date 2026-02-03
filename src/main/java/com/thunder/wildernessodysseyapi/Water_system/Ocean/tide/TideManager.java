package com.thunder.wildernessodysseyapi.Water_system.Ocean.tide;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.concurrent.ConcurrentHashMap;


/**
 * Simulates a simple ocean/river tide (distinct from any wave simulation) that can be queried by other systems and
 * gently influences entities in water.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class TideManager {
    private static final Map<ResourceKey<Level>, TideState> TIDE_STATES = new ConcurrentHashMap<>();

    private static TideConfig.TideConfigValues cachedConfig = TideConfig.defaultValues();

    private TideManager() {
    }

    /**
     * Called when config reloads to refresh cached values.
     */
    public static void reloadConfig() {
        cachedConfig = loadConfigWithFallback();
    }

    /**
     * Returns the latest tide snapshot for the given level.
     */
    public static TideSnapshot snapshot(ServerLevel level) {
        TideState state = TIDE_STATES.computeIfAbsent(level.dimension(), key -> new TideState());
        return new TideSnapshot(state.normalizedHeight, state.changePerTick, cachedConfig.cycleTicks());
    }

    /**
     * Returns the amplitude (in blocks) for the biome at the given position.
     */
    public static double getLocalAmplitude(ServerLevel level, BlockPos pos) {
        return TideAmplitudeHelper.getLocalAmplitude(level, pos, cachedConfig);
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        TIDE_STATES.clear();
        cachedConfig = loadConfigWithFallback();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TIDE_STATES.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!event.hasTime()) {
            return;
        }
        if (!cachedConfig.enabled()) {
            return;
        }

        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            if (level.players().isEmpty()) {
                continue;
            }
            TideState state = TIDE_STATES.computeIfAbsent(level.dimension(), key -> new TideState());
            long dayTime = level.getDayTime();
            TideAstronomy.TideSample sample = TideAstronomy.computeTideSample(dayTime, level, cachedConfig);
            state.update(dayTime, sample.height(), sample.changePerTick());
        }
    }

    private static TideConfig.TideConfigValues loadConfigWithFallback() {
        try {
            return TideConfig.values();
        } catch (IllegalStateException e) {
            ModConstants.LOGGER.warn("Tide config accessed before load; using defaults instead. ({})", e.getMessage());
            return TideConfig.defaultValues();
        }
    }

    /**
     * Lightweight state container tracked per dimension.
     */
    private static final class TideState {
        private long lastUpdateTick = -1L;
        private double normalizedHeight = 0.0D;
        private double changePerTick = 0.0D;

        void update(long worldTick, double newHeight, double newChangePerTick) {
            changePerTick = newChangePerTick;
            normalizedHeight = newHeight;
            lastUpdateTick = worldTick;
        }
    }

    /**
     * Read-only tide information for consumers.
     */
    public record TideSnapshot(double normalizedHeight, double verticalChangePerTick, long cycleTicks) {
        public boolean isRising() {
            return verticalChangePerTick > 0.0D;
        }

        public String trendDescription() {
            if (Math.abs(verticalChangePerTick) < 1.0E-5) {
                return "slack";
            }
            return isRising() ? "rising" : "falling";
        }
    }
}
