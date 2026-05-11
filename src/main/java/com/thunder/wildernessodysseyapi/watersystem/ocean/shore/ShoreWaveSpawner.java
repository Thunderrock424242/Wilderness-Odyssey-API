package com.thunder.wildernessodysseyapi.watersystem.ocean.shore;

import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Adds small, capped SPH pulses at ocean shorelines while tides are moving.
 * The actual ocean remains vanilla water; this only creates local shore wash.
 */
@EventBusSubscriber(modid = "wildernessodysseyapi")
public final class ShoreWaveSpawner {

    private static final boolean ENABLED = false;

    private static final int SEA_LEVEL = 62;
    private static final int TICK_INTERVAL = 160;
    private static final int SCAN_RANGE = 18;
    private static final int SAMPLE_STEP = 10;
    private static final int MAX_SPAWNS_PER_PLAYER = 1;
    private static final int SHORE_COOLDOWN_TICKS = 300;

    private static final Random RANDOM = new Random();
    private static final Map<ResourceKey<Level>, Integer> tickCounters = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<Long, Long>> cooldowns = new HashMap<>();

    private ShoreWaveSpawner() {}

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!ENABLED) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.players().isEmpty()) return;

        ResourceKey<Level> key = level.dimension();
        int counter = tickCounters.getOrDefault(key, 0) + 1;
        tickCounters.put(key, counter);
        if (counter < TICK_INTERVAL) return;
        tickCounters.put(key, 0);

        float tideRate = TideSystem.getTideRate(level);
        float tideMotion = Math.min(1.0f, Math.abs(tideRate) * 45.0f);
        if (tideMotion < 0.20f) return;

        Map<Long, Long> levelCooldowns = cooldowns.computeIfAbsent(key, ignored -> new HashMap<>());
        long now = level.getGameTime();
        if (levelCooldowns.size() > 2048) {
            levelCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        }

        level.players().forEach(player -> spawnNearPlayer(level, player.blockPosition(), tideRate, tideMotion, levelCooldowns, now));
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ResourceKey<Level> key = level.dimension();
        tickCounters.remove(key);
        cooldowns.remove(key);
    }

    private static void spawnNearPlayer(ServerLevel level, BlockPos origin, float tideRate, float tideMotion,
                                        Map<Long, Long> levelCooldowns, long now) {
        int spawned = 0;
        int offsetX = RANDOM.nextInt(SAMPLE_STEP);
        int offsetZ = RANDOM.nextInt(SAMPLE_STEP);
        float spawnChance = 0.08f + tideMotion * 0.18f;

        for (int dx = -SCAN_RANGE + offsetX; dx <= SCAN_RANGE && spawned < MAX_SPAWNS_PER_PLAYER; dx += SAMPLE_STEP) {
            for (int dz = -SCAN_RANGE + offsetZ; dz <= SCAN_RANGE && spawned < MAX_SPAWNS_PER_PLAYER; dz += SAMPLE_STEP) {
                if (RANDOM.nextFloat() > spawnChance) continue;

                ShoreCandidate candidate = findShoreCandidate(level, origin.getX() + dx, origin.getZ() + dz);
                if (candidate == null) continue;

                long cooldownKey = cooldownKey(candidate.surface());
                if (levelCooldowns.getOrDefault(cooldownKey, 0L) > now) continue;

                spawnShorePulse(level, candidate, tideRate, tideMotion);
                levelCooldowns.put(cooldownKey, now + SHORE_COOLDOWN_TICKS);
                spawned++;
            }
        }
    }

    private static ShoreCandidate findShoreCandidate(ServerLevel level, int x, int z) {
        BlockPos surface = findTopWaterSurface(level, x, z);
        if (surface == null) return null;
        if (WaterBodyClassifier.classify(level, surface) != WaterBodyClassifier.WaterType.OCEAN) return null;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos shore = surface.relative(direction);
            if (isShoreBlock(level, shore)) {
                return new ShoreCandidate(surface, direction);
            }
        }

        return null;
    }

    private static BlockPos findTopWaterSurface(ServerLevel level, int x, int z) {
        for (int y = SEA_LEVEL + 3; y >= SEA_LEVEL - 3; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.hasChunkAt(pos)) return null;
            if (level.getFluidState(pos).is(Fluids.WATER)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean isShoreBlock(ServerLevel level, BlockPos pos) {
        if (level.getFluidState(pos).is(Fluids.WATER)) return false;

        BlockState state = level.getBlockState(pos);
        if (!state.getCollisionShape(level, pos).isEmpty()) {
            return true;
        }

        BlockPos below = pos.below();
        if (level.getFluidState(below).is(Fluids.WATER)) return false;
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    private static void spawnShorePulse(ServerLevel level, ShoreCandidate candidate, float tideRate, float tideMotion) {
        Direction shoreDirection = candidate.shoreDirection();
        float flowSign = tideRate >= 0.0f ? 1.0f : -0.65f;
        float horizontalImpulse = (0.18f + tideMotion * 0.28f) * flowSign;

        float spawnX = candidate.surface().getX() + 0.5f + shoreDirection.getStepX() * 0.48f;
        float spawnY = candidate.surface().getY() + 1.02f;
        float spawnZ = candidate.surface().getZ() + 0.5f + shoreDirection.getStepZ() * 0.48f;

        int particleCount = Math.max(8, Math.round(SPHConstants.SHORE_WAVE_PARTICLES * (0.55f + tideMotion * 0.45f)));
        float impulseX = shoreDirection.getStepX() * horizontalImpulse;
        float impulseZ = shoreDirection.getStepZ() * horizontalImpulse;

        SPHSimulationManager.get().createTransientSimulation(
                spawnX, spawnY, spawnZ,
                level,
                particleCount,
                impulseX, -0.35f, impulseZ,
                SPHConstants.SHORE_WAVE_LIFETIME_TICKS
        );
    }

    private static long cooldownKey(BlockPos pos) {
        int cellX = pos.getX() >> 2;
        int cellZ = pos.getZ() >> 2;
        return ((long) cellX & 0xFFFFFFFFL) | (((long) cellZ & 0xFFFFFFFFL) << 32);
    }

    private record ShoreCandidate(BlockPos surface, Direction shoreDirection) {}
}
