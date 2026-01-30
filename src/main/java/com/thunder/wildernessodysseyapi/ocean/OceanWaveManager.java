package com.thunder.wildernessodysseyapi.ocean;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.tide.TideConfig;
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
 * Simulates short ocean surface waves layered on top of the main tide cycle.
 * The logic is deliberately gameplay-only to remain shader friendly.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class OceanWaveManager {
    private static final Map<ResourceKey<Level>, WaveState> WAVE_STATES = new ConcurrentHashMap<>();
    private static TideConfig.TideConfigValues cachedConfig = TideConfig.defaultValues();

    private OceanWaveManager() {
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
    public static double getLocalWaveAmplitude(ServerLevel level, BlockPos pos) {
        if (!cachedConfig.wavesEnabled()) {
            return 0.0D;
        }
        Holder<Biome> biomeHolder = level.getBiome(pos);
        if (biomeHolder.is(BiomeTags.IS_OCEAN)) {
            return cachedConfig.waveAmplitudeBlocks();
        }
        return 0.0D;
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
        if (!cachedConfig.enabled() || !cachedConfig.wavesEnabled()) {
            return;
        }

        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            WaveState state = WAVE_STATES.computeIfAbsent(level.dimension(), key -> new WaveState());
            long gameTime = level.getGameTime();
            double newHeight = computeWaveNormalizedHeight(gameTime, cachedConfig);
            state.update(gameTime, newHeight);
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
        if (!cachedConfig.enabled() || !cachedConfig.wavesEnabled() || !entity.isInWaterOrBubble()) {
            return;
        }

        WaveSnapshot snapshot = snapshot(serverLevel);
        double amplitude = getLocalWaveAmplitude(serverLevel, entity.blockPosition());
        if (amplitude <= 0.0D) {
            return;
        }

        double verticalForce = snapshot.verticalChangePerTick() * amplitude * cachedConfig.waveStrength();
        if (Math.abs(verticalForce) < 1.0E-5) {
            return;
        }
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.x, motion.y + verticalForce, motion.z);
    }

    private static TideConfig.TideConfigValues loadConfigWithFallback() {
        try {
            return TideConfig.values();
        } catch (IllegalStateException e) {
            ModConstants.LOGGER.warn("Wave config accessed before load; using defaults instead. ({})", e.getMessage());
            return TideConfig.defaultValues();
        }
    }

    private static double computeWaveNormalizedHeight(long gameTime, TideConfig.TideConfigValues config) {
        long cycleTicks = config.wavePeriodTicks();
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
        public String trendDescription() {
            if (Math.abs(verticalChangePerTick) < 1.0E-5) {
                return "calm";
            }
            return verticalChangePerTick > 0.0D ? "cresting" : "troughing";
        }
    }
}
