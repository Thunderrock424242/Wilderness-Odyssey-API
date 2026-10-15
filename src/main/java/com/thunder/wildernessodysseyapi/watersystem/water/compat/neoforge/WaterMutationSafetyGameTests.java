package com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.CanonicalWaterFlowGameTests;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Isolated runtime proofs; optional Sable is inspected only when installed. */
@GameTestHolder("wildernessodysseyapi_water_safety_tests")
@PrefixGameTestTemplate(false)
public final class WaterMutationSafetyGameTests {
    private WaterMutationSafetyGameTests() { }

    /** Real bucket placement must reach the ordinary server ticker without a manual solver call. */
    @GameTest(template = "water_flow", timeoutTicks = 100)
    public static void vanillaBucketFlowsAlongOneBlockTrench(GameTestHelper helper) {
        bucketFlowsAlongTrench(helper, (BucketItem) Items.WATER_BUCKET);
    }

    /** The custom bucket must follow the same automatic, conserved flow path. */
    @GameTest(template = "water_flow", timeoutTicks = 100)
    public static void wildernessBucketFlowsAlongOneBlockTrench(GameTestHelper helper) {
        bucketFlowsAlongTrench(helper, WildernessFluidRegistry.WILDERNESS_WATER_BUCKET.get());
    }

    private static void bucketFlowsAlongTrench(GameTestHelper helper, BucketItem bucket) {
        var level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(3, 3, 4));
        for (int x = -3; x <= 3; x++) {
            for (int z = -1; z <= 1; z++) {
                level.setBlock(source.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(source.offset(x, 0, z), z != 0 || Math.abs(x) == 3
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            }
        }
        helper.assertTrue(bucket.emptyContents(null, level, source, null, new ItemStack(bucket)),
                "Bucket placement failed in an empty trench");
        helper.assertTrue(CanonicalWater.get(level, source).volumeUnits() == WaterVolumeChunk.UNITS_PER_BLOCK,
                "Bucket did not establish exactly one conserved block of water");

        // Deliberately do not call tickCell: this covers event registration,
        // active-queue admission, and physical projection through normal ticks.
        helper.succeedWhen(() -> {
            helper.assertTrue(CanonicalWater.get(level, source.west()).volumeUnits() > 0
                            && CanonicalWater.get(level, source.east()).volumeUnits() > 0,
                    "Bucket stayed in one cell: " + CanonicalWater.get(level, source)
                            + "; " + WildernessFluidRegistry.diagnostics(level));
            int total = 0;
            for (int x = -2; x <= 2; x++) {
                BlockPos cell = source.offset(x, 0, 0);
                int units = CanonicalWater.get(level, cell).volumeUnits();
                total += units;
                if (units > 0) {
                    helper.assertTrue(!level.getFluidState(cell).isEmpty(),
                            "Flow reached a trench cell without a visible fluid projection: " + cell);
                }
            }
            helper.assertTrue(total == WaterVolumeChunk.UNITS_PER_BLOCK,
                    "Trench flow created or lost water: " + total);
        });
    }

    /** Collecting live flow evidence must not wake, drain, or reproject the inspected water. */
    @GameTest(template = "water_flow")
    public static void flowInspectionLeavesCanonicalWaterUnchanged(GameTestHelper helper)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(4, 4, 4));
        CanonicalWater.set(level, position, WaterVolumeChunk.WaterCell.still(3072,
                WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED | WaterVolumeChunk.FLAG_SLEEPING), true, false);
        var before = CanonicalWater.get(level, position);
        var blockBefore = level.getBlockState(position);
        var volume = level.getChunkAt(position).getData(ModAttachments.WATER_VOLUME);
        long revisionBefore = volume.revision();
        int queueBefore = CanonicalWater.diagnostics(level).queuedCells();
        var commandSource = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();

        int result = level.getServer().getCommands().getDispatcher().execute(
                "wowater inspect " + position.getX() + " " + position.getY() + " " + position.getZ(), commandSource);

        helper.assertTrue(result == 1, "Flow inspection command did not execute successfully");
        helper.assertTrue(before.equals(CanonicalWater.get(level, position)), "Inspection changed canonical water");
        helper.assertTrue(blockBefore.equals(level.getBlockState(position)), "Inspection changed physical water");
        helper.assertTrue(revisionBefore == volume.revision(), "Inspection created a water revision");
        helper.assertTrue(queueBefore == CanonicalWater.diagnostics(level).queuedCells(), "Inspection changed flow work");
        helper.succeed();
    }

    @GameTest(template = "water_flow")
    public static void shapeContextRestoresAfterNestedFailure(GameTestHelper helper) {
        var level = helper.getLevel();
        helper.assertTrue(WaterMutationSafety.shapeReader() == null, "Leaked shape context before test");
        WaterMutationSafety.withLoadedShapeContext(level, () -> {
            BlockGetter outer = WaterMutationSafety.shapeReader();
            try {
                WaterMutationSafety.withLoadedShapeContext(level, () -> { throw new IllegalArgumentException("fixture"); });
            } catch (IllegalArgumentException expected) {
                helper.assertTrue(WaterMutationSafety.shapeReader() == outer, "Nested context did not restore");
            }
            return true;
        });
        helper.assertTrue(WaterMutationSafety.shapeReader() == null, "Shape context leaked after return");
        helper.succeed();
    }

    @GameTest(template = "water_flow")
    public static void sableShapeUsesActualPositionWithoutItsLoadingAccessor(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("sable")) { helper.succeed(); return; }
        BlockPos actual = helper.absolutePos(new BlockPos(4, 4, 4));
        BlockPos[] observed = { null };
        BlockState probe = Blocks.PURPLE_SHULKER_BOX.defaultBlockState().setValue(ShulkerBoxBlock.FACING, Direction.WEST);
        helper.assertTrue(WaterMutationSafety.setBlock(helper.getLevel(), actual, probe, 3), "Shape fixture placement failed");
        var box = (ShulkerBoxBlockEntity) helper.getLevel().getBlockEntity(actual);
        WaterMutationSafety.withLoadedShapeContext(helper.getLevel(), () -> {
            box.triggerEvent(1, 1);
            for (int tick = 0; tick < 12; tick++) ShulkerBoxBlockEntity.tick(helper.getLevel(), actual, probe, box);
            helper.assertTrue(!probe.isCollisionShapeFullBlock(WaterMutationSafety.shapeReader(), actual),
                    "Opened shulker did not create a position-sensitive collision fixture");
            return true;
        });
        BlockGetter forbiddenLoadingReader = new BlockGetter() {
            @Override public BlockState getBlockState(BlockPos pos) { throw new AssertionError("Sable loading accessor was used"); }
            @Override public FluidState getFluidState(BlockPos pos) { throw new AssertionError("Sable fluid accessor was used"); }
            @Override public BlockEntity getBlockEntity(BlockPos pos) {
                observed[0] = pos.immutable();
                throw new AssertionError("Sable entity accessor was used");
            }
            @Override public int getHeight() { return helper.getLevel().getHeight(); }
            @Override public int getMinBuildHeight() { return helper.getLevel().getMinBuildHeight(); }
        };
        var classifier = Class.forName("dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState");
        var solid = classifier.getMethod("isSolid", BlockGetter.class, BlockPos.class, BlockState.class);
        var full = classifier.getMethod("isFullBlock", BlockGetter.class, BlockPos.class, BlockState.class);
        // Characterize the original seam without ever waiting on a real chunk
        // future. The unscoped upstream call must hit our forbidden accessor.
        try {
            solid.invoke(null, forbiddenLoadingReader, actual, probe);
            helper.fail("Upstream classifier no longer exhibits the characterized loading path; review the bridge");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            helper.assertTrue(expected.getCause() instanceof AssertionError, "Unexpected upstream failure");
            helper.assertTrue(BlockPos.ZERO.equals(observed[0]), "Upstream origin-shape diagnosis no longer matches");
        }
        WaterMutationSafety.withLoadedShapeContext(helper.getLevel(), () -> {
            try {
                helper.assertTrue((boolean) solid.invoke(null, forbiddenLoadingReader, actual, probe), "Solid classification changed");
                helper.assertTrue(!(boolean) full.invoke(null, forbiddenLoadingReader, actual, probe),
                        "Full-block classifier did not read the opened box at its actual position");
                helper.assertTrue(BlockPos.ZERO.equals(observed[0]), "Scoped call used the forbidden accessor");
                return true;
            } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
        });
        helper.succeed();
    }

    @GameTest(template = "water_flow")
    public static void canonicalProjectionRoundTripConservesUnits(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 4, 4));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        int accepted = CanonicalWater.addVolume(level, pos, 3072, 0, 0, 0);
        helper.assertTrue(accepted == 3072, "Loaded target rejected volume");
        helper.assertTrue(CanonicalWater.get(level, pos).volumeUnits() == 3072, "Projection changed exact volume");
        helper.assertTrue(!CanonicalWater.get(level, pos).projectionPending(), "Loaded projection stayed pending");
        helper.assertTrue(CanonicalWater.drainVolume(level, pos, 3072) == 3072, "Drain lost volume");
        helper.assertTrue(level.getBlockState(pos).isAir(), "Dry projection left water behind");
        helper.assertTrue(CanonicalWater.get(level, pos).volumeUnits() == 0, "Dry cell retained volume");
        helper.succeed();
    }

    @GameTest(template = "water_flow")
    public static void sameVolumeDirectWriteAcknowledgesDeferredProjection(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 4, 4));
        CanonicalWater.set(level, pos, WaterVolumeChunk.WaterCell.still(4096, 0), true, false);
        // Recreate a saved desired cell whose physical full-block projection
        // predates the intent. The machine's write changes no canonical units.
        var desired = WaterVolumeChunk.WaterCell.still(3072, WaterVolumeChunk.FLAG_PROJECTION_PENDING);
        level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4)
                .getData(ModAttachments.WATER_VOLUME).set(pos, desired);
        BlockState partial = WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get()
                .defaultBlockState().setValue(LiquidBlock.LEVEL, 2);
        helper.assertTrue(level.setBlock(pos, partial, 3), "Same-volume direct write failed");
        helper.assertTrue(CanonicalWater.get(level, pos).volumeUnits() == 3072, "No-delta write changed volume");
        CanonicalWater.reprojectCompatibility(level, pos);
        helper.assertTrue(!CanonicalWater.get(level, pos).projectionPending(), "No-delta projection stayed pending");
        helper.assertTrue(level.getBlockState(pos).equals(partial), "No-delta projection changed shape");
        helper.assertTrue(WaterMutationSafety.shapeReader() == null, "Direct write leaked shape context");
        helper.succeed();
    }

    @GameTest(template = "water_flow")
    public static void repeatedProjectionsDeduplicateWithoutCreatingVolume(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 4, 4));
        var cell = WaterVolumeChunk.WaterCell.still(3072, 0);
        CanonicalWater.set(level, pos, cell, true, false);
        long revision = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4)
                .getData(ModAttachments.WATER_VOLUME).revision();
        for (int update = 0; update < 256; update++) CanonicalWater.set(level, pos, cell, true, false);
        helper.assertTrue(CanonicalWater.get(level, pos).volumeUnits() == 3072, "Repeated writes created volume");
        helper.assertTrue(level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4)
                .getData(ModAttachments.WATER_VOLUME).revision() == revision, "Unchanged projections created revisions");
        helper.succeed();
    }

    @GameTest(template = "water_flow")
    public static void unavailableBorderPersistsIntentAndChunkLifecycleKeepsItsVolume(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos boundary = null;
        // Inspect naturally loaded columns only; the fixture never requests a
        // missing chunk, even when C2ME is present.
        for (int distance = 0; distance < 64 && boundary == null; distance++) {
            int x = (origin.getX() >> 4) + distance;
            int z = origin.getZ() >> 4;
            if (level.getChunkSource().getChunkNow(x, z) != null
                    && level.getChunkSource().getChunkNow(x + 1, z) == null) {
                boundary = new BlockPos((x << 4) + 15, level.getMaxBuildHeight() - 8, (z << 4) + 8);
            }
        }
        helper.assertTrue(boundary != null, "Fixture has no unavailable boundary within 64 columns");
        var chunk = level.getChunkSource().getChunkNow(boundary.getX() >> 4, boundary.getZ() >> 4);
        var volume = chunk.getData(ModAttachments.WATER_VOLUME);
        BlockState before = chunk.getBlockState(boundary);
        helper.assertTrue(before.isAir(), "High boundary fixture is occupied");
        CanonicalWater.set(level, boundary, WaterVolumeChunk.WaterCell.still(3072, 0), true);
        helper.assertTrue(volume.get(boundary).projectionPending(), "Unavailable boundary was not deferred");
        helper.assertTrue(chunk.getBlockState(boundary).equals(before), "Unavailable boundary mutated blocks");
        helper.assertTrue(CanonicalWater.drainVolume(level, boundary, 3072) == 0, "Unsafe drain was accepted");
        var restored = new WaterVolumeChunk();
        restored.deserializeNBT(level.registryAccess(), volume.serializeNBT(level.registryAccess()));
        helper.assertTrue(restored.get(boundary).projectionPending()
                && restored.get(boundary).volumeUnits() == 3072, "Save/reload lost deferred volume");
        int queued = CanonicalWater.diagnostics(level).queuedCells();
        CanonicalWater.onChunkUnload(level, chunk.getPos());
        helper.assertTrue(CanonicalWater.diagnostics(level).queuedCells() < queued, "Unload retained runtime work");
        CanonicalWater.onChunkLoad(level, chunk);
        helper.assertTrue(CanonicalWater.diagnostics(level).queuedCells() == queued, "Load lost pending work");
        helper.assertTrue(volume.get(boundary).volumeUnits() == 3072, "Lifecycle hooks changed volume");
        volume.set(boundary, WaterVolumeChunk.WaterCell.EMPTY);
        helper.succeed();
    }

    @GameTest(template = "water_flow")
    public static void downwardFlowConservesUnits(GameTestHelper helper) {
        CanonicalWaterFlowGameTests.downwardTransferConservesExactVolume(helper);
    }

    @GameTest(template = "water_flow")
    public static void lateralFlowConservesUnits(GameTestHelper helper) {
        CanonicalWaterFlowGameTests.symmetricLateralTransferHasNoDirectionBias(helper);
    }

    @GameTest(template = "water_flow")
    public static void solidDisplacementConservesUnits(GameTestHelper helper) {
        CanonicalWaterFlowGameTests.directSolidWriteDisplacesWaterExactlyOnce(helper);
    }

    @GameTest(template = "water_flow")
    public static void enclosureRetainsAndReleasesItsParcel(GameTestHelper helper) {
        CanonicalWaterFlowGameTests.enclosedSolidWriteRetainsDisplacementReservoir(helper);
    }
}
