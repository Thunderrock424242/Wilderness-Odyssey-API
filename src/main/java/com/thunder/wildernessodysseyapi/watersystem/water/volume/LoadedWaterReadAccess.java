package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * Reads immediately available runtime water chunks without tickets or future waits.
 *
 * <p>Server reads require the owning server thread. Generation workers must use
 * their generation region instead of the runtime level. Client callers retain
 * their existing client-thread ownership and query only received chunks. A chunk
 * holder or ticket alone is not proof that a complete chunk is available.</p>
 */
public final class LoadedWaterReadAccess {

    private LoadedWaterReadAccess() {
    }

    /** Rejects server generation/shutdown reads before touching runtime state. */
    public static boolean canRead(Level level) {
        return level != null && (!(level instanceof ServerLevel serverLevel)
                || serverLevel.getServer().isSameThread() && !serverLevel.getServer().isStopped());
    }

    /** Returns an available chunk at an in-height position, never requesting one. */
    @Nullable
    public static LevelChunk loadedChunk(Level level, BlockPos pos) {
        if (!canRead(level) || level.isOutsideBuildHeight(pos)) {
            return null;
        }
        return loadedChunk(level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** Returns an available chunk column, never advancing its generation status. */
    @Nullable
    public static LevelChunk loadedChunk(Level level, int x, int z) {
        if (!canRead(level)) {
            return null;
        }
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getChunkSource().getChunkNow(x, z);
        }
        var chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
        return chunk instanceof LevelChunk levelChunk ? levelChunk : null;
    }

    /**
     * Supplies bounded fluid/shape reads; unavailable neighbors appear empty.
     * The view is local to the query and is never retained in a global cache.
     */
    public static BlockGetter blockReader(Level level) {
        return new LoadedBlockReader(level);
    }

    private record LoadedBlockReader(Level level) implements BlockGetter {
        @Override
        public BlockState getBlockState(BlockPos pos) {
            LevelChunk chunk = loadedChunk(level, pos);
            return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            LevelChunk chunk = loadedChunk(level, pos);
            return chunk == null ? null : chunk.getBlockEntities().get(pos);
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return level.getMinBuildHeight();
        }
    }
}
