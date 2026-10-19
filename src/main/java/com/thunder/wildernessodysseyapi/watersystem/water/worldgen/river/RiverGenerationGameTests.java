package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WatershedSavedData;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WatershedBasinSavedData;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WatershedSimulationModel;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WatershedServices;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import net.minecraft.world.level.material.FlowingFluid;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.GenerationWaterStateMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Detached real ProtoChunks exercise writes without touching the test level's terrain. */
@GameTestHolder("wildernessodysseyapi_river_tests")
@PrefixGameTestTemplate(false)
public final class RiverGenerationGameTests {
    private static final RiverGenerationSettings SETTINGS = new RiverGenerationSettings(
            true, 1, 2, 1, 2, 3, 256, 12288, true, true, true);

    private RiverGenerationGameTests() { }

    @GameTest(template = "water_flow", timeoutTicks = 200)
    public static void completeTileCrossesChunksAndKeepsGeneratedVolumeOwnership(GameTestHelper helper) {
        var chunks = bowl(helper);
        var terrain = terrain();
        var plan = RiverCatchmentPlanner.plan(42, 0, 0, terrain, SETTINGS);
        helper.assertTrue(!plan.columns().isEmpty(), "Fixture did not produce a complete river assembly");
        int loaded = helper.getLevel().getChunkSource().getLoadedChunksCount();
        helper.assertTrue(RiverNetworkFeature.placeTile(42, 0, 0, chunks, SETTINGS, pos -> true),
                "Generation rejected a sealed natural basin");
        long expected = 0;
        long actual = 0;
        boolean west = false;
        boolean east = false;
        for (var column : plan.columns()) {
            var chunk = chunks[(column.x() >> 4) + (column.z() >> 4) * 2];
            var metadata = chunk.getData(ModAttachments.RIVER_GENERATION);
            var reach = metadata.reachAt(column.x() & 15, column.z() & 15);
            helper.assertTrue(reach != null && reach.waterY() == column.waterY(), "Reach metadata disagrees with placement");
            west |= column.x() == 15;
            east |= column.x() == 16;
            for (int y = column.floorY() + 1; y <= column.topY(); y++) {
                var pos = new BlockPos(column.x(), y, column.z());
                var span = chunk.getData(ModAttachments.GENERATED_WATER).spanAt(pos);
                helper.assertTrue(GenerationWaterStateMapper.isWildernessWater(chunk.getBlockState(pos))
                                && span != null && span.amountUnits() == WaterVolumeChunk.UNITS_PER_BLOCK,
                        "Generated channel lacks matching physical fluid and exact compact volume");
                expected += WaterVolumeChunk.UNITS_PER_BLOCK;
            }
        }
        for (var chunk : chunks) {
            var generated = chunk.getData(ModAttachments.GENERATED_WATER);
            actual += volume(generated);
            helper.assertTrue(!chunk.hasData(ModAttachments.WATER_VOLUME), "Generation created a second sparse inventory");
            var restored = new GeneratedWaterChunk();
            restored.deserializeNBT(helper.getLevel().registryAccess(), generated.serializeNBT(helper.getLevel().registryAccess()));
            helper.assertTrue(volume(restored) == volume(generated),
                    "Generated volume changed on save/reload");
            var metadata = chunk.getData(ModAttachments.RIVER_GENERATION);
            helper.assertTrue(metadata.processed() && !metadata.hasTerrain(SETTINGS.fingerprint()), "Capture retained after generation");
        }
        helper.assertTrue(west && east && expected == actual, "Chunk crossing or generated volume mismatch");
        helper.assertTrue(!RiverNetworkFeature.placeTile(42, 0, 0, chunks, SETTINGS, pos -> true), "Completed tile was recarved");
        helper.assertTrue(helper.getLevel().getChunkSource().getLoadedChunksCount() == loaded, "Placement requested terrain");
        helper.succeed();
    }

    @GameTest(template = "water_flow", timeoutTicks = 200)
    public static void writeVetoAndForeignBlocksRejectTheWholeTile(GameTestHelper helper) {
        var blocked = bowl(helper);
        var plan = RiverCatchmentPlanner.plan(42, 0, 0, terrain(), SETTINGS);
        var first = plan.columns().getFirst();
        BlockPos veto = new BlockPos(first.x(), first.waterY(), first.z());
        helper.assertFalse(RiverNetworkFeature.placeTile(42, 0, 0, blocked, SETTINGS, pos -> !pos.equals(veto)),
                "A denied write was ignored");
        for (var chunk : blocked) helper.assertFalse(chunk.hasData(ModAttachments.GENERATED_WATER), "Partial water after write veto");

        var foreign = bowl(helper);
        var chunk = foreign[(first.x() >> 4) + (first.z() >> 4) * 2];
        chunk.setBlockState(veto, Blocks.CHEST.defaultBlockState(), false);
        helper.assertFalse(RiverNetworkFeature.placeTile(42, 0, 0, foreign, SETTINGS, pos -> true), "Foreign block was overwritten");
        helper.assertTrue(chunk.getBlockState(veto).is(Blocks.CHEST), "Protected block changed");
        for (var member : foreign) helper.assertFalse(member.hasData(ModAttachments.GENERATED_WATER), "Partial water after foreign block veto");
        helper.succeed();
    }

    @GameTest(template = "water_flow", timeoutTicks = 200)
    public static void eachAllowedInvocationOrderProducesTheSameTile(GameTestHelper helper) {
        CompoundTag[] expected = null;
        for (int owner : new int[]{3, 0, 2, 1}) {
            var chunks = bowl(helper);
            int ownerX = owner % 2;
            int ownerZ = owner / 2;
            helper.assertTrue(RiverNetworkFeature.placeTile(42, 0, 0, chunks, SETTINGS,
                    pos -> Math.abs((pos.getX() >> 4) - ownerX) <= 1
                            && Math.abs((pos.getZ() >> 4) - ownerZ) <= 1), "Owner write radius rejected tile");
            var tags = new CompoundTag[4];
            for (int i = 0; i < 4; i++) tags[i] = chunks[i].getData(ModAttachments.GENERATED_WATER).serializeNBT(helper.getLevel().registryAccess());
            if (expected == null) expected = tags;
            else for (int i = 0; i < 4; i++) helper.assertTrue(expected[i].equals(tags[i]), "Placement depends on first chunk order");
        }
        var completed = bowl(helper);
        completed[2] = new LevelChunk(helper.getLevel(), new ChunkPos(0, 1));
        helper.assertFalse(RiverNetworkFeature.placeTile(42, 0, 0, completed, SETTINGS, pos -> true), "Existing LevelChunk admitted");
        helper.succeed();
    }

    @GameTest(template = "water_flow", timeoutTicks = 200)
    public static void tributariesReachExistingWaterAndRejectAStaleReceivingBank(GameTestHelper helper) {
        var terrain = RiverTileTerrain.create((x, z) -> x >= 23 ? 100 : 100 + (23 - x) / 3,
                (x, z) -> x >= 23, (x, z) -> 0xFFFF);
        var plan = RiverCatchmentPlanner.plan(42, 0, 0, terrain, SETTINGS);
        helper.assertTrue(plan.columns().stream().anyMatch(column -> column.kind() == RiverChannelColumn.Kind.STREAM),
                "Receiver fixture must contain an actual tributary");
        var chunks = tile(helper, terrain);
        helper.assertTrue(RiverNetworkFeature.placeTile(42, 0, 0, chunks, SETTINGS, pos -> true), "Tributary placement failed");
        for (var column : plan.columns()) {
            var chunk = chunks[(column.x() >> 4) + (column.z() >> 4) * 2];
            helper.assertTrue(GenerationWaterStateMapper.isWildernessWater(chunk.getBlockState(
                    new BlockPos(column.x(), column.waterY(), column.z()))), "Tributary is disconnected from physical water");
        }
        var stale = tile(helper, terrain);
        BlockPos missing = new BlockPos(23, 100, 15);
        stale[1].setBlockState(missing, Blocks.AIR.defaultBlockState(), false);
        helper.assertFalse(RiverNetworkFeature.placeTile(42, 0, 0, stale, SETTINGS, pos -> true),
                "Stale captured receiving water was accepted as a live bank");
        var filled = tile(helper, terrain);
        filled[1].setBlockState(missing, Blocks.STONE.defaultBlockState(), false);
        helper.assertFalse(RiverNetworkFeature.placeTile(42, 0, 0, filled, SETTINGS, pos -> true),
                "A solid replacement was accepted as the planned receiving outlet");
        helper.succeed();
    }

    @GameTest(template = "water_flow", timeoutTicks = 200)
    public static void supportedDropsStoreExactFallingFluidAndSpans(GameTestHelper helper) {
        var settings = new RiverGenerationSettings(true, 1, 2, 1, 2, 10, 256, 12288, true, true, true);
        var terrain = RiverTileTerrain.create((x, z) -> x >= 23 ? 100 : 100 + (23 - x) * 2,
                (x, z) -> x >= 23, (x, z) -> 0xFFFF);
        var plan = RiverCatchmentPlanner.plan(42, 0, 0, terrain, settings);
        var chunks = tile(helper, terrain, settings);
        helper.assertTrue(RiverNetworkFeature.placeTile(42, 0, 0, chunks, settings, pos -> true), "Supported steep channel was rejected");
        int falling = 0;
        for (var column : plan.columns()) for (int y = column.waterY() + 1; y <= column.topY(); y++) {
            var chunk = chunks[(column.x() >> 4) + (column.z() >> 4) * 2];
            var pos = new BlockPos(column.x(), y, column.z());
            helper.assertTrue(chunk.getFluidState(pos).getValue(FlowingFluid.FALLING)
                            && chunk.getData(ModAttachments.GENERATED_WATER).spanAt(pos).cell().falling(),
                    "Falling transition was stored as a floating horizontal source");
            falling++;
        }
        helper.assertTrue(falling > 0, "Fixture did not place falling water");
        helper.succeed();
    }

    @GameTest(template = "water_flow", timeoutTicks = 200)
    public static void plannedHeadingUsesExistingModeledSpeedWithoutChangingWater(GameTestHelper helper) {
        var level = helper.getLevel();
        var anchor = helper.absolutePos(new BlockPos(4, 3, 4));
        var position = new BlockPos((anchor.getX() & ~15) + 15, anchor.getY(), (anchor.getZ() & ~15) + 15);
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        CanonicalWater.set(level, position,
                WaterVolumeChunk.WaterCell.still(4096, 0), true, false);
        var chunk = level.getChunkAt(position);
        chunk.getData(ModAttachments.GENERATED_WATER).recordCell(position,
                GeneratedWaterChunk.Cell.of(8, false, GeneratedWaterChunk.BodyType.RIVER));
        var regional = WatershedSavedData.get(level)
                .getOrCreate(level, chunk);
        regional.apply(new WatershedSimulationModel.Result(
                0, 0, 0, 0.2f, 0, 0, false, 0, 1, 0.06f, 0, 0, 0, 0), 0.8f, level.getGameTime());
        var metadata = new RiverGenerationChunk();
        metadata.complete(4321, 0, 0, List.of(new RiverChannelColumn(position.getX() & 15,
                position.getZ() & 15, position.getY(), position.getY() - 1, position.getY(), 0, -1, 0.8f,
                RiverChannelColumn.Kind.STREAM)));
        chunk.setData(ModAttachments.RIVER_GENERATION, metadata);
        var volume = chunk.getData(ModAttachments.WATER_VOLUME);
        long revision = volume.revision();
        var flow = WatershedServices.localFlow(level, position);
        var observation = RiverReachServices.observe(level, position);
        var physical = com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices.access()
                .getCurrent(level, net.minecraft.world.phys.Vec3.atCenterOf(position));
        helper.assertTrue(Math.abs(flow.currentX()) < 0.0001f && Math.abs(flow.currentZ() + 0.04f) < 0.0001f,
                "Reach heading or SI speed limit was not used by shared watershed current");
        helper.assertTrue(observation != null && observation.waterbodyId() == 4321
                        && observation.stage() == position.getY() && Math.abs(observation.velocityZ() - physical.z) < 0.0001
                        && Math.abs(observation.velocityX() - physical.x) < 0.0001,
                "Read-only API disagrees with shared current: " + observation + "; physical body="
                        + com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices.access().getWaterBody(level, position));
        boolean oldSmoothing = WaterSimulationConfig.SMOOTH_RIVER_CURRENTS.get();
        try {
            var saved = WatershedSavedData.get(level);
            var basins = WatershedBasinSavedData.get(level);
            for (int z = 0; z < 2; z++) for (int x = 0; x < 2; x++) {
                var neighbor = level.getChunkSource().getChunkNow(chunk.getPos().x + x, chunk.getPos().z + z);
                helper.assertTrue(neighbor != null, "Smoothing fixture neighbor was not loaded");
                BlockPos water = new BlockPos(neighbor.getPos().getMinBlockX() + 8, position.getY(), neighbor.getPos().getMinBlockZ() + 8);
                neighbor.getData(ModAttachments.GENERATED_WATER).recordCell(water,
                        GeneratedWaterChunk.Cell.of(8, false, GeneratedWaterChunk.BodyType.RIVER));
                var state = saved.getOrCreate(level, neighbor);
                saved.refreshTerrain(level, water);
                state.apply(new WatershedSimulationModel.Result(
                        0, 0, 0, 0.2f, 0, 0, false, 0, 1, x == 0 && z == 0 ? 0.06f : -0.03f, 0, 0, 0, 0), 0.8f, level.getGameTime());
                basins.union(regional.localBasinId(), state.localBasinId());
            }
            WaterSimulationConfig.SMOOTH_RIVER_CURRENTS.set(true);
            var smoothed = WatershedServices.localFlow(level, position);
            helper.assertTrue(smoothed.currentStrength() < 0.02f, "Smoothing fixture failed to reduce regional current");
            var observed = RiverReachServices.observe(level, position);
            var composed = com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices.access()
                    .getCurrent(level, net.minecraft.world.phys.Vec3.atCenterOf(position));
            helper.assertTrue(observed != null && Math.abs(observed.velocityZ() - composed.z) < 0.0001,
                    "Observation bypassed the shared smoothed current");
        } finally {
            WaterSimulationConfig.SMOOTH_RIVER_CURRENTS.set(oldSmoothing);
        }
        helper.assertTrue(volume.revision() == revision
                        && CanonicalWater.getTracked(level, position).volumeUnits() == 4096,
                "Reach sampling changed physical water inventory");
        helper.succeed();
    }

    @GameTest(template = "water_flow", timeoutTicks = 200)
    public static void connectedSlicesPreserveBordersInEitherOrder(GameTestHelper helper) {
        var terrain = RiverTileTerrain.create((x, z) -> x >= 24 ? 99 : 100,
                (x, z) -> x >= 24, (x, z) -> 0xFFFF);
        var edge = new RiverConnectedPlanner.Edge(7, 4321, 8, 8, 99, 24, 8, 99, 3, 1, true);
        var shape = RiverChannelShape.raster(List.of(edge), 0, 0, 32, 1, 4, true);
        CompoundTag[] expected = null;
        for (int first : new int[]{0, 1}) {
            var chunks = tile(helper, terrain);
            var receiver = chunks[1].getBlockState(new BlockPos(24, 99, 8));
            for (int owner : new int[]{first, 1 - first}) {
                helper.assertTrue(RiverConnectedGeneration.placeSlice(42, chunks[owner], shape, SETTINGS,
                        (x, z) -> x >= 0 && x < 2 && z >= 0 && z < 2 ? chunks[x + z * 2] : null,
                        pos -> true), "Connected slice was rejected or depended on its neighbor finishing first");
            }
            helper.assertTrue(chunks[1].getBlockState(new BlockPos(24, 99, 8)).equals(receiver), "Native receiving water changed");
            helper.assertTrue(chunks[0].getFluidState(new BlockPos(15, 99, 8)).isSource()
                    && chunks[1].getFluidState(new BlockPos(16, 99, 8)).isSource(), "Physical river has a chunk-border gap");
            var tags = new CompoundTag[]{chunks[0].getData(ModAttachments.GENERATED_WATER).serializeNBT(helper.getLevel().registryAccess()),
                    chunks[1].getData(ModAttachments.GENERATED_WATER).serializeNBT(helper.getLevel().registryAccess())};
            if (expected == null) expected = tags;
            else helper.assertTrue(expected[0].equals(tags[0]) && expected[1].equals(tags[1]), "Connected generation depends on chunk order");
            for (int i = 0; i < 2; i++) helper.assertFalse(chunks[i].hasData(ModAttachments.WATER_VOLUME), "Connected generation duplicated sparse inventory");
        }
        helper.succeed();
    }

    @GameTest(template = "water_flow", timeoutTicks = 200)
    public static void connectedWriteVetoNeverLeavesPartialWater(GameTestHelper helper) {
        var chunks = tile(helper, RiverTileTerrain.create((x, z) -> 100, (x, z) -> false, (x, z) -> 0xFFFF));
        var edge = new RiverConnectedPlanner.Edge(7, 4321, 8, 8, 99, 24, 8, 99, 3, 1, true);
        var shape = RiverChannelShape.raster(List.of(edge), 0, 0, 32, 1, 4, true);
        helper.assertFalse(RiverConnectedGeneration.placeSlice(42, chunks[0], shape, SETTINGS,
                (x, z) -> x >= 0 && x < 2 && z >= 0 && z < 2 ? chunks[x + z * 2] : null,
                pos -> pos.getX() != 12), "Denied connected write was ignored");
        helper.assertFalse(chunks[0].hasData(ModAttachments.GENERATED_WATER), "Connected veto left partial water");
        helper.succeed();
    }

    @GameTest(template = "water_flow", timeoutTicks = 200)
    public static void connectedSuspendedPredictionLeavesTerrainUntouched(GameTestHelper helper) {
        var chunks = tile(helper, RiverTileTerrain.create((x, z) -> 100, (x, z) -> false, (x, z) -> 0xFFFF));
        var edge = new RiverConnectedPlanner.Edge(7, 4321, 8, 8, 104, 24, 8, 104, 3, 1, false);
        var shape = RiverChannelShape.raster(List.of(edge), 0, 0, 32, 1, 4, true);
        var rejected = new java.util.ArrayList<RiverConnectedGeneration.Rejection>();
        helper.assertFalse(RiverConnectedGeneration.placeSlice(42, chunks[0], shape, SETTINGS,
                (x, z) -> x >= 0 && x < 2 && z >= 0 && z < 2 ? chunks[x + z * 2] : null,
                pos -> true, rejected::add), "Suspended water was admitted");
        helper.assertTrue(rejected.size() == 1 && rejected.getFirst().reason() == RiverConnectedGeneration.Reason.SUSPENDED,
                "Suspended admission did not report its bounded reason");
        helper.assertFalse(chunks[0].hasData(ModAttachments.GENERATED_WATER), "Suspended veto created generated water");
        helper.assertTrue(chunks[0].getBlockState(new BlockPos(8, 100, 8)).is(Blocks.STONE), "Suspended veto modified terrain");
        helper.succeed();
    }

    @GameTest(template = "water_flow", timeoutTicks = 200)
    public static void connectedChannelsClearDryPlantsButPreserveSolidFeatures(GameTestHelper helper) {
        var terrain = RiverTileTerrain.create((x, z) -> 100, (x, z) -> false, (x, z) -> 0xFFFF);
        var edge = new RiverConnectedPlanner.Edge(7, 4321, 8, 8, 99, 24, 8, 99, 3, 1, false);
        var shape = RiverChannelShape.raster(List.of(edge), 0, 0, 32, 1, 4, true);
        for (boolean plant : new boolean[]{true, false}) {
            var chunks = tile(helper, terrain);
            var position = new BlockPos(12, 101, 8);
            chunks[0].setBlockState(position, (plant ? Blocks.SHORT_GRASS : Blocks.OAK_LOG).defaultBlockState(), false);
            boolean placed = RiverConnectedGeneration.placeSlice(42, chunks[0], shape, SETTINGS,
                    (x, z) -> x >= 0 && x < 2 && z >= 0 && z < 2 ? chunks[x + z * 2] : null, pos -> true);
            helper.assertTrue(placed == plant, "Dry plants and solid features received the same admission");
            helper.assertTrue(plant ? chunks[0].getBlockState(position).isAir() : chunks[0].getBlockState(position).is(Blocks.OAK_LOG),
                    "Channel left an unsupported plant or removed a solid feature");
        }
        helper.succeed();
    }

    @GameTest(template = "water_flow", timeoutTicks = 200)
    public static void descendingBordersAcceptCompletedMatchingReachesAndRejectOldSolidWrappers(GameTestHelper helper) {
        var settings = new RiverGenerationSettings(true, 1, 2, 1, 2, 10, 256, 12288, true, true, true);
        var terrain = RiverTileTerrain.create((x, z) -> 100, (x, z) -> false, (x, z) -> 0xFFFF);
        var edge = new RiverConnectedPlanner.Edge(7, 4321, 8, 8, 99, 24, 8, 95, 3, 1, false);
        var shape = RiverChannelShape.raster(List.of(edge), 0, 0, 32, 1, 4, true);
        for (boolean wrapper : new boolean[]{false, true}) for (int first : new int[]{0, 1}) {
            var chunks = tile(helper, terrain, settings);
            helper.assertTrue(RiverConnectedGeneration.placeSlice(42, chunks[first], shape, settings,
                    (x, z) -> x >= 0 && x < 2 && z >= 0 && z < 2 ? chunks[x + z * 2] : null, pos -> true),
                    "First descending slice was rejected");
            var completed = new LevelChunk(helper.getLevel(), (ProtoChunk) chunks[first], null);
            chunks[first] = wrapper ? new net.minecraft.world.level.chunk.ImposterProtoChunk(completed, false) : completed;
            helper.assertTrue(RiverConnectedGeneration.placeSlice(42, chunks[1 - first], shape, settings,
                    (x, z) -> x >= 0 && x < 2 && z >= 0 && z < 2 ? chunks[x + z * 2] : null, pos -> true),
                    "Descending border depended on a matching neighbor staying a ProtoChunk; first=" + first + ", wrapper=" + wrapper);
        }
        var old = tile(helper, terrain, settings);
        old[1].setData(ModAttachments.RIVER_GENERATION, new RiverGenerationChunk());
        old[1] = new net.minecraft.world.level.chunk.ImposterProtoChunk(new LevelChunk(helper.getLevel(), (ProtoChunk) old[1], null), false);
        helper.assertFalse(RiverConnectedGeneration.placeSlice(42, old[0], shape, settings,
                (x, z) -> x >= 0 && x < 2 && z >= 0 && z < 2 ? old[x + z * 2] : null, pos -> true),
                "Old solid chunk wrapper was treated as a pending channel");
        helper.assertFalse(old[0].hasData(ModAttachments.GENERATED_WATER), "Old-neighbor veto left partial water");
        helper.succeed();
    }

    @GameTest(template = "water_flow", timeoutTicks = 200)
    public static void pendingNeighborFallingCurtainsMayOccupyCertifiedAir(GameTestHelper helper) {
        var edge = new RiverConnectedPlanner.Edge(7, 4321, 8, 8, 103, 24, 8, 99, 3, 1, false);
        var shape = RiverChannelShape.raster(List.of(edge), 0, 0, 32, 1, 4, true);
        var terrain = RiverTileTerrain.create((x, z) -> shape.at(x, z) == null ? 108 : shape.at(x, z).waterY(),
                (x, z) -> false, (x, z) -> 0xFFFF);
        for (int first : new int[]{0, 1}) {
            var chunks = tile(helper, terrain);
            for (int owner : new int[]{first, 1 - first}) {
                var rejected = new java.util.ArrayList<RiverConnectedGeneration.Rejection>();
                helper.assertTrue(RiverConnectedGeneration.placeSlice(42, chunks[owner], shape, SETTINGS,
                        (x, z) -> x >= 0 && x < 2 && z >= 0 && z < 2 ? chunks[x + z * 2] : null,
                        pos -> true, rejected::add), "Certified future falling air was rejected: " + rejected);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "water_flow", timeoutTicks = 200)
    public static void pendingNeighborSolidClearanceVetoesTheUpstreamSlice(GameTestHelper helper) {
        var chunks = tile(helper, RiverTileTerrain.create((x, z) -> 100, (x, z) -> false, (x, z) -> 0xFFFF));
        var edge = new RiverConnectedPlanner.Edge(7, 4321, 8, 8, 99, 24, 8, 99, 3, 1, false);
        var shape = RiverChannelShape.raster(List.of(edge), 0, 0, 32, 1, 4, true);
        var feature = new BlockPos(16, 101, 8);
        chunks[1].setBlockState(feature, Blocks.OAK_LOG.defaultBlockState(), false);
        helper.assertFalse(RiverConnectedGeneration.placeSlice(42, chunks[0], shape, SETTINGS,
                (x, z) -> x >= 0 && x < 2 && z >= 0 && z < 2 ? chunks[x + z * 2] : null, pos -> true),
                "Pending neighbor with an unwriteable solid clearance admitted an upstream fragment");
        helper.assertFalse(chunks[0].hasData(ModAttachments.GENERATED_WATER), "Neighbor clearance veto left partial water");
        helper.assertTrue(chunks[1].getBlockState(feature).is(Blocks.OAK_LOG), "Neighbor feature changed during admission");
        helper.succeed();
    }

    @GameTest(template = "water_flow", timeoutTicks = 200)
    public static void composedCurtainsRespectTheOriginalDropPolicyBeforeWrites(GameTestHelper helper) {
        var chunks = tile(helper, RiverTileTerrain.create((x, z) -> 100, (x, z) -> false, (x, z) -> 0xFFFF));
        var shape = new RiverChannelShape.Shape(List.of(new RiverChannelShape.Column(
                8, 8, 99, 98, 105, 1, 0, 1, RiverChannelColumn.Kind.WATERFALL, 4321)), 4, true);
        var rejected = new java.util.ArrayList<RiverConnectedGeneration.Rejection>();
        helper.assertFalse(RiverConnectedGeneration.placeSlice(42, chunks[0], shape, SETTINGS,
                (x, z) -> x >= 0 && x < 2 && z >= 0 && z < 2 ? chunks[x + z * 2] : null, pos -> true, rejected::add),
                "Composed curtain exceeded its certified drop policy");
        helper.assertTrue(rejected.size() == 1 && rejected.getFirst().reason() == RiverConnectedGeneration.Reason.DROP,
                "Composed curtain did not retain its drop admission policy");
        helper.assertFalse(chunks[0].hasData(ModAttachments.GENERATED_WATER), "Drop veto left partial water");
        helper.succeed();
    }

    @GameTest(template = "water_flow", timeoutTicks = 200)
    public static void optionalBankPlantsRespectLiveSurvivalAndRemainingWrites(GameTestHelper helper) {
        var values = com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs.commonSpec().getValues();
        var vegetation = (net.neoforged.neoforge.common.ModConfigSpec.BooleanValue) values.get("riverBanks.vegetation");
        var chance = (net.neoforged.neoforge.common.ModConfigSpec.DoubleValue) values.get("riverBanks.vegetationChance");
        boolean enabledBefore = vegetation.get();
        double chanceBefore = chance.get();
        try {
            var level = helper.getLevel();
            var anchor = helper.absolutePos(new BlockPos(5, 3, 5));
            if ((anchor.getX() & 15) < 2) anchor = anchor.offset(2, 0, 0);
            if ((anchor.getX() & 15) > 13) anchor = anchor.offset(-2, 0, 0);
            if ((anchor.getZ() & 15) < 2) anchor = anchor.offset(0, 0, 2);
            if ((anchor.getZ() & 15) > 13) anchor = anchor.offset(0, 0, -2);
            var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
            var plains = registry.getHolderOrThrow(Biomes.PLAINS);
            var chunk = new ProtoChunk(new ChunkPos(anchor), UpgradeData.EMPTY, level, registry, null);
            chunk.fillBiomesFromNoise((x, y, z, sampler) -> plains, level.getChunkSource().randomState().sampler());
            chunk.setPersistedStatus(ChunkStatus.CARVERS);
            chunk.setBlockState(anchor, Blocks.WATER.defaultBlockState(), false);
            var banks = List.of(anchor.east(), anchor.west(), anchor.north());
            for (var bank : banks) {
                chunk.setBlockState(bank.below(), Blocks.STONE.defaultBlockState(), false);
                chunk.setBlockState(bank, Blocks.GRASS_BLOCK.defaultBlockState(), false);
                level.setBlock(bank, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            }
            var protectedPlant = anchor.south().above();
            chunk.setBlockState(protectedPlant.below().below(), Blocks.STONE.defaultBlockState(), false);
            chunk.setBlockState(protectedPlant.below(), Blocks.GRASS_BLOCK.defaultBlockState(), false);
            chunk.setBlockState(protectedPlant, Blocks.OAK_LOG.defaultBlockState(), false);
            net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(chunk,
                    java.util.EnumSet.of(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG));
            var columns = List.of(new RiverChannelColumn(anchor.getX() & 15, anchor.getZ() & 15,
                    anchor.getY(), anchor.getY() - 1, anchor.getY(), 1, 0, 1, RiverChannelColumn.Kind.STREAM));
            vegetation.set(false);
            helper.assertTrue(com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.bank.RiverBankDecorator.decorate(
                    level, chunk, columns, 0, 0, 42, pos -> true, 1) == 0, "Disabled vegetation still placed a plant");
            vegetation.set(true);
            chance.set(1.0);
            helper.assertTrue(com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.bank.RiverBankDecorator.decorate(
                    level, chunk, columns, 0, 0, 42, pos -> false, 1) == 0, "Denied bank write was ignored");
            int written = com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.bank.RiverBankDecorator.decorate(
                    level, chunk, columns, 0, 0, 42, pos -> true, 1);
            helper.assertTrue(written == 1, "Enabled native bank plants did not honor the single remaining write: " + written);
            helper.assertTrue(banks.stream().filter(bank -> !chunk.getBlockState(bank.above()).isAir()).count() == 1,
                    "Bank decorator exceeded the remaining assembly write cap");
            helper.assertTrue(chunk.getBlockState(protectedPlant).is(Blocks.OAK_LOG), "Bank decoration replaced a solid feature");
            helper.assertFalse(chunk.hasData(ModAttachments.WATER_VOLUME), "Bank decoration created a water inventory");
        } finally {
            vegetation.set(enabledBefore);
            chance.set(chanceBefore);
        }
        helper.succeed();
    }

    @GameTest(template = "water_flow", timeoutTicks = 200)
    public static void riverHabitatUsesLiveComposedWaterWithoutLoadingOrChangingInventory(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(new BlockPos(5, 3, 5));
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        CanonicalWater.set(level, position, WaterVolumeChunk.WaterCell.still(4096, 0).withVelocity(1.25f, 0, 0.5f), true, false);
        var chunk = level.getChunkAt(position);
        var metadata = new RiverGenerationChunk();
        metadata.complete(4321, 0, 0, List.of(new RiverChannelColumn(position.getX() & 15, position.getZ() & 15,
                position.getY(), position.getY() - 1, position.getY(), 0, -1, 0.8f, RiverChannelColumn.Kind.STREAM)));
        chunk.setData(ModAttachments.RIVER_GENERATION, metadata);
        int loaded = level.getChunkSource().getLoadedChunksCount();
        long revision = chunk.getData(ModAttachments.WATER_VOLUME).revision();
        var habitat = com.thunder.wildernessodysseyapi.ecosystem.service.EcosystemServices.riverHabitat(level, position).orElseThrow();
        helper.assertTrue(habitat.waterbodyId() == 4321 && habitat.biome() != null && habitat.depth() > 0,
                "River habitat omitted actual loaded geometry or biome");
        helper.assertTrue(Math.abs(habitat.currentMetresPerSecond().x - 1.25) < 0.0001
                && Math.abs(habitat.currentMetresPerSecond().z - 0.5) < 0.0001, "Habitat bypassed canonical current ownership");
        var observation = RiverReachServices.observe(level, position);
        helper.assertTrue(observation != null && Math.abs(observation.velocityX() - 1.25f) < 0.0001f,
                "Reach observation bypassed the composed physical current");
        var distant = new BlockPos(29_000_000, position.getY(), 29_000_000);
        helper.assertTrue(com.thunder.wildernessodysseyapi.ecosystem.service.EcosystemServices.riverHabitat(level, distant).isEmpty(),
                "Unloaded river habitat was fabricated");
        helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == loaded
                && chunk.getData(ModAttachments.WATER_VOLUME).revision() == revision,
                "Habitat query loaded chunks or changed water");
        CanonicalWater.set(level, position, WaterVolumeChunk.WaterCell.EMPTY, true, false);
        helper.assertTrue(com.thunder.wildernessodysseyapi.ecosystem.service.EcosystemServices.riverHabitat(level, position).isEmpty(),
                "Dry reach metadata was reported as live habitat");
        helper.succeed();
    }

    @GameTest(template = "water_flow", timeoutTicks = 600)
    public static void vanillaAndAmplifiedPredictionUseNoChunksAndMatchNativeHeight(GameTestHelper helper) {
        var level = helper.getLevel();
        var settingsRegistry = level.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS);
        var plains = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        int before = level.getChunkSource().getLoadedChunksCount();
        for (var key : List.of(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD,
                net.minecraft.world.level.levelgen.NoiseGeneratorSettings.AMPLIFIED)) {
            var settings = settingsRegistry.getHolderOrThrow(key);
            var generator = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(
                    new net.minecraft.world.level.biome.FixedBiomeSource(plains), settings);
            var random = net.minecraft.world.level.levelgen.RandomState.create(settings.value(),
                    level.registryAccess().registryOrThrow(Registries.NOISE).asLookup(), 42);
            long[] timings = new long[64];
            for (int i = 0; i < 64; i++) {
                int x = i % 8 * 64 + 8;
                int z = i / 8 * 64 + 8;
                long start = System.nanoTime();
                var prediction = RiverNoiseTerrain.predict(generator, level, random, x, z);
                timings[i] = System.nanoTime() - start;
                int nativeHeight = generator.getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, random) - 1;
                helper.assertTrue(prediction.height() == nativeHeight, "Prediction differs from the generator's own terrain height");
                helper.assertTrue(prediction.equals(RiverNoiseTerrain.predict(generator, level, random, x, z)), "Noise prediction is not reproducible");
            }
            java.util.Arrays.sort(timings);
            double mean = java.util.Arrays.stream(timings).average().orElse(0) / 1_000_000.0;
            com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER.info(
                    "River predictor benchmark {}: 64 real noise columns, mean={}ms p95={}ms p99={}ms",
                    key.location(), mean, timings[60] / 1_000_000.0, timings[63] / 1_000_000.0);
            var planning = new RiverConnectedPlanner.Settings(4, 1, 2, 2, 1024);
            var first = RiverGenerationPlanning.plan(level, generator, random, 0, 0, SETTINGS, planning, 4);
            var misses = RiverGenerationPlanning.statistics(level).terrainMisses();
            helper.assertTrue(first.equals(RiverGenerationPlanning.plan(level, generator, random, 0, 0, SETTINGS, planning, 4)),
                    "Cached terrain changed the plan");
            helper.assertTrue(RiverGenerationPlanning.statistics(level).terrainMisses() == misses,
                    "An identical cached plan re-evaluated noise terrain");
        }
        helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == before, "Prediction requested or generated chunks");
        RiverGenerationPlanning.clear(level);
        helper.succeed();
    }

    private static RiverTileTerrain terrain() {
        return RiverTileTerrain.create((x, z) -> 140 + Math.max(Math.abs(x - 16), Math.abs(z - 16)) / 3,
                (x, z) -> false, (x, z) -> 0xFFFF);
    }

    private static long volume(GeneratedWaterChunk generated) {
        long units = 0;
        for (int column = 0; column < 256; column++) for (var span : generated.snapshot().spansForColumn(column)) {
            units += (long) (span.topY() - span.bottomY() + 1) * span.amountUnits();
        }
        return units;
    }

    private static ChunkAccess[] bowl(GameTestHelper helper) {
        return tile(helper, terrain());
    }

    private static ChunkAccess[] tile(GameTestHelper helper, RiverTileTerrain terrain) {
        return tile(helper, terrain, SETTINGS);
    }

    private static ChunkAccess[] tile(GameTestHelper helper, RiverTileTerrain terrain, RiverGenerationSettings settings) {
        var level = helper.getLevel();
        var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        var plains = registry.getHolderOrThrow(Biomes.PLAINS);
        ChunkAccess[] chunks = new ChunkAccess[4];
        for (int i = 0; i < 4; i++) {
            var chunk = new ProtoChunk(new ChunkPos(i % 2, i / 2), UpgradeData.EMPTY, level, registry, null);
            chunk.fillBiomesFromNoise((x, y, z, sampler) -> plains, level.getChunkSource().randomState().sampler());
            chunk.setPersistedStatus(ChunkStatus.CARVERS);
            int[] capture = new int[512];
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int wx = i % 2 * 16 + x;
                int wz = i / 2 * 16 + z;
                int height = terrain.height(wx, wz);
                capture[(x + z * 16) * 2] = height | (terrain.water(wx, wz) ? 0x10000 : 0);
                capture[(x + z * 16) * 2 + 1] = 0xFFFF;
                for (int y = height - 15; y <= height; y++) chunk.setBlockState(new BlockPos(wx, y, wz),
                        terrain.water(wx, wz) && y >= height - 1 ? Blocks.WATER.defaultBlockState() : Blocks.STONE.defaultBlockState(), false);
            }
            chunk.setData(ModAttachments.RIVER_GENERATION, RiverGenerationChunk.captured(settings.fingerprint(), capture));
            chunks[i] = chunk;
        }
        return chunks;
    }
}
