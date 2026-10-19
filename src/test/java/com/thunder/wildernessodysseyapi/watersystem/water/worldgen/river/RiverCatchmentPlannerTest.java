package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class RiverCatchmentPlannerTest {
    private static final RiverGenerationSettings SETTINGS = new RiverGenerationSettings(
            true, 1.0, 2, 1, 2, 3, 256, 12288, true, true, true);

    @Test
    void elevatedLakeSpillsThroughItsLowestCertifiedSaddleToNativeWater() {
        var terrain = spillBasin(false);
        var plan = RiverCatchmentPlanner.plan(42, 0, 0, terrain, SETTINGS);
        assertTrue(plan.columns().stream().anyMatch(column -> column.x() == 8 && column.z() == 16
                && column.kind() == RiverChannelColumn.Kind.LAKE && column.waterY() == 141));
        int previous = 141;
        for (int x = 14; x < 24; x++) {
            final int cellX = x;
            var outlet = plan.columns().stream().filter(column -> column.x() == cellX && column.z() == 16).findFirst().orElseThrow();
            assertNotEquals(RiverChannelColumn.Kind.LAKE, outlet.kind());
            assertTrue(outlet.waterY() <= previous, "Lake outlet flowed uphill");
            assertTrue(outlet.waterY() >= 137);
            previous = outlet.waterY();
        }
        assertEquals(137, previous);
        assertTrue(plan.columns().stream().noneMatch(column -> terrain.water(column.x(), column.z())));
    }

    @Test
    void aLowerUnknownSpillCannotBeReplacedWithAHigherKnownOutlet() {
        var plan = RiverCatchmentPlanner.plan(42, 0, 0, spillBasin(true), SETTINGS);
        assertTrue(plan.columns().stream().noneMatch(column -> column.x() == 14 && column.z() == 16
                && column.kind() == RiverChannelColumn.Kind.STREAM));
    }

    @Test
    void fillsASealedMountainBasinAboveSeaLevel() {
        var plan = RiverCatchmentPlanner.plan(42, 0, 0, bowl(140), SETTINGS);
        assertFalse(plan.columns().isEmpty());
        assertTrue(plan.columns().stream().anyMatch(column -> column.kind() == RiverChannelColumn.Kind.LAKE));
        assertTrue(plan.columns().stream().allMatch(column -> column.waterY() >= 140));
        assertTrue(plan.columns().stream().allMatch(column -> column.x() > 0 && column.x() < 31
                && column.z() > 0 && column.z() < 31));
        assertTrue(plan.requiredWrites() <= SETTINGS.maximumWrites());
    }

    @Test
    void sealedLakeCrossesTheInternalChunkBorder() {
        var plan = RiverCatchmentPlanner.plan(42, 0, 0, bowl(100), SETTINGS);
        assertTrue(plan.columns().stream().anyMatch(column -> column.x() == 15));
        assertTrue(plan.columns().stream().anyMatch(column -> column.x() == 16));
        for (var column : plan.columns()) {
            if (column.kind() != RiverChannelColumn.Kind.STREAM) continue;
            assertTrue(plan.columns().stream().anyMatch(next ->
                    Math.abs(next.x() - column.x()) + Math.abs(next.z() - column.z()) == 1
                            && next.waterY() <= column.waterY()), "Isolated stream voxel");
        }
    }

    @Test
    void openSlopesRemainUncarvedRatherThanEndingAtTileBorders() {
        var terrain = RiverTileTerrain.create((x, z) -> 140 - x, (x, z) -> false, (x, z) -> 0xFFFF);
        assertTrue(RiverCatchmentPlanner.plan(42, 0, 0, terrain, SETTINGS).columns().isEmpty());
    }

    @Test
    void receivingWaterIsPreservedAndCanReceiveAnUplandStream() {
        var terrain = RiverTileTerrain.create((x, z) -> x >= 23 ? 100 : 100 + (23 - x) / 3,
                (x, z) -> x >= 23, (x, z) -> 0xFFFF);
        var plan = RiverCatchmentPlanner.plan(42, 0, 0, terrain, SETTINGS);
        assertFalse(plan.columns().isEmpty());
        assertTrue(plan.columns().stream().anyMatch(column -> column.kind() == RiverChannelColumn.Kind.STREAM));
        assertTrue(plan.columns().stream().noneMatch(column -> column.x() >= 23));
    }

    @Test
    void steepChannelsContainBoundedConnectedFallingColumns() {
        var terrain = RiverTileTerrain.create((x, z) -> x >= 23 ? 100 : 100 + (23 - x) * 2,
                (x, z) -> x >= 23, (x, z) -> 0xFFFF);
        var steep = new RiverGenerationSettings(true, 1, 2, 1, 2, 10, 256, 12288, true, true, true);
        var plan = RiverCatchmentPlanner.plan(42, 0, 0, terrain, steep);
        assertTrue(plan.columns().stream().anyMatch(column -> column.kind() == RiverChannelColumn.Kind.WATERFALL));
        assertTrue(plan.columns().stream().allMatch(column -> column.topY() - column.waterY() <= 4));
    }

    @Test
    void aCavityUnderTheLakeRejectsTheWholeAssembly() {
        var terrain = RiverTileTerrain.create((x, z) -> 140 + Math.max(Math.abs(x - 16), Math.abs(z - 16)) / 3,
                (x, z) -> false, (x, z) -> x == 16 && z == 16 ? 0 : 0xFFFF);
        assertTrue(RiverCatchmentPlanner.plan(42, 0, 0, terrain, SETTINGS).columns().isEmpty());
    }

    @Test
    void insufficientWriteBudgetNeverProducesAPartialRiver() {
        var tiny = new RiverGenerationSettings(true, 1.0, 2, 1, 2, 3, 256, 4, true, true, true);
        assertTrue(RiverCatchmentPlanner.plan(42, 0, 0, bowl(140), tiny).columns().isEmpty());
    }

    @Test
    void lowerReceivingWaterCannotReplaceARaisedSideWall() {
        var terrain = RiverTileTerrain.create((x, z) -> x == 11 && z == 5 ? 107 : x >= 23 ? 100 : 123 - x,
                (x, z) -> x >= 23 || x == 11 && z == 5, (x, z) -> 0xFFFF);
        var plan = RiverCatchmentPlanner.plan(42, 0, 0, terrain, SETTINGS);
        for (var column : plan.columns()) for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int nx = column.x() + direction[0];
            int nz = column.z() + direction[1];
            if (terrain.water(nx, nz)) assertTrue(terrain.height(nx, nz) >= column.waterY(),
                    "Low receiving water cannot seal a raised channel wall");
        }
    }

    @Test
    void concurrentRequestsProduceTheSameImmutableAssembly() throws Exception {
        var terrain = bowl(140);
        var expected = RiverCatchmentPlanner.plan(42, -32, -32, terrain, SETTINGS);
        try (var executor = Executors.newFixedThreadPool(4)) {
            Callable<RiverCatchmentPlanner.Plan> call = () -> RiverCatchmentPlanner.plan(42, -32, -32, terrain, SETTINGS);
            for (var result : executor.invokeAll(List.of(call, call, call, call))) assertEquals(expected, result.get());
        }
        assertThrows(UnsupportedOperationException.class, () -> expected.columns().clear());
    }

    private static RiverTileTerrain spillBasin(boolean openFrontier) {
        return RiverTileTerrain.create((x, z) -> {
            if (x >= 24 && z >= 14 && z <= 18) return 137;
            if (z == 16 && x >= 14) return Math.max(137, 142 - Math.max(0, x - 15));
            if (openFrontier && z == 16 && x <= 4) return 141;
            if (x >= 5 && x <= 13 && z >= 11 && z <= 21) return 140;
            return 145;
        }, (x, z) -> x >= 24 && z >= 14 && z <= 18, (x, z) -> 0xFFFF);
    }

    private static RiverTileTerrain bowl(int floor) {
        return RiverTileTerrain.create((x, z) -> floor + Math.max(Math.abs(x - 16), Math.abs(z - 16)) / 3,
                (x, z) -> false, (x, z) -> 0xFFFF);
    }
}
