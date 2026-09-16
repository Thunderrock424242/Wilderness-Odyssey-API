package com.thunder.wildernessodysseyapi.temporalrift.echo;

import com.thunder.wildernessodysseyapi.temporalrift.config.TemporalRiftConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Generation-only sampled surface decoration. No work queue can later rewrite player construction. */
public final class EchoDistortionManager {
    private EchoDistortionManager() { }

    /** Samples sixteen narrow columns in a new chunk instead of scanning its complete volume. */
    public static void decorateNewChunk(ServerLevel level, LevelChunk chunk) {
        if (!TemporalRiftConfig.ENABLE_ECHO_STABILITY_SYSTEM.get()) return;
        EchoStabilityLevel stability = EchoStabilityManager.baseLevel(level.getSeed(),
                EchoRegionModel.regionId(chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ()));
        if (stability == EchoStabilityLevel.STABLE) return;
        long hash = EchoRegionModel.mix(level.getSeed() ^ chunk.getPos().toLong());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // One point in each 4x4 cell: at most 16 * 24 block reads and 64 mutations.
        int changes = 0;
        for (int sample = 0; sample < 16 && changes < 64; sample++) {
            int x = (sample & 3) * 4 + (int) (hash & 3);
            int z = (sample >> 2) * 4 + (int) ((hash >>> 2) & 3);
            int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            for (int y = Math.min(surface + 1, level.getMaxBuildHeight() - 1);
                 y >= Math.max(level.getMinBuildHeight(), surface - 22) && changes < 64; y--) {
                pos.set(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
                BlockState state = chunk.getBlockState(pos);
                if (state.is(BlockTags.LEAVES) && shouldStripLeaf(level.getSeed(), pos, stability)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    changes++;
                } else if (state.getBlock() instanceof DoorBlock && state.hasProperty(BlockStateProperties.OPEN)
                        && !state.getValue(BlockStateProperties.OPEN)) {
                    level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), 2);
                    changes++;
                }
            }
            hash = EchoRegionModel.mix(hash + sample);
        }
    }

    private static boolean shouldStripLeaf(long seed, BlockPos pos, EchoStabilityLevel stability) {
        long patch = seed ^ (long) Math.floorDiv(pos.getX(), 6) * 0x9E3779B97F4A7C15L
                ^ (long) Math.floorDiv(pos.getY(), 8) * 0xC2B2AE3D27D4EB4FL
                ^ (long) Math.floorDiv(pos.getZ(), 6) * 0x165667B19E3779F9L;
        return Math.floorMod(EchoRegionModel.mix(patch), 100) < (stability == EchoStabilityLevel.FRACTURED ? 65 : 35);
    }
}
