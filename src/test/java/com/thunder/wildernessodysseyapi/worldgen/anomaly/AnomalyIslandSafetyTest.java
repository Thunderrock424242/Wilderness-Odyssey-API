package com.thunder.wildernessodysseyapi.worldgen.anomaly;

import org.junit.jupiter.api.Test;

import static com.thunder.wildernessodysseyapi.worldgen.anomaly.AnomalyIslandSafety.Rejection.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Exercises the full preflight policy without Minecraft world mutation. */
class AnomalyIslandSafetyTest {
    private static final AnomalyIslandSafety.Bounds BOUNDS = new AnomalyIslandSafety.Bounds(-14, 100, -14, -8, 106, -8);

    @Test
    void acceptsEmptyElevatedSpaceInANegativeCoordinateChunk() {
        assertEquals(NONE, evaluate(BOUNDS, new Site()));
    }

    @Test
    void rejectsChunkBoundaryBeforeReadingWorldState() {
        Site site = new Site();
        assertEquals(CHUNK_BOUNDARY, evaluate(new AnomalyIslandSafety.Bounds(-2, 100, -14, 4, 106, -8), site));
        assertEquals(0, site.reads);
    }

    @Test
    void rejectsUnavailableChunkWithoutReadingBlocks() {
        Site site = new Site();
        site.available = false;
        assertEquals(CHUNK_UNAVAILABLE, evaluate(BOUNDS, site));
        assertEquals(0, site.reads);
    }

    @Test
    void rejectsStartsOrReferencesBeforeReadingBlocks() {
        Site site = new Site();
        site.protectedChunk = true;
        assertEquals(STRUCTURE_PRESENT, evaluate(BOUNDS, site));
        assertEquals(0, site.reads);
    }

    @Test
    void checksFarCornerAndAirMarginBeforeAnyPlacement() {
        Site site = new Site();
        site.obstructedX = -7;
        site.obstructedY = 107;
        site.obstructedZ = -7;
        assertEquals(OBSTRUCTED, evaluate(BOUNDS, site));
        assertTrue(site.reads > 300);
    }

    @Test
    void rejectsBlockEntitiesEvenWhenTheCellReportsAir() {
        Site site = new Site();
        site.blockEntity = true;
        assertEquals(BLOCK_ENTITY, evaluate(BOUNDS, site));
    }

    @Test
    void rejectsGroundWithoutTheRequiredSeparation() {
        Site site = new Site();
        site.ground = 93;
        assertEquals(GROUND_CLEARANCE, evaluate(BOUNDS, site));
    }

    @Test
    void rejectsBuildHeightMarginsAndOversizedTemplates() {
        assertEquals(BUILD_HEIGHT, evaluate(new AnomalyIslandSafety.Bounds(-14, 313, -14, -8, 319, -8), new Site()));
        assertEquals(INVALID_BOUNDS, evaluate(new AnomalyIslandSafety.Bounds(-15, 100, -14, -2, 106, -8), new Site()));
    }

    @Test
    void rejectsAirMarginWhoseIterationWouldOverflow() {
        Site site = new Site();
        assertEquals(INVALID_BOUNDS, evaluate(new AnomalyIslandSafety.Bounds(
                Integer.MAX_VALUE - 7, 100, 2, Integer.MAX_VALUE - 1, 106, 8), site));
        assertEquals(0, site.reads);
    }

    @Test
    void rejectsUnavailableWorldgenWriteRegion() {
        Site site = new Site();
        site.writable = false;
        assertEquals(WRITE_UNAVAILABLE, evaluate(BOUNDS, site));
    }

    @Test
    void rejectsOutOfBoundsTemplateCoordinatesAndInvalidPaletteIndices() {
        assertTrue(AnomalyIslandSafety.templateBlockFits(9, 13, 9, 4, 8, 12, 8, 3));
        assertFalse(AnomalyIslandSafety.templateBlockFits(9, 13, 9, 4, 9, 12, 8, 3));
        assertFalse(AnomalyIslandSafety.templateBlockFits(9, 13, 9, 4, 8, -1, 8, 3));
        assertFalse(AnomalyIslandSafety.templateBlockFits(9, 13, 9, 4, 8, 12, 8, 4));
        assertFalse(AnomalyIslandSafety.templateBlockFits(9, 13, 9, 4, 8, 12, 8, -1));
    }

    private static AnomalyIslandSafety.Rejection evaluate(AnomalyIslandSafety.Bounds bounds, Site site) {
        return AnomalyIslandSafety.evaluate(bounds, -64, 320, site);
    }

    private static final class Site implements AnomalyIslandSafety.Site {
        boolean available = true;
        boolean protectedChunk;
        boolean blockEntity;
        boolean writable = true;
        int ground = 80;
        int reads;
        int obstructedX = Integer.MAX_VALUE;
        int obstructedY;
        int obstructedZ;

        public boolean chunkAvailable(int x, int z) { return available; }
        public boolean structureProtected(int x, int z) { return protectedChunk; }
        public int groundHeight(int x, int z) { return ground; }
        public boolean blockEntityAt(int x, int y, int z) { return blockEntity; }
        public boolean canWriteAt(int x, int y, int z) { return writable; }
        public boolean airAt(int x, int y, int z) {
            reads++;
            return x != obstructedX || y != obstructedY || z != obstructedZ;
        }
    }
}
