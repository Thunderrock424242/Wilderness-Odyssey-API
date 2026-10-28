package com.thunder.wildernessodysseyapi.watersystem.water.fluid;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge.WaterMutationSafety;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge.WorldFluidMutationReconciler;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Tests accepted automation edits through actual chunk writes and ordinary server ticks. */
@GameTestHolder("wildernessodysseyapi_water_safety_tests")
@PrefixGameTestTemplate(false)
public final class WaterBarrierGameTests {

    private static final int FULL = WaterVolumeChunk.UNITS_PER_BLOCK;
    private static final int SLEEPING = WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED
            | WaterVolumeChunk.FLAG_SLEEPING;

    private WaterBarrierGameTests() { }

    @GameTest(template = "water_flow")
    public static void detachedChunkChangesCannotWakeLiveWorldWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(3, 3, 4));
        enclosedChannel(level, source);
        CanonicalWater.set(level, source, WaterVolumeChunk.WaterCell.still(FULL, SLEEPING), true, false);
        var before = CanonicalWater.get(level, source);
        var liveChunk = level.getChunkSource().getChunkNow(source.getX() >> 4, source.getZ() >> 4);
        var detached = new LevelChunk(level, liveChunk.getPos());
        detached.getData(ModAttachments.WATER_VOLUME).set(source, before);
        WaterBarrierUpdates.onBlockChanged(detached, source.east(),
                Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
        helper.assertTrue(before.equals(CanonicalWater.get(level, source)),
                "Detached chunk observation woke live-world water at the same coordinates");
        helper.succeed();
    }

    @GameTest(template = "water_flow", timeoutTicks = 100)
    public static void generatedWaterMaterializesOnlyAtAnAcceptedBreach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(3, 3, 4));
        enclosedChannel(level, source);
        WorldFluidMutationReconciler.setCanonicalProjectionBlock(level, source,
                WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get().defaultBlockState(), 2);
        level.getChunkSource().getChunkNow(source.getX() >> 4, source.getZ() >> 4)
                .getData(ModAttachments.GENERATED_WATER).recordCell(source,
                        GeneratedWaterChunk.Cell.of(8, false, GeneratedWaterChunk.BodyType.LAKE));
        helper.assertTrue(!CanonicalWater.isTracked(level, source), "Generated fixture was eagerly materialized");
        helper.assertTrue(level.setBlock(source.east(), Blocks.AIR.defaultBlockState(), 2), "Generated breach failed");
        helper.assertTrue(CanonicalWater.isTracked(level, source) && !CanonicalWater.get(level, source).sleeping(),
                "Generated water did not enter the existing local flow queue after the breach");
        helper.succeedWhen(() -> {
            helper.assertTrue(CanonicalWater.get(level, source.east(2)).volumeUnits() > 0,
                    "Generated parcel did not flow through the accepted opening");
            helper.assertTrue(channelUnits(level, source) == FULL, "Generated breach duplicated its lazy baseline");
        });
    }

    @GameTest(template = "water_flow")
    public static void starterGenerationScopeDoesNotWakeRuntimeNeighbors(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(3, 3, 4));
        BlockPos gate = source.east();
        enclosedChannel(level, source);
        CanonicalWater.set(level, source, WaterVolumeChunk.WaterCell.still(FULL, SLEEPING), true, false);
        var before = CanonicalWater.get(level, source);
        WorldFluidMutationReconciler.withStarterTerrainGeneration(level,
                new BoundingBox(gate.getX(), gate.getY(), gate.getZ(), gate.getX(), gate.getY(), gate.getZ()),
                () -> level.setBlock(gate, Blocks.AIR.defaultBlockState(), 2));
        helper.assertTrue(before.equals(CanonicalWater.get(level, source)),
                "Starter-generation terrain write entered runtime finite-water scheduling");
        helper.succeed();
    }

    @GameTest(template = "water_flow", batch = "water_barrier_boundary", timeoutTicks = 100)
    public static void acceptedBreachFindsSleepingWaterAcrossALoadedChunkEdge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(4, 3, 4));
        BlockPos source = null;
        for (int offsetX = -1; offsetX <= 1 && source == null; offsetX++) {
            BlockPos candidate = new BlockPos(((anchor.getX() >> 4) + offsetX) * 16 + 15,
                    level.getMaxBuildHeight() - 8, anchor.getZ());
            if (WaterMutationSafety.isReady(level, candidate, WaterMutationSafety.CALLBACK_RADIUS + 1)) {
                source = candidate;
            }
        }
        helper.assertTrue(source != null, "No naturally admitted chunk edge for the boundary fixture");
        BlockPos boundarySource = source;
        enclosedChannel(level, boundarySource);
        CanonicalWater.set(level, boundarySource, WaterVolumeChunk.WaterCell.still(FULL, SLEEPING), true, false);
        level.setBlock(boundarySource.east(), Blocks.AIR.defaultBlockState(), 2);
        helper.assertTrue(!CanonicalWater.get(level, boundarySource).sleeping(),
                "Barrier notification did not cross into its already-loaded neighbor");
        helper.succeedWhen(() -> {
            helper.assertTrue(CanonicalWater.get(level, boundarySource.east(2)).volumeUnits() > 0,
                    "Cross-chunk breach did not advance through normal finite-flow ticks");
            helper.assertTrue(channelUnits(level, boundarySource) == FULL, "Cross-chunk flow changed inventory");
        });
    }

    @GameTest(template = "water_flow", timeoutTicks = 100)
    public static void directBreachWakesSleepingWaterWithoutNeighborNotifications(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(3, 3, 4));
        enclosedChannel(level, source);
        CanonicalWater.set(level, source, WaterVolumeChunk.WaterCell.still(FULL, SLEEPING), true, false);
        helper.assertTrue(CanonicalWater.get(level, source).sleeping(), "Fixture did not start asleep");

        helper.assertTrue(level.setBlock(source.east(), Blocks.AIR.defaultBlockState(), 2),
                "Automation did not open the barrier");
        helper.assertTrue(!CanonicalWater.get(level, source).sleeping(),
                "Accepted direct barrier removal did not wake adjacent canonical water");
        helper.assertTrue(channelUnits(level, source) == FULL, "Wake changed the conservation ledger");
        helper.succeedWhen(() -> {
            helper.assertTrue(CanonicalWater.get(level, source.east(2)).volumeUnits() > 0,
                    "Breach did not propagate through the ordinary finite-flow queue");
            helper.assertTrue(channelUnits(level, source) == FULL, "Breach created or lost water");
        });
    }

    @GameTest(template = "water_flow", timeoutTicks = 100)
    public static void automationExposesRetainedDisplacementWithoutManualWake(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(4, 3, 4));
        for (BlockPos pos : BlockPos.betweenClosed(source.offset(-2, -1, -2), source.offset(2, 4, 2))) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        }
        level.setBlock(source, Blocks.AIR.defaultBlockState(), 2);
        CanonicalWater.set(level, source, WaterVolumeChunk.WaterCell.still(FULL, SLEEPING), true, false);
        helper.assertTrue(level.setBlock(source, Blocks.STONE.defaultBlockState(), 2), "Solid placement failed");
        var retained = CanonicalWater.get(level, source);
        helper.assertTrue(retained.displacementReservoir() && retained.volumeUnits() == FULL,
                "Fixture failed to retain one exact displacement parcel");
        CanonicalWater.set(level, source, retained.withAddedFlags(WaterVolumeChunk.FLAG_SLEEPING), false, false);

        helper.assertTrue(level.setBlock(source, Blocks.AIR.defaultBlockState(), 2), "Automation did not expose parcel");
        helper.assertTrue(!CanonicalWater.get(level, source).sleeping(),
                "Accepted dry-block removal left its displacement parcel asleep");
        helper.succeedWhen(() -> {
            var released = CanonicalWater.get(level, source);
            helper.assertTrue(!released.displacementReservoir() && released.volumeUnits() == FULL,
                    "Exposed displacement did not resume ordinary canonical water");
            helper.assertTrue(WildernessWaterAuthority.sample(level, source).water(),
                    "Released parcel has no physical water projection");
        });
    }

    @GameTest(template = "water_flow")
    public static void unchangedClosedAndProvisionalBarriersDoNotWakeWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(3, 3, 4));
        BlockPos gate = source.east();
        enclosedChannel(level, source);
        CanonicalWater.set(level, source, WaterVolumeChunk.WaterCell.still(FULL, SLEEPING), true, false);
        var before = CanonicalWater.get(level, source);
        var chunk = level.getChunkSource().getChunkNow(source.getX() >> 4, source.getZ() >> 4);
        long revision = chunk.getData(ModAttachments.WATER_VOLUME).revision();
        level.setBlock(gate, Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(gate, Blocks.ANDESITE.defaultBlockState(), 2);
        helper.assertTrue(before.equals(CanonicalWater.get(level, source)),
                "An unchanged/closed barrier woke canonical water");

        int snapshotCount = level.capturedBlockSnapshots.size();
        boolean capture = level.captureBlockSnapshots;
        boolean restoring = level.restoringBlockSnapshots;
        try {
            level.captureBlockSnapshots = true;
            level.setBlock(gate, Blocks.AIR.defaultBlockState(), 2);
            helper.assertTrue(before.equals(CanonicalWater.get(level, source)),
                    "A provisional terrain write woke canonical water");
        } finally {
            level.captureBlockSnapshots = false;
            level.restoringBlockSnapshots = true;
            level.setBlock(gate, Blocks.ANDESITE.defaultBlockState(), 2);
            level.captureBlockSnapshots = capture;
            level.restoringBlockSnapshots = restoring;
            while (level.capturedBlockSnapshots.size() > snapshotCount) {
                level.capturedBlockSnapshots.remove(level.capturedBlockSnapshots.size() - 1);
            }
        }
        helper.assertTrue(chunk.getData(ModAttachments.WATER_VOLUME).revision() == revision,
                "Ignored terrain changes created a water revision");
        helper.assertTrue(channelUnits(level, source) == FULL, "Ignored terrain changes changed inventory");
        helper.succeed();
    }

    private static void enclosedChannel(ServerLevel level, BlockPos source) {
        for (BlockPos pos : BlockPos.betweenClosed(source.offset(-1, -1, -1), source.offset(3, 1, 1))) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        }
        level.setBlock(source, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(source.east(2), Blocks.AIR.defaultBlockState(), 2);
    }

    private static long channelUnits(ServerLevel level, BlockPos source) {
        long total = 0;
        for (int x = 0; x <= 2; x++) total += CanonicalWater.get(level, source.east(x)).volumeUnits();
        return total;
    }
}
