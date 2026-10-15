package com.thunder.wildernessodysseyapi.weather.integration;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge.WorldFluidMutationReconciler;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Exercises the real loaded-column adapter rather than supplied coverage values. */
@GameTestHolder(ModConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaterCoverageSamplingGameTests {
    private WaterCoverageSamplingGameTests() {
    }

    @GameTest(template = "water_flow", batch = "water_audit_sampling", skyAccess = true)
    public static void oneBlockDeepSurfaceWaterCountsAsWet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(helper.absolutePos(new BlockPos(7, 4, 7)));
        AtmosphereCellKey cell = new AtmosphereCellKey(chunk.x, chunk.z);
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        int top = level.getMinBuildHeight();
        for (int z = 1; z < 16; z += 2) {
            for (int x = 1; x < 16; x += 2) {
                top = Math.max(top, level.getHeight(Heightmap.Types.WORLD_SURFACE,
                        chunk.getMinBlockX() + x, chunk.getMinBlockZ() + z));
            }
        }
        top += 2;
        try {
            for (int z = 1; z < 16; z += 2) {
                for (int x = 1; x < 16; x += 2) {
                    BlockPos water = new BlockPos(chunk.getMinBlockX() + x, top, chunk.getMinBlockZ() + z);
                    originals.put(water.below(), level.getBlockState(water.below()));
                    originals.put(water, level.getBlockState(water));
                    WorldFluidMutationReconciler.setCanonicalProjectionBlock(level, water.below(),
                            Blocks.STONE.defaultBlockState(), 2);
                    WorldFluidMutationReconciler.setCanonicalProjectionBlock(level, water,
                            Blocks.WATER.defaultBlockState(), 2);
                }
            }
            WildernessWeatherWaterInfluence influence = new WildernessWeatherWaterInfluence();
            influence.beginSamplingPass(Set.of(cell.packed()), level.getGameTime(), 20, 1);
            WaterInfluenceSample sample = influence.sample(level, cell, 16, 20);
            helper.assertTrue(sample.loadedProbeFraction() == 1.0f, "Water fixture is not fully loaded");
            helper.assertTrue(sample.surfaceWaterCoverage() == 1.0f,
                    "One-block-deep water was classified as its dry bed: " + sample);
        } finally {
            for (var entry : originals.entrySet()) {
                WorldFluidMutationReconciler.setCanonicalProjectionBlock(level,
                        entry.getKey(), entry.getValue(), 2);
            }
        }
        helper.succeed();
    }
}
