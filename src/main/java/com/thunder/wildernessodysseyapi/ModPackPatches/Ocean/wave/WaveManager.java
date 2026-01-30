package com.thunder.wildernessodysseyapi.ModPackPatches.Ocean.wave;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
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
 * Simulates surface wave motion for ocean biomes, amplified by windy weather.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class WaveManager {
    private static final Map<ResourceKey<Level>, WaveState> WAVE_STATES = new ConcurrentHashMap<>();

    private static WaveConfig.WaveConfigValues cachedConfig = WaveConfig.defaultValues();

    private WaveManager() {
    }

    /**
     * Called when config reloads to refresh cached values.
     */
    public static void reloadConfig() {
        cachedConfig = loadConfigWithFallback();
    }

    /**
     * Returns the latest wave snapshot for the given level.
     */
    public static WaveSnapshot snapshot(ServerLevel level) {
        WaveState state = WAVE_STATES.computeIfAbsent(level.dimension(), key -> new WaveState());
        return new WaveSnapshot(state.normalizedHeight, state.changePerTick, cachedConfig.wavePeriodTicks());
    }

    /**
     * Returns the wave amplitude (in blocks) for the biome at the given position.
     */
    public static double getLocalAmplitude(ServerLevel level, BlockPos pos) {
        Holder<Biome> biomeHolder = level.getBiome(pos);
        if (biomeHolder.is(BiomeTags.IS_OCEAN)) {
            return cachedConfig.baseAmplitudeBlocks();
        }
        return 0.0D;
    }

    public static double getWindAmplitudeFactor(ServerLevel level) {
        if (level.isThundering()) {
            return cachedConfig.thunderWindMultiplier();
        }
        if (level.isRaining()) {
            return cachedConfig.rainWindMultiplier();
        }
        return 1.0D;
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        WAVE_STATES.clear();
        cachedConfig = loadConfigWithFallback();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        WAVE_STATES.clear();
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
            WaveState state = WAVE_STATES.computeIfAbsent(level.dimension(), key -> new WaveState());
            long gameTime = level.getGameTime();
            double newHeight = computeNormalizedHeight(gameTime, cachedConfig);
            state.update(gameTime, newHeight);
        }
    }

    private static WaveConfig.WaveConfigValues loadConfigWithFallback() {
        try {
            return WaveConfig.values();
        } catch (IllegalStateException e) {
            ModConstants.LOGGER.warn("Wave config accessed before load; using defaults instead. ({})", e.getMessage());
            return WaveConfig.defaultValues();
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
        if (!serverLevel.hasChunkAt(entity.blockPosition())) {
            return;
        }
        BlockPos entityPos = entity.blockPosition();
        if (!serverLevel.getFluidState(entityPos).is(FluidTags.WATER)) {
            return;
        }
        if (!serverLevel.getFluidState(entityPos.above()).isEmpty()) {
            return;
        }
        double proximityBlocks = cachedConfig.playerProximityBlocks();
        if (proximityBlocks > 0.0D && serverLevel.getNearestPlayer(entity, proximityBlocks) == null) {
            return;
        }

        WaveSnapshot snapshot = snapshot(serverLevel);
        double amplitude = getLocalAmplitude(serverLevel, entityPos);
        if (amplitude <= 0.0D) {
            return;
        }
        amplitude *= getWindAmplitudeFactor(serverLevel);

        double verticalForce = snapshot.verticalChangePerTick() * amplitude * cachedConfig.currentStrength();
        if (Math.abs(verticalForce) < 1.0E-8) {
            return;
        }

        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.x, motion.y + verticalForce, motion.z);
    }

    private static double computeNormalizedHeight(long gameTime, WaveConfig.WaveConfigValues config) {
        long cycleTicks = Math.max(1L, config.wavePeriodTicks());
        long adjustedTime = gameTime % cycleTicks;
        double phase = (adjustedTime / (double) cycleTicks) * 2.0D * PI;
        return Math.sin(phase);
    }

    /**
     * Lightweight state container tracked per dimension.
     */
    private static final class WaveState {
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
     * Read-only wave information for consumers.
     */
    public record WaveSnapshot(double normalizedHeight, double verticalChangePerTick, long cycleTicks) {
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
