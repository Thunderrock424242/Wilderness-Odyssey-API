package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RiverChannelShapeTest {
    private static final RiverConnectedPlanner.Edge EDGE = new RiverConnectedPlanner.Edge(
            42, 123, 8, 8, 110, 56, 8, 104, 16, 4, false);

    @Test
    void independentlyRasterizedChunkFacesMatchExactly() {
        var west = RiverChannelShape.raster(List.of(EDGE), 0, 0, 32, 2, 16, true);
        var east = RiverChannelShape.raster(List.of(EDGE), 32, 0, 32, 2, 16, true);
        int matches = 0;
        for (var column : west.columns()) if (column.x() >= 31) {
            assertEquals(column, east.at(column.x(), column.z()));
            matches++;
        }
        assertTrue(matches > 0);
    }

    @Test
    void widerAccumulatedChannelsHaveDeepCentersAndContinuousBends() {
        var shape = RiverChannelShape.raster(List.of(EDGE), 0, 0, 32, 2, 16, true);
        assertTrue(shape.columns().stream().anyMatch(column -> column.waterY() - column.floorY() >= 2));
        for (int x = 10; x < 30; x++) {
            final int columnX = x;
            assertTrue(shape.columns().stream().anyMatch(column -> column.x() == columnX));
        }
        assertTrue(shape.columns().stream().allMatch(column -> column.topY() - column.waterY() <= 16));
    }

    @Test
    void confluencesUseTheSameStageIndependentOfEdgeOrder() {
        var tributary = new RiverConnectedPlanner.Edge(7, 123, 24, 24, 108, 40, 8, 106, 3, 2, false);
        assertEquals(RiverChannelShape.raster(List.of(EDGE, tributary), 16, 0, 32, 2, 16, true),
                RiverChannelShape.raster(List.of(tributary, EDGE), 16, 0, 32, 2, 16, true));
    }

    @Test
    void disabledFallsRejectUnsupportedVerticalTransitions() {
        var cliff = new RiverConnectedPlanner.Edge(8, 123, 8, 8, 140, 24, 8, 100, 3, 1, true);
        assertTrue(RiverChannelShape.raster(List.of(cliff), 0, 0, 32, 1, 1, false).columns().isEmpty());
    }

    @Test
    void exactReceivingShorelineOpensAtItsNativeSurfaceAcrossTheWholeWidth() {
        var edge = new RiverConnectedPlanner.Edge(42, 123, 8, 8, 69, 24, 8, 62, 3, 1, true);
        var shape = RiverChannelShape.raster(List.of(edge), 0, 0, 32, 1, 4, true,
                (x, z) -> new RiverConnectedPlanner.Terrain(x >= 20 ? 62 : 70, x >= 20, 0.8f));
        assertFalse(shape.columns().isEmpty());
        for (var column : shape.columns()) if (column.x() >= 19) assertEquals(62, column.waterY());
        assertTrue(shape.columns().stream().anyMatch(column -> column.x() == 19 && column.z() == 8));
    }

    @Test
    void uncertainFineTerrainRejectsTheWholeShape() {
        assertTrue(RiverChannelShape.raster(List.of(EDGE), 0, 0, 32, 1, 4, true,
                (x, z) -> x == 19 ? null : new RiverConnectedPlanner.Terrain(120, false, 0.8f)).columns().isEmpty());
    }

    @Test
    void anEarlyNativeReceiverCannotCreateAnUphillContinuation() {
        var edge = new RiverConnectedPlanner.Edge(42, 123, 8, 8, 69, 24, 8, 62, 3, 1, true);
        var shape = RiverChannelShape.raster(List.of(edge), 0, 0, 32, 1, 16, true,
                (x, z) -> new RiverConnectedPlanner.Terrain(x == 14 || x >= 20 ? 62 : 70, x == 14 || x >= 20, 0.8f));
        for (var column : shape.columns()) {
            var next = shape.at(column.x() + 1, column.z());
            if (next != null && column.directionX() > 0)
                assertTrue(next.waterY() <= column.waterY(), "Fine receiver refinement introduced an uphill reach");
        }
    }

    @Test
    void fineShorelineRefinementMatchesOnOverlappingFaces() {
        RiverConnectedPlanner.TerrainSampler terrain = (x, z) -> new RiverConnectedPlanner.Terrain(x >= 54 ? 104 : 120, x >= 54, 0.8f);
        var west = RiverChannelShape.raster(List.of(EDGE), 0, 0, 32, 2, 16, true, terrain);
        var east = RiverChannelShape.raster(List.of(EDGE), 32, 0, 32, 2, 16, true, terrain);
        assertFalse(west.columns().isEmpty());
        assertFalse(east.columns().isEmpty());
        int matches = 0;
        for (var column : west.columns()) if (column.x() >= 31) {
            assertEquals(column, east.at(column.x(), column.z()));
            matches++;
        }
        assertTrue(matches > 0);
    }

    @Test
    void diagonalFineInletsNeverResumeUphillInEitherForwardComponent() {
        for (int rotation = 0; rotation < 4; rotation++) {
            var start = rotate(8, 8, rotation);
            var end = rotate(24, 24, rotation);
            int turn = rotation;
            var edge = new RiverConnectedPlanner.Edge(42, 123, start[0], start[1], 69, end[0], end[1], 62, 3, 1, true);
            var shape = RiverChannelShape.raster(List.of(edge), 0, 0, 32, 1, 4, true, (x, z) -> {
                var unrotated = rotate(x, z, (4 - turn) % 4);
                boolean wet = unrotated[0] == 16 && unrotated[1] == 14 || unrotated[0] >= 20 && unrotated[1] >= 20;
                return new RiverConnectedPlanner.Terrain(wet ? 62 : 70, wet, 0.8f);
            });
            for (var column : shape.columns()) {
                var xNext = shape.at(column.x() + (int) Math.signum(column.directionX()), column.z());
                var zNext = shape.at(column.x(), column.z() + (int) Math.signum(column.directionZ()));
                if (xNext != null) assertTrue(xNext.waterY() <= column.waterY(), "Fine inlet flowed uphill along X");
                if (zNext != null) assertTrue(zNext.waterY() <= column.waterY(), "Fine inlet flowed uphill along Z");
            }
        }
    }

    private static int[] rotate(int x, int z, int quarterTurns) {
        for (int i = 0; i < quarterTurns; i++) {
            int nextX = 32 - z;
            z = x;
            x = nextX;
        }
        return new int[]{x, z};
    }

    @Test
    void certifiedFullRouteGeometryCanBeMergedOnEitherSideOfABorder() {
        RiverConnectedPlanner.TerrainSampler terrain = (x, z) -> new RiverConnectedPlanner.Terrain(x >= 54 ? 104 : 120, x >= 54, 0.8f);
        var full = RiverChannelShape.rasterComplete(List.of(EDGE), 2, 16, true, terrain);
        assertNotNull(full.at(8, 8));
        assertNotNull(full.at(56, 8));
        var west = RiverChannelShape.mergeCertified(List.of(full), 0, 0, 32, 16, true);
        var east = RiverChannelShape.mergeCertified(List.of(full), 32, 0, 32, 16, true);
        for (var column : west.columns()) if (column.x() >= 31) assertEquals(column, east.at(column.x(), column.z()));
        assertFalse(west.columns().isEmpty());
        assertFalse(east.columns().isEmpty());
    }

    @Test
    void anUnrelatedComposedCurtainCannotEraseSharedBorderGeometry() {
        var valid = new RiverChannelShape.Shape(List.of(
                new RiverChannelShape.Column(31, 8, 100, 99, 100, 1, 0, 1, RiverChannelColumn.Kind.STREAM, 1),
                new RiverChannelShape.Column(32, 8, 100, 99, 100, 1, 0, 1, RiverChannelColumn.Kind.STREAM, 1)));
        var high = new RiverChannelShape.Shape(List.of(
                new RiverChannelShape.Column(4, 4, 120, 119, 120, 1, 0, 1, RiverChannelColumn.Kind.STREAM, 2)));
        var low = new RiverChannelShape.Shape(List.of(
                new RiverChannelShape.Column(4, 4, 100, 99, 100, 0, 1, 1, RiverChannelColumn.Kind.STREAM, 3)));
        var west = RiverChannelShape.mergeCertified(List.of(high, low, valid), 0, 0, 32, 4, true);
        var east = RiverChannelShape.mergeCertified(List.of(valid), 32, 0, 32, 4, true);
        assertEquals(east.at(31, 8), west.at(31, 8));
        assertNotNull(west.at(31, 8));
        assertEquals(20, west.at(4, 4).topY() - west.at(4, 4).waterY());
        assertEquals(4, west.maximumDrop());
    }

    @Test
    void lowerReceivingStageOwnsConfluenceDirectionRegardlessOfMergeOrder() {
        var high = new RiverChannelShape.Shape(List.of(
                new RiverChannelShape.Column(8, 8, 103, 102, 103, -1, 0, 1, RiverChannelColumn.Kind.STREAM, 2)));
        var low = new RiverChannelShape.Shape(List.of(
                new RiverChannelShape.Column(8, 8, 100, 99, 100, 1, 0, 1, RiverChannelColumn.Kind.STREAM, 3)));
        var lower = new RiverChannelShape.Shape(List.of(
                new RiverChannelShape.Column(8, 8, 100, 98, 100, 0, 1, 2, RiverChannelColumn.Kind.STREAM, 1)));
        var forward = RiverChannelShape.mergeCertified(List.of(high, low, lower), 0, 0, 32, 4, true);
        var reverse = RiverChannelShape.mergeCertified(List.of(lower, high, low), 0, 0, 32, 4, true);
        assertEquals(forward, reverse);
        assertTrue(forward.at(8, 8).directionX() > 0);
        assertTrue(forward.at(8, 8).directionZ() > 0);
        assertEquals(1, forward.at(8, 8).basinId());
    }
}
