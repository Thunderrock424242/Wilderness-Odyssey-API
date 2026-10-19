package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.bank;

import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.GenerationWaterStateMapper;
import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.RiverChannelColumn;
import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.RiverTerrainCapture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.function.Predicate;

/**
 * Optional single-block native bank plants after river placement. Every candidate
 * is verified against live dry terrain; no feature replay or runtime growth owner.
 */
public final class RiverBankDecorator {
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private RiverBankDecorator() { }

    /** Returns successful writes, bounded by both configured and remaining assembly limits. */
    public static int decorate(WorldGenLevel level, ChunkAccess chunk, List<RiverChannelColumn> columns,
                               int offsetX, int offsetZ, long seed, Predicate<BlockPos> canWrite, int maximumWrites) {
        if (!RiverBankConfig.vegetationEnabled() || maximumWrites <= 0 || !(chunk instanceof ProtoChunk)
                || chunk.isUpgrading() || chunk.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES)
                || chunk.getAllStarts().values().stream().anyMatch(start -> start.isValid())
                || chunk.getAllReferences().values().stream().anyMatch(references -> !references.isEmpty())) return 0;
        int budget = Math.min(RiverBankConfig.maximumVegetationWrites(), maximumWrites);
        if (budget <= 0) return 0;
        double probability = RiverBankConfig.vegetationChance();
        var profiles = RiverBankProfiles.snapshot();
        int writes = 0;
        long identity = seed ^ chunk.getPos().toLong();
        for (var candidate : RiverBankCandidates.select(columns, offsetX, offsetZ, 256, identity)) {
            if (writes >= budget) break;
            int x = candidate.x();
            int z = candidate.z();
            // Native survival checks remain inside the supplied chunk's terrain.
            if (x == 0 || x == 15 || z == 0 || z == 15) continue;
            long entropy = RiverBankProfiles.mix(identity ^ (x + z * 16L) * 0x9E3779B97F4A7C15L);
            if ((entropy >>> 11) * 0x1.0p-53 >= probability) continue;
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            BlockPos support = new BlockPos(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
            BlockPos target = support.above();
            if (y <= chunk.getMinBuildHeight() || target.getY() >= chunk.getMaxBuildHeight()
                    || !canWrite.test(target) || !level.ensureCanWrite(target)) continue;
            BlockState ground = chunk.getBlockState(support);
            BlockState foundation = chunk.getBlockState(support.below());
            BlockState previous = chunk.getBlockState(target);
            if (!RiverTerrainCapture.natural(ground) || !RiverTerrainCapture.natural(foundation)
                    || !ground.isCollisionShapeFullBlock(level, support)
                    || previous.hasBlockEntity() || !previous.getFluidState().isEmpty() || !previous.isAir()) continue;
            if (!adjacentLiveWater(chunk, support)) continue;
            var profile = RiverBankProfiles.profile(chunk, support, profiles);
            var selected = profile == null ? null : profile.plant(RiverBankProfiles.mix(entropy));
            if (selected == null) continue;
            BlockState plant = BuiltInRegistries.BLOCK.get(selected).defaultBlockState();
            if (plant.hasBlockEntity() || !plant.getFluidState().isEmpty() || !plant.canSurvive(level, target)) continue;
            chunk.setBlockState(target, plant, false);
            if (chunk.getBlockState(target).equals(plant)) writes++;
        }
        return writes;
    }

    private static boolean adjacentLiveWater(ChunkAccess chunk, BlockPos support) {
        for (int[] direction : DIRECTIONS) for (int dy = -1; dy <= 0; dy++) {
            BlockPos neighbor = support.offset(direction[0], dy, direction[1]);
            BlockState state = chunk.getBlockState(neighbor);
            if ((state.is(Blocks.WATER) || GenerationWaterStateMapper.isWildernessWater(state))
                    && state.getFluidState().isSource()) return true;
        }
        return false;
    }
}
