package com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;

/**
 * Non-loading admission and shape reads for server-owned water mutations.
 * Sable 2.0.6 reclassifies adjacent voxels and their neighbors for each write;
 * a two-block horizontal halo therefore has to be available before callbacks.
 */
public final class WaterMutationSafety {
    public static final int CALLBACK_RADIUS = 2;
    private static final ThreadLocal<BlockGetter> SHAPE_READER = new ThreadLocal<>();
    private static final Map<ServerLevel, Measurements> MEASUREMENTS = new WeakHashMap<>();

    private WaterMutationSafety() { }

    /** Never creates a chunk ticket or waits for a future. */
    public static boolean isReady(ServerLevel level, BlockPos pos, int radius) {
        if (!level.getServer().isSameThread() || level.getServer().isStopped()
                || level.isOutsideBuildHeight(pos)) return false;
        boolean ready = hasNeighborhood(pos.getX(), pos.getZ(), radius,
                (x, z) -> level.getChunkSource().getChunkNow(x, z) != null);
        if (!ready) measurements(level).availabilityFailures++;
        return ready;
    }

    /** Checks only the chunk columns touched by the callback footprint. */
    public static boolean hasNeighborhood(int x, int z, int radius, BiPredicate<Integer, Integer> loaded) {
        for (int chunkX = (x - radius) >> 4; chunkX <= (x + radius) >> 4; chunkX++) {
            for (int chunkZ = (z - radius) >> 4; chunkZ <= (z + radius) >> 4; chunkZ++) {
                if (!loaded.test(chunkX, chunkZ)) return false;
            }
        }
        return true;
    }

    /** Actual loaded shape context, present only inside a water-owned block write. */
    @Nullable
    public static BlockGetter shapeReader() { return SHAPE_READER.get(); }

    /** Retains normal Minecraft flags, neighbor notifications and physics callbacks. */
    public static boolean setBlock(ServerLevel level, BlockPos pos, BlockState state, int flags) {
        if (!isReady(level, pos, CALLBACK_RADIUS)) {
            measurements(level).deferred++;
            return false;
        }
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4).getBlockState(pos).equals(state)) {
            measurements(level).deduplicated++;
            return true;
        }
        long started = System.nanoTime();
        try {
            boolean changed = withLoadedShapeContext(level, () -> level.setBlock(pos, state, flags));
            if (changed) measurements(level).projections++;
            return changed;
        } finally {
            long duration = System.nanoTime() - started;
            Measurements measurement = measurements(level);
            measurement.mutationNanos += duration;
            measurement.maxMutationNanos = Math.max(measurement.maxMutationNanos, duration);
            if (duration >= 50_000_000L && WaterSimulationConfig.watershedDebugLoggingEnabled()
                    && level.getGameTime() - measurement.lastWarningTick >= 200) {
                measurement.lastWarningTick = level.getGameTime();
                ModConstants.LOGGER.warn("Slow water block mutation: dimension={}, position={}, state={}, microseconds={}",
                        level.dimension().location(), pos, state, duration / 1_000);
            }
        }
    }

    /** Scopes a synchronous admitted mutation; nested dimension contexts restore their caller. */
    public static boolean withLoadedShapeContext(ServerLevel level, BooleanSupplier mutation) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Water mutations require the server thread");
        BlockGetter previous = SHAPE_READER.get();
        SHAPE_READER.set(previous instanceof LoadedShapeReader reader && reader.level() == level
                ? previous : new LoadedShapeReader(level));
        try {
            return mutation.getAsBoolean();
        } finally {
            if (previous == null) SHAPE_READER.remove();
            else SHAPE_READER.set(previous);
        }
    }

    public static void deduplicated(ServerLevel level) { measurements(level).deduplicated++; }
    public static void deferred(ServerLevel level) { measurements(level).deferred++; }

    /** Starts one bounded local-flow pass without losing the lifetime maxima. */
    public static void beginTick(ServerLevel level) {
        Measurements measurement = measurements(level);
        measurement.projections = measurement.deferred = measurement.deduplicated = 0;
        measurement.availabilityFailures = measurement.mutationNanos = 0;
    }

    public static void finishTick(ServerLevel level, long nanos) {
        Measurements measurement = measurements(level);
        measurement.maxTickNanos = Math.max(measurement.maxTickNanos, nanos);
    }

    public static String diagnostics(ServerLevel level) {
        Measurements m = measurements(level);
        return "water projections=" + m.projections + ", deferred=" + m.deferred + ", deduplicated=" + m.deduplicated
                + ", unavailable neighborhoods=" + m.availabilityFailures + ", mutation microseconds=" + m.mutationNanos / 1_000
                + ", maximum mutation microseconds=" + m.maxMutationNanos / 1_000
                + ", maximum flow tick microseconds=" + m.maxTickNanos / 1_000;
    }

    public static void clearLevel(ServerLevel level) { MEASUREMENTS.remove(level); }

    private static Measurements measurements(ServerLevel level) {
        return MEASUREMENTS.computeIfAbsent(level, ignored -> new Measurements());
    }

    private static final class Measurements {
        long projections, deferred, deduplicated, availabilityFailures, mutationNanos;
        long maxMutationNanos, maxTickNanos;
        long lastWarningTick = -200;
    }

    /**
     * Matches a bounded Minecraft region view: absent columns read as air.
     * The admitted halo covers Sable's known neighborhood. A modded shape that
     * reads beyond it still cannot start chunk loading through this view.
     */
    private record LoadedShapeReader(ServerLevel level) implements BlockGetter {
        @Override
        public BlockState getBlockState(BlockPos pos) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            return chunk == null || level.isOutsideBuildHeight(pos) ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            return chunk == null ? null : chunk.getBlockEntity(pos);
        }

        @Override
        public int getHeight() { return level.getHeight(); }

        @Override
        public int getMinBuildHeight() { return level.getMinBuildHeight(); }
    }
}
