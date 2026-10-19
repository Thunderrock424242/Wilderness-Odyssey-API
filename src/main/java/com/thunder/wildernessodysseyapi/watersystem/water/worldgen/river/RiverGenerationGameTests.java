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
        helper.assertTrue(Math.abs(flow.currentX()) < 0.0001f && Math.abs(flow.currentZ() + 0.04f) < 0.0001f,
                "Reach heading or SI speed limit was not used by shared watershed current");
        helper.assertTrue(observation != null && observation.waterbodyId() == 4321
                        && observation.stage() == position.getY() && Math.abs(observation.velocityZ() + 0.8f) < 0.0001f,
                "Read-only API disagrees with shared current");
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
            helper.assertTrue(observed != null && Math.abs(observed.velocityZ() - smoothed.currentZ() * 20) < 0.0001f,
                    "Observation bypassed the shared smoothed current");
        } finally {
            WaterSimulationConfig.SMOOTH_RIVER_CURRENTS.set(oldSmoothing);
        }
        helper.assertTrue(volume.revision() == revision
                        && CanonicalWater.getTracked(level, position).volumeUnits() == 4096,
                "Reach sampling changed physical water inventory");
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
