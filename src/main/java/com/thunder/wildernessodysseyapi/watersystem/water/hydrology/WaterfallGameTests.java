package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.water.authority.AuthorityWaterAccess;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.RiverChannelColumn;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Exercises the public authority path, never a second entity force handler. */
@GameTestHolder("wildernessodysseyapi_water_safety_tests")
@PrefixGameTestTemplate(false)
public final class WaterfallGameTests {
    private WaterfallGameTests() { }

    @GameTest(template = "water_flow")
    public static void actualFloodAboveGeneratedWaterRemainsQueryable(GameTestHelper helper) {
        var level = helper.getLevel();
        var base = helper.absolutePos(new BlockPos(4, 3, 4));
        com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge.WorldFluidMutationReconciler
                .setCanonicalProjectionBlock(level, base,
                        com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry
                                .WILDERNESS_WATER_BLOCK.get().defaultBlockState(), 2);
        level.getChunkSource().getChunkNow(base.getX() >> 4, base.getZ() >> 4)
                .getData(ModAttachments.GENERATED_WATER).recordCell(base,
                        com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk.Cell.of(8, false,
                                com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk.BodyType.OCEAN));
        helper.assertTrue(CanonicalWater.placeTemporaryFlood(level, base.above(), 0, 0),
                "Could not prepare the explicit one-block read-only flood fixture");
        var sample = new com.thunder.wildernessodysseyapi.watersystem.water.api.WaterSample();
        new AuthorityWaterAccess().sample(level, base.getX() + .5, base.getY() + 1.5, base.getZ() + .5, 0, sample);
        helper.assertTrue(sample.water(), "Generated surface lookup ignored actual canonical floodwater above it");
        helper.succeed();
    }

    @GameTest(template = "water_flow")
    public static void finiteParcelKeepsDownwardCurrentInPublicSampling(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(new BlockPos(4, 4, 4));
        level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        CanonicalWater.set(level, position, WaterVolumeChunk.WaterCell.still(4096,
                WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED).withFlowState(4096, .2f, -2f, 0), true, false);
        var current = new AuthorityWaterAccess().getCurrent(level, Vec3.atCenterOf(position));
        helper.assertTrue(current.y <= -1.99, "Surface sampling discarded canonical falling momentum");
        helper.assertTrue(CanonicalWater.get(level, position).volumeUnits() == 4096,
                "Read-only current sampling changed finite volume");
        helper.succeed();
    }

    @GameTest(template = "water_flow")
    public static void interruptedCurtainLosesDerivedJetWithoutChangingInventory(GameTestHelper helper) {
        var level = helper.getLevel();
        var base = helper.absolutePos(new BlockPos(4, 2, 4));
        for (int y = 0; y <= 3; y++) {
            var position = base.above(y);
            level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
            CanonicalWater.set(level, position, WaterVolumeChunk.WaterCell.still(4096,
                    WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED).withFlowState(4096, 1, 0, 0), true, false);
        }
        var chunk = level.getChunkSource().getChunkNow(base.getX() >> 4, base.getZ() >> 4);
        chunk.getData(ModAttachments.RIVER_GENERATION).complete(1234, 0, 0, List.of(
                new RiverChannelColumn(base.getX() & 15, base.getZ() & 15, base.getY(), base.getY() - 1,
                        base.getY() + 3, 1, 0, 1, RiverChannelColumn.Kind.WATERFALL)));
        var access = new AuthorityWaterAccess();
        var sample = Vec3.atCenterOf(base.above(1));
        helper.assertTrue(access.getCurrent(level, sample).y < -1, "Verified flowing curtain has no jet");
        CanonicalWater.set(level, base, CanonicalWater.get(level, base).withFlowState(4096, 1, -8, 0), true, false);
        double nearLip = Math.abs(access.getCurrent(level, Vec3.atCenterOf(base.above(3))).y);
        double belowLip = Math.abs(access.getCurrent(level, sample).y);
        helper.assertTrue(nearLip < belowLip - .5, "A fast lower parcel incorrectly became the lip's ballistic seed");
        CanonicalWater.set(level, base, CanonicalWater.get(level, base).withFlowState(4096, 1, 0, 0), true, false);
        for (int warmup = 0; warmup < 128; warmup++) access.getCurrent(level, sample);
        long[] timings = new long[5];
        double checksum = 0;
        for (int round = 0; round < timings.length; round++) {
            long started = System.nanoTime();
            for (int query = 0; query < 128; query++) checksum += access.getCurrent(level, sample).y;
            timings[round] = System.nanoTime() - started;
        }
        java.util.Arrays.sort(timings);
        helper.assertTrue(checksum < 0, "Measured queries did not consume a waterfall jet");
        com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER.info(
                "[Waterfall query fixture] warmup=128 queriesPerRound=128 rounds=5 medianNanosPerQuery={} slowestNanosPerQuery={} checksum={}",
                timings[2] / 128, timings[4] / 128, checksum);
        int before = CanonicalWater.get(level, base.above(1)).volumeUnits();
        CanonicalWater.set(level, base.above(2), WaterVolumeChunk.WaterCell.EMPTY, true, false);
        helper.assertTrue(Math.abs(access.getCurrent(level, sample).y) < .01,
                "A gap in the actual wet curtain retained a metadata-only jet");
        helper.assertTrue(CanonicalWater.get(level, base.above(1)).volumeUnits() == before,
                "Current sampling changed the receiver's inventory");
        helper.succeed();
    }
}
