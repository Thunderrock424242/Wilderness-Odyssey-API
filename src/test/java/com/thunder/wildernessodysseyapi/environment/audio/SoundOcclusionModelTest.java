package com.thunder.wildernessodysseyapi.environment.audio;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundOcclusionModelTest {

    private static final SoundOcclusionModel.Point LISTENER = point(0.5, 2.5, 0.5);
    private static final SoundOcclusionModel.Point SOURCE = point(12.5, 2.5, 0.5);

    @Test
    void openAirPreservesTheExistingMix() {
        assertEquals(1.0f, SoundOcclusionModel.gain(LISTENER, SOURCE, path -> 0.0));
    }

    @Test
    void thickBunkerWallsHeavilyDampOutdoorSound() {
        float gain = SoundOcclusionModel.gain(LISTENER, SOURCE,
                path -> path.x() >= 4 && path.x() <= 6 ? path.length() * 1.6 : 0.0);
        assertTrue(gain < 0.02f, "Three blocks of dense wall must heavily damp the surf");
        assertTrue(gain > 0.0f, "Occlusion should retain a small transmission floor");
    }

    @Test
    void thickerWallsTransmitLessThanOneBlock() {
        float thin = SoundOcclusionModel.gain(LISTENER, SOURCE,
                path -> path.x() == 4 ? path.length() * 1.6 : 0.0);
        float thick = SoundOcclusionModel.gain(LISTENER, SOURCE,
                path -> path.x() >= 4 && path.x() <= 6 ? path.length() * 1.6 : 0.0);
        assertTrue(thin < 0.3f);
        assertTrue(thick < thin * 0.1f);
    }

    @Test
    void anOpeningPreservesSoundThroughTheWall() {
        float gain = SoundOcclusionModel.gain(LISTENER, SOURCE, path ->
                path.x() == 4 && (path.y() < 2 || path.y() > 3 || path.z() != 0)
                        ? path.length() * 1.6 : 0.0);
        assertEquals(1.0f, gain);
    }

    @Test
    void roofDampsAnElevatedSource() {
        float gain = SoundOcclusionModel.gain(LISTENER, point(0.5, 40.5, 0.5),
                path -> path.y() >= 5 && path.y() <= 7 ? path.length() * 1.6 : 0.0);
        assertTrue(gain < 0.02f);
    }

    @Test
    void negativeCoordinatesTraceTheSameWallThickness() {
        float positive = SoundOcclusionModel.gain(LISTENER, SOURCE,
                path -> path.x() == 4 ? path.length() * 1.6 : 0.0);
        float negative = SoundOcclusionModel.gain(point(-0.5, 2.5, -0.5), point(-12.5, 2.5, -0.5),
                path -> path.x() == -5 ? path.length() * 1.6 : 0.0);
        assertEquals(positive, negative, 0.0001f);
    }

    @Test
    void axisAlignedRaysAtIntegerBoundariesStillCrossWalls() {
        float gain = SoundOcclusionModel.gain(point(0, 0, 0), point(12, -0.0, -0.0),
                path -> path.x() == 4 ? path.length() * 1.6 : 0.0);
        assertTrue(gain < 0.3f);
    }

    @Test
    void openingAThinDoorShapeRestoresTransmission() {
        float closed = SoundOcclusionModel.gain(LISTENER, SOURCE, path ->
                path.x() == 4 ? path.intersectionLength(4.4, 2, 0, 4.5875, 4, 1) * 6.0 : 0.0);
        float open = SoundOcclusionModel.gain(LISTENER, SOURCE, path ->
                path.x() == 4 ? path.intersectionLength(4, 2, 0, 5, 4, 0.1875) * 6.0 : 0.0);
        assertTrue(closed < 0.5f);
        assertEquals(1.0f, open);
    }

    @Test
    void aThinPillarDoesNotOccludeTheEntireSourceAperture() {
        float gain = SoundOcclusionModel.gain(LISTENER, SOURCE, path ->
                path.x() == 4 ? path.intersectionLength(4, 2, 0.45, 5, 3, 0.55) * 1.6 : 0.0);
        assertTrue(gain > 0.55f && gain < 0.9f);
    }

    @Test
    void thinCollisionShapesOnlyBlockRaysThatActuallyIntersectThem() {
        var crossing = new SoundOcclusionModel.CellPath(4, 2, 0,
                point(4.0, 2.5, 0.5), point(5.0, 2.5, 0.5));
        assertEquals(0.1875, crossing.intersectionLength(4.4, 2, 0, 4.5875, 3, 1), 0.000001);
        assertEquals(0.0, crossing.intersectionLength(4, 2, 0, 5, 3, 0.1875));
    }

    @Test
    void aDiagonalOnlyCountsTheThicknessInsideTheShape() {
        var crossing = new SoundOcclusionModel.CellPath(0, 0, 0,
                point(0, 0, 0), point(1, 1, 1));
        assertEquals(Math.sqrt(3) * 0.25,
                crossing.intersectionLength(0.5, 0.5, 0.5, 0.75, 0.75, 0.75), 0.000001);
    }

    @Test
    void unavailableTerrainDoesNotInventWalls() {
        assertEquals(1.0f, SoundOcclusionModel.gain(LISTENER, SOURCE, path -> Double.NaN));
    }

    @Test
    void unavailableTerrainDoesNotEraseAKnownWallNearTheListener() {
        float gain = SoundOcclusionModel.gain(LISTENER, SOURCE, path -> {
            if (path.x() >= 8) {
                return Double.NaN;
            }
            return path.x() == 4 ? path.length() * 1.6 : 0.0;
        });
        assertTrue(gain < 0.3f);
    }

    @Test
    void distantSourcesHaveABoundedLocalTrace() {
        AtomicInteger reads = new AtomicInteger();
        float gain = SoundOcclusionModel.gain(LISTENER, point(100_000, 100_000, 100_000), path -> {
            reads.incrementAndGet();
            assertTrue(path.end().x() < 70 && path.end().y() < 70 && path.end().z() < 70);
            return path.x() == 4 ? path.length() * 1.6 : 0.0;
        });
        assertTrue(reads.get() > 0 && reads.get() <= 600);
        assertTrue(gain < 1.0f);
    }

    @Test
    void invalidAndCoincidentEndpointsDoNotReadTheWorld() {
        AtomicInteger reads = new AtomicInteger();
        SoundOcclusionModel.LossSampler sampler = path -> {
            reads.incrementAndGet();
            return 1.0;
        };
        assertEquals(1.0f, SoundOcclusionModel.gain(LISTENER, LISTENER, sampler));
        assertEquals(1.0f, SoundOcclusionModel.gain(LISTENER, point(Double.NaN, 2, 0), sampler));
        assertEquals(1.0f, SoundOcclusionModel.gain(LISTENER, point(Double.POSITIVE_INFINITY, 2, 0), sampler));
        assertEquals(0, reads.get());
    }

    private static SoundOcclusionModel.Point point(double x, double y, double z) {
        return new SoundOcclusionModel.Point(x, y, z);
    }
}
