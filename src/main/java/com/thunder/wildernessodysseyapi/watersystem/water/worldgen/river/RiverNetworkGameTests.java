package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WatershedSavedData;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WatershedSimulationModel;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Disposable world-backed checks; neither the planner nor inspection may mutate an existing world. */
@GameTestHolder("wildernessodysseyapi_river_tests")
@PrefixGameTestTemplate(false)
public final class RiverNetworkGameTests {
    private static final String TEMPLATE = "water_flow";

    private RiverNetworkGameTests() { }

    @GameTest(template = TEMPLATE)
    public static void inspectionPreservesBlocksAttachmentsAndCanonicalWork(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        CanonicalWater.set(level, position, WaterVolumeChunk.WaterCell.still(4096, 0), true, false);
        var chunk = level.getChunkAt(position);
        var cell = java.util.Objects.requireNonNull(CanonicalWater.getTracked(level, position),
                "Inspection fixture requires a tracked canonical cell");
        var state = chunk.getBlockState(position);
        var volume = chunk.getData(ModAttachments.WATER_VOLUME);
        long revision = volume.revision();
        long generatedRevision = chunk.getExistingData(ModAttachments.GENERATED_WATER)
                .map(water -> water.revision()).orElse(-1L);
        var work = CanonicalWater.diagnostics(level);
        int loaded = level.getChunkSource().getLoadedChunksCount();

        var source = level.getServer().createCommandSourceStack().withLevel(level)
                .withPosition(Vec3.atCenterOf(position)).withSuppressedOutput();
        int result = level.getServer().getCommands().getDispatcher().execute("wowater riverplan", source);

        helper.assertTrue(result == 1 && state.equals(chunk.getBlockState(position))
                        && cell.equals(CanonicalWater.getTracked(level, position))
                        && volume.revision() == revision
                        && chunk.getExistingData(ModAttachments.GENERATED_WATER)
                        .map(water -> water.revision()).orElse(-1L) == generatedRevision
                        && CanonicalWater.diagnostics(level).equals(work)
                        && level.getChunkSource().getLoadedChunksCount() == loaded,
                "Read-only river inspection changed terrain, water state, work or loaded chunks");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void missingTerrainRemainsUnknownWithoutChunkRequests(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos distant = new BlockPos(24_000_000, 64, 24_000_000);
        helper.assertTrue(level.getChunkSource().getChunkNow(distant.getX() >> 4, distant.getZ() >> 4) == null,
                "Disposable missing-terrain fixture was already loaded");
        int loaded = level.getChunkSource().getLoadedChunksCount();
        var plan = RiverNetworkPlanner.plan(LoadedRiverTerrain.capture(level, distant));
        helper.assertTrue(plan.nodes().isEmpty() && level.getChunkSource().getLoadedChunksCount() == loaded
                        && level.getChunkSource().getChunkNow(distant.getX() >> 4, distant.getZ() >> 4) == null,
                "Planner fabricated terrain or requested missing chunks");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void movingCanonicalWaterDoesNotReceiveItsRegionalVelocityTwice(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(4, 3, 4));
        // Broad contained surface keeps the center query independent of a dry bank.
        for (int z = -2; z <= 2; z++) for (int x = -2; x <= 2; x++) {
            BlockPos water = position.offset(x, 0, z);
            level.setBlock(water.below(), Blocks.STONE.defaultBlockState(), 3);
            CanonicalWater.set(level, water,
                    WaterVolumeChunk.WaterCell.still(4096, 0).withVelocity(1.2f, 0, 0), true, false);
            level.getChunkAt(water).getData(ModAttachments.GENERATED_WATER).recordCell(water,
                    GeneratedWaterChunk.Cell.of(8, false, GeneratedWaterChunk.BodyType.RIVER));
        }
        var metadata = WatershedSavedData.get(level).getOrCreate(level, level.getChunkAt(position));
        metadata.apply(new WatershedSimulationModel.Result(
                0, 0, 0, 0.2f, 0, 0, false, 0, 1, 0.06f, 0, 0, 0, 0), 0.8f, level.getGameTime());
        var before = java.util.Objects.requireNonNull(CanonicalWater.getTracked(level, position),
                "Current ownership fixture requires moving canonical water");
        var environment = WildernessWaterAuthority.sampleEnvironment(level,
                position.getX() + 0.5, position.getZ() + 0.5, 0);
        helper.assertTrue(WildernessWaterAuthority.getSurfaceHeight(level,
                        position.getX() + 0.5, position.getZ() + 0.5, 0) > position.getY(),
                "Generated river surface fixture was unavailable");
        helper.assertTrue(Math.abs(environment.currentStrength() - 1.2f) < 0.002f,
                "Canonical and regional currents were added twice: " + environment.currentStrength());
        helper.assertTrue(before.equals(CanonicalWater.getTracked(level, position)),
                "Sampling current changed canonical volume or momentum");
        helper.succeed();
    }
}
