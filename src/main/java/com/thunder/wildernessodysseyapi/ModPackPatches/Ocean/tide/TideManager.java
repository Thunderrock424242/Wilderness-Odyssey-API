package com.thunder.wildernessodysseyapi.ModPackPatches.Ocean.tide;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Math.PI;

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
        Holder<Biome> biomeHolder = level.getBiome(pos);
        if (biomeHolder.is(BiomeTags.IS_OCEAN)) {
            return cachedConfig.oceanAmplitudeBlocks();
        }
        if (biomeHolder.is(BiomeTags.IS_RIVER)) {
            return cachedConfig.riverAmplitudeBlocks();
        }
        return 0.0D;
    }

    public static double getMoonPhaseAmplitudeFactor(ServerLevel level) {
        return switch (level.getMoonPhase()) {
            case 0 -> 1.2D;
            case 1, 7 -> 1.1D;
            case 2, 6 -> 1.0D;
            case 3, 5 -> 0.9D;
            case 4 -> 0.8D;
            default -> 1.0D;
        };
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
            double moonFactor = getMoonPhaseAmplitudeFactor(level);
            double newHeight = computeNormalizedHeight(dayTime, cachedConfig) * moonFactor;
            state.update(dayTime, newHeight);
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

    private static double computeNormalizedHeight(long dayTime, TideConfig.TideConfigValues config) {
        long cycleTicks = Math.max(1L, config.cycleTicks());
        long adjustedTime = (dayTime + config.phaseOffsetTicks()) % cycleTicks;
        double phase = (adjustedTime / (double) cycleTicks) * 2.0D * PI;
        return Math.sin(phase);
    }

    /**
     * Lightweight state container tracked per dimension.
     */
    private static final class TideState {
        private long lastUpdateTick = -1L;
        private double normalizedHeight = 0.0D;
        private double changePerTick = 0.0D;

        void update(long worldTick, double newHeight) {
            if (lastUpdateTick >= 0L) {
                long delta = Math.max(1L, worldTick - lastUpdateTick);
                changePerTick = (newHeight - normalizedHeight) / delta;
            }
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
