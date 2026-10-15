package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real chunk heightmaps and server registries exercise terrain admission inputs. */
@GameTestHolder(ModConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HydrologyTerrainSamplingGameTests {
    private HydrologyTerrainSamplingGameTests() {
    }

    @GameTest(template = "water_flow", batch = "water_audit_sampling")
    public static void grassySurfaceRetainsVegetationAboveSubsurfaceSoil(GameTestHelper helper) {
        // A detached chunk avoids changing another test's terrain while retaining
        // the actual LevelChunk heightmaps and block/tag behavior used on admission.
        LevelChunk chunk = new LevelChunk(helper.getLevel(), new ChunkPos(0, 0));
        for (int z = 2; z < 16; z += 4) {
            for (int x = 2; x < 16; x += 4) {
                chunk.setBlockState(new BlockPos(x, 63, z), Blocks.DIRT.defaultBlockState(), false);
                chunk.setBlockState(new BlockPos(x, 64, z), Blocks.GRASS_BLOCK.defaultBlockState(), false);
            }
        }
        RegionalHydrologyState region = new RegionalHydrologyState(0, 0, 64);
        RegionalSoilSampler.sample(chunk, region);
        helper.assertTrue(region.vegetation == 1.0, "Grass-covered terrain was sampled as unvegetated dirt");
        for (int z = 2; z < 16; z += 4) {
            for (int x = 2; x < 16; x += 4) {
                chunk.setBlockState(new BlockPos(x, 64, z), Blocks.DIRT.defaultBlockState(), false);
            }
        }
        RegionalSoilSampler.sample(chunk, region);
        helper.assertTrue(region.vegetation == 0.0, "Bare dirt was counted as vegetation");
        helper.succeed();
    }
}
