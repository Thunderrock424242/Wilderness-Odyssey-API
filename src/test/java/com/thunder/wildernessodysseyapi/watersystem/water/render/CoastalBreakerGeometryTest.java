package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalSegment;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoastalBreakerGeometryTest {

    private static final CoastalSegment.ShorelinePoint SHORE = new CoastalSegment.ShorelinePoint(
            0, 63.875f, 0, List.of(), List.of(
            new CoastalSegment.NearshoreCell(0, 63.875f, 0, 1.0f, 0.0f),
            new CoastalSegment.NearshoreCell(-1, 63.875f, 0, 1.0f, 1.0f),
            new CoastalSegment.NearshoreCell(-2, 64.125f, 0, 1.0f, 2.0f)));

    @Test
    void rendersFullSwellHeightWithoutTheFormerSecondAttenuation() {
        for (var stage : List.of(CoastalWaveModel.Stage.INCOMING, CoastalWaveModel.Stage.SHOALING)) {
            var shape = CoastalBreakerGeometry.sample(SHORE, wave(stage, 1.0f));
            assertEquals(1.25f, shape.crestHeight(), 0.0001f);
        }
        var breaking = CoastalBreakerGeometry.sample(SHORE,
                wave(CoastalWaveModel.Stage.BREAKING, 1.0f));
        assertEquals(1.60f, breaking.crestHeight(), 0.0001f);
    }

    @Test
    void hasASeawardSlopeAndADescendingLandwardFace() {
        var shape = CoastalBreakerGeometry.sample(SHORE,
                wave(CoastalWaveModel.Stage.BREAKING, 1.0f));
        assertTrue(shape.backOffset() < 0.0f);
        assertTrue(shape.lipOffset() > 0.0f);
        assertTrue(shape.frontOffset() > shape.lipOffset());
        assertTrue(shape.crestHeight() > shape.lipHeight());
        assertTrue(shape.lipHeight() > 0.0f);
        assertEquals(3, CoastalBreakerGeometry.QUADS_PER_CREST);
    }

    @Test
    void interpolatesPositionAndHeightAcrossWaterCells() {
        var first = CoastalBreakerGeometry.sample(SHORE,
                wave(CoastalWaveModel.Stage.SHOALING, 1.25f));
        var next = CoastalBreakerGeometry.sample(SHORE,
                wave(CoastalWaveModel.Stage.SHOALING, 1.30f));
        assertEquals(1.25f, first.distanceFromShore(), 0.0001f);
        assertEquals(63.9375f, first.surfaceY(), 0.0001f);
        assertEquals(0.05f, next.distanceFromShore() - first.distanceFromShore(), 0.0001f);
        assertEquals(0.0125f, next.surfaceY() - first.surfaceY(), 0.0001f);
    }

    @Test
    void feetStayInsideCachedWaterAtBothEndsOfTheStrip() {
        for (float distance : new float[]{-4.0f, 0.0f, 0.25f, 1.75f, 2.0f, 8.0f}) {
            var shape = CoastalBreakerGeometry.sample(SHORE,
                    wave(CoastalWaveModel.Stage.BREAKING, distance));
            assertTrue(shape.distanceFromShore() >= 0.0f);
            assertTrue(shape.distanceFromShore() <= 2.0f);
            assertTrue(shape.distanceFromShore() - shape.frontOffset() >= -0.451f);
            assertTrue(shape.distanceFromShore() - shape.backOffset() <= 2.451f);
        }
    }

    @Test
    void retreatDoesNotKeepAnOffshoreWallStanding() {
        assertEquals(0.0f, CoastalBreakerGeometry.sample(SHORE,
                wave(CoastalWaveModel.Stage.RETREAT, 1.0f)).crestHeight());
    }

    private static CoastalWaveModel.Sample wave(CoastalWaveModel.Stage stage, float distance) {
        return new CoastalWaveModel.Sample(stage, 1L, 0.5f, 0.5f, 0.3f,
                1.25f, 1.60f, 1.0f, distance, 0.5f, 0.0f, 3.0f, 0.2f, 0.1f);
    }
}
