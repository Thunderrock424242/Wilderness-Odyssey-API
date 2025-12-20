package com.thunder.wildernessodysseyapi.tide;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Math.PI;

/**
 * Simulates a simple ocean/river tide that can be queried by other systems and gently influences entities in water.
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
            TideState state = TIDE_STATES.computeIfAbsent(level.dimension(), key -> new TideState());
            long dayTime = level.getDayTime();
            double newHeight = computeNormalizedHeight(dayTime, cachedConfig);
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

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!cachedConfig.enabled() || !entity.isInWaterOrBubble()) {
            return;
        }

        TideSnapshot snapshot = snapshot(serverLevel);
        double amplitude = getLocalAmplitude(serverLevel, entity.blockPosition());
        if (amplitude <= 0.0D) {
            return;
        }

        double verticalForce = snapshot.verticalChangePerTick() * amplitude * cachedConfig.currentStrength();
        if (Math.abs(verticalForce) < 1.0E-5) {
            return;
        }

        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.x, motion.y + verticalForce, motion.z);
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
