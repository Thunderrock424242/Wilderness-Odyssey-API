package com.thunder.wildernessodysseyapi.worldgen.spawn;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.worldgen.structure.LargeStructurePlacementOptimizer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises preparation and release against Minecraft's real chunk-ticket lifecycle. */
@GameTestHolder(ModConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StarterBunkerChunkGameTests {
    private StarterBunkerChunkGameTests() { }

    @GameTest(template = "water_flow", timeoutTicks = 200)
    public static void starterFootprintStaysLoadedUntilConstructionFinishes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = distantOrigin(helper, 64);
        Vec3i size = new Vec3i(17, 1, 1);
        ChunkPos first = new ChunkPos(origin);
        ChunkPos last = new ChunkPos(origin.offset(16, 0, 0));
        helper.assertTrue(level.getChunkSource().getChunkNow(first.x, first.z) == null,
                "The fixture must start outside the loaded spawn and GameTest chunks");

        Boolean constructed = StarterBunkerChunkPreparation.withChunks(level, origin, size, () -> {
            helper.assertTrue(LargeStructurePlacementOptimizer.preparePlacement(level, origin, size),
                    "Starter construction began before its selected footprint was loaded");
            // getChunk's transient UNKNOWN ticket expires during managed chunk work.
            // Reproduce that lifetime without running unrelated entity or fluid ticks.
            for (int i = 0; i < 8; i++) level.getChunkSource().tick(() -> true, false);
            helper.assertTrue(LargeStructurePlacementOptimizer.preparePlacement(level, origin, size),
                    "Starter chunks expired while island construction still owned them");
            return true;
        });
        helper.assertTrue(Boolean.TRUE.equals(constructed), "Starter construction did not return its result");
        succeedsAfterRelease(helper, first, last);
    }

    @GameTest(template = "water_flow", timeoutTicks = 200)
    public static void failedConstructionReleasesItsChunkTickets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = distantOrigin(helper, 128);
        ChunkPos chunk = new ChunkPos(origin);
        try {
            StarterBunkerChunkPreparation.withChunks(level, origin, new Vec3i(1, 1, 1), () -> {
                helper.assertTrue(level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null,
                        "Starter construction ran without preparing its chunk");
                throw new IllegalArgumentException("fixture");
            });
            helper.fail("The construction failure was swallowed");
        } catch (IllegalArgumentException expected) {
            helper.assertTrue("fixture".equals(expected.getMessage()), "Unexpected construction failure");
        }
        succeedsAfterRelease(helper, chunk, chunk);
    }

    @GameTest(template = "water_flow")
    public static void rejectedFootprintDoesNotLoadChunksOrStartConstruction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = distantOrigin(helper, 192);
        ChunkPos chunk = new ChunkPos(origin);
        Object result = StarterBunkerChunkPreparation.withChunks(level, origin, new Vec3i(512, 1, 512), () -> {
            helper.fail("An over-budget starter footprint began construction");
            return new Object();
        });
        helper.assertTrue(result == null && level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null,
                "Rejected starter preparation changed chunk availability");
        helper.succeed();
    }

    private static BlockPos distantOrigin(GameTestHelper helper, int chunkOffset) {
        ChunkPos fixture = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        return new BlockPos((fixture.x + chunkOffset) * 16, 64, fixture.z * 16);
    }

    private static void succeedsAfterRelease(GameTestHelper helper, ChunkPos first, ChunkPos last) {
        helper.succeedWhen(() -> helper.assertTrue(
                helper.getLevel().getChunkSource().getChunkNow(first.x, first.z) == null
                        && helper.getLevel().getChunkSource().getChunkNow(last.x, last.z) == null,
                "Starter chunks remained pinned after construction ended"));
    }
}
