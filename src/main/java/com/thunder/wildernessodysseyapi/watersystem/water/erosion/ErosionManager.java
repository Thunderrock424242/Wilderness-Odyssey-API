package com.thunder.wildernessodysseyapi.watersystem.water.erosion;

import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.surface.HydrodynamicResponse;
import com.thunder.wildernessodysseyapi.watersystem.water.surface.WaterSurfaceSampler;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Slow server-only erosion and conserved sediment response within the existing
 * water scheduler. No chunk tickets, particles, independent water state or
 * asynchronous world reads are introduced. Eligibility fails closed for old
 * terrain; construction revokes a surrounding three-by-three chunk area.
 */
public final class ErosionManager {
    private static final int MAX_CANDIDATES = 256;
    private static final Map<ServerLevel, Runtime> RUNTIMES = new IdentityHashMap<>();
    private ErosionManager() { }

    /** Performs one bounded second of optional work on the existing scheduler's level thread. */
    public static void tick(ServerLevel level) {
        if (!ErosionConfig.enabled() || !WildernessWaterRules.isEnabled(level)) {
            clear(level);
            return;
        }
        Runtime runtime = RUNTIMES.computeIfAbsent(level, ignored -> new Runtime());
        long now = level.getGameTime();
        if (now < runtime.nextTick && now >= runtime.lastTick) return;
        runtime.lastTick = now;
        runtime.nextTick = now + 20L;
        long started = System.nanoTime();
        ErosionSavedData data = ErosionSavedData.get(level);
        runtime.checks = 0;
        runtime.transfers = 0;
        int budget = ErosionConfig.checks();
        // Existing candidates receive fair repeated exposure. Discovery uses the
        // unused half of the budget so moving players can acquire new terrain.
        Iterator<Map.Entry<Long, Exposure>> iterator = runtime.exposure.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            BlockPos pos = BlockPos.of(entry.getKey());
            if (!nearPlayer(level, pos) || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null
                    || now - entry.getValue().lastSeen > 1_200L) iterator.remove();
        }
        if (!runtime.exposure.isEmpty()) {
            Long[] positions = runtime.exposure.keySet().toArray(Long[]::new);
            int count = Math.min(positions.length, budget / 2);
            for (int i = 0; i < count; i++) {
                int index = Math.floorMod(runtime.cursor++, positions.length);
                evaluate(level, data, runtime, BlockPos.of(positions[index]), now);
                runtime.checks++;
            }
        }
        int playerCount = level.players().size();
        for (; runtime.checks < budget && playerCount > 0; runtime.checks++) {
            var player = level.players().get(Math.floorMod(runtime.discovery++, playerCount));
            // Integer hash visits loaded terrain around active players without
            // materializing a list of every shoreline or evaluating global water.
            int hash = runtime.discovery * 0x9E3779B9;
            int x = player.getBlockX() + Math.floorMod(hash, 49) - 24;
            int z = player.getBlockZ() + Math.floorMod(Integer.rotateLeft(hash, 13), 49) - 24;
            LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (chunk == null || !safeChunk(level, data, chunk)) continue;
            int y = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15);
            evaluate(level, data, runtime, new BlockPos(x, y, z), now);
        }
        runtime.micros = (System.nanoTime() - started) / 1_000L;
    }

    private static void evaluate(ServerLevel level, ErosionSavedData data, Runtime runtime, BlockPos pos, long now) {
        long key = pos.asLong();
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !safeChunk(level, data, chunk) || !nearPlayer(level, pos)) {
            runtime.exposure.remove(key);
            return;
        }
        BlockState state = level.getBlockState(pos);
        float resistance = MaterialErosionRegistry.resistance(state);
        if (!Float.isFinite(resistance) || !safeSurface(level, pos)) {
            runtime.exposure.remove(key);
            return;
        }
        BlockPos water = adjacentWater(level, pos);
        if (water == null) return;
        WaterSurfaceSampler.sampleAt(level, water, 0.0f, runtime.sample);
        if (!runtime.sample.surface.valid()) return;
        long chunkKey = chunk.getPos().toLong();
        float pressure = runtime.sample.erosionPressure;
        var material = MaterialErosionRegistry.material(state);
        settleOrTransport(level, data, runtime, pos, chunkKey, now);
        Exposure exposure = runtime.exposure.get(key);
        if (exposure == null) {
            if (runtime.exposure.size() >= MAX_CANDIDATES || pressure < 0.015f) return;
            exposure = new Exposure(state, now);
            runtime.exposure.put(key, exposure);
        }
        if (exposure.original != state) {
            runtime.exposure.remove(key);
            return;
        }
        float seconds = Math.max(0L, now - exposure.lastSeen) / 20.0f;
        pressure = Math.max(pressure, exposure.impact);
        exposure.impact = 0.0f;
        exposure.lastSeen = now;
        exposure.energy = HydrodynamicResponse.accumulate(exposure.energy, pressure, seconds, resistance);
        if (exposure.energy >= resistance && data.canCredit(chunkKey, material)
                && runtime.budget.allows(now, chunkKey, ErosionConfig.changes())) {
            if (level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)) {
                data.credit(chunkKey, material);
                runtime.budget.record(now, chunkKey);
                runtime.eroded++;
                runtime.exposure.remove(key);
                WildernessFluidRegistry.notifyTerrainChanged(level, pos);
            }
            return;
        }
    }

    private static void settleOrTransport(ServerLevel level, ErosionSavedData data, Runtime runtime,
                                          BlockPos floor, long chunk, long now) {
        var material = data.available(chunk);
        if (material == null) return;
        var surface = runtime.sample.surface;
        if (runtime.sample.speed > 0.20f) {
            if (runtime.transfers >= 1) return;
            int dx = Math.abs(surface.currentX()) >= Math.abs(surface.currentZ()) ? (int) Math.signum(surface.currentX()) : 0;
            int dz = dx == 0 ? (int) Math.signum(surface.currentZ()) : 0;
            LevelChunk downstream = level.getChunkSource().getChunkNow(ChunkPos.getX(chunk) + dx, ChunkPos.getZ(chunk) + dz);
            if (downstream != null && safeChunk(level, data, downstream)
                    && data.transfer(chunk, downstream.getPos().toLong(), material)) runtime.transfers++;
            return;
        }
        BlockPos deposit = floor.above();
        BlockState previous = level.getBlockState(deposit);
        // Shallow deposits displace finite water through its existing owner.
        // Hosted water, plants and occupied terrain cannot receive sediment.
        if (runtime.sample.erosionPressure > 0.04f || !openWaterOrAir(level, deposit)
                || !level.getEntities(null, new net.minecraft.world.phys.AABB(deposit)).isEmpty()
                || !level.getBlockState(deposit.above()).isAir()
                || !runtime.budget.allows(now, chunk, ErosionConfig.changes())) return;
        if (level.setBlock(deposit, material.depositState(), 3)) {
            CanonicalWater.displaceForSolidPlacement(level, deposit, previous, material.depositState());
            data.spend(chunk, material);
            runtime.budget.record(now, chunk);
            runtime.deposited++;
            WildernessFluidRegistry.notifyTerrainChanged(level, deposit);
        }
    }

    private static boolean safeChunk(ServerLevel level, ErosionSavedData data, LevelChunk chunk) {
        // The starter bunker is placed by the story pipeline, outside ordinary
        // structure starts. Read its durable bounds independently of spawn rules.
        var bunker = com.thunder.wildernessodysseyapi.worldgen.spawn.CryoSpawnData.get(level)
                .getStarterBunkerBounds().orElse(null);
        if (bunker != null) {
            int x = chunk.getPos().getMinBlockX();
            int z = chunk.getPos().getMinBlockZ();
            if (bunker.maxX + 16 > x && bunker.minX - 16 < x + 16
                    && bunker.maxZ + 16 > z && bunker.minZ - 16 < z + 16) return false;
        }
        return data.eligible(chunk.getPos().toLong()) && chunk.getAllStarts().isEmpty()
                && chunk.getAllReferences().isEmpty() && chunk.getBlockEntities().isEmpty();
    }

    private static boolean safeSurface(ServerLevel level, BlockPos pos) {
        // Never undercut solid supports or mutate at an unloaded boundary.
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.hasChunkAt(neighbor)) return false;
            BlockState state = level.getBlockState(neighbor);
            if (state.hasBlockEntity() || state.is(MaterialErosionRegistry.IMMUNE)) return false;
        }
        return openWaterOrAir(level, pos.above())
                && level.getBlockState(pos.below()).isCollisionShapeFullBlock(level, pos.below());
    }

    private static boolean openWaterOrAir(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority
                .isPlainWaterProjection(state);
    }

    private static BlockPos adjacentWater(ServerLevel level, BlockPos pos) {
        if (CanonicalWater.isWater(level, pos.above())) return pos.above();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos water = pos.relative(direction);
            if (com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority.isWaterAt(level, water)) return water;
        }
        return null;
    }

    private static boolean nearPlayer(ServerLevel level, BlockPos pos) {
        for (var player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 48.0 * 48.0) return true;
        }
        return false;
    }

    /** Releases transient pressure on dimension unload or disable. */
    public static void clear(ServerLevel level) { RUNTIMES.remove(level); }

    /** Accepts one aggregate server-known impact; individual SPH particles never edit terrain. */
    public static void offerImpact(ServerLevel level, BlockPos position, float strength) {
        Runtime runtime = RUNTIMES.get(level);
        if (runtime == null || !ErosionConfig.enabled()) return;
        float energy = HydrodynamicResponse.unit(strength);
        for (var entry : runtime.exposure.entrySet()) {
            BlockPos candidate = BlockPos.of(entry.getKey());
            if (candidate.distSqr(position) <= 9.0) entry.getValue().impact = Math.max(entry.getValue().impact, energy);
        }
    }

    /** Compact read-only profiling for the existing water debug command. */
    public static String diagnostics(ServerLevel level) {
        Runtime runtime = RUNTIMES.get(level);
        return runtime == null ? "erosion inactive" : "erosion candidates=" + runtime.exposure.size()
                + ", checks=" + runtime.checks + ", eroded=" + runtime.eroded
                + ", deposited=" + runtime.deposited + ", cpuMicros=" + runtime.micros;
    }

    private static final class Runtime {
        private final Map<Long, Exposure> exposure = new LinkedHashMap<>();
        private final ErosionBudget budget = new ErosionBudget();
        private final WaterSurfaceSampler.Sample sample = new WaterSurfaceSampler.Sample();
        private long nextTick, lastTick, eroded, deposited, micros;
        private int cursor, discovery, checks, transfers;
    }

    private static final class Exposure {
        private final BlockState original;
        private float energy;
        private float impact;
        private long lastSeen;
        private Exposure(BlockState original, long now) { this.original = original; lastSeen = now; }
    }
}
