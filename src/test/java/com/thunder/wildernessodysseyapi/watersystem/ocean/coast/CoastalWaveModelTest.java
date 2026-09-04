package com.thunder.wildernessodysseyapi.watersystem.ocean.coast;

import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoastalWaveModelTest {

    private static final OceanSeaState.Sample STORM = new OceanSeaState.Sample(
            0.94f, 1.0f, 0.0f, 22.0f, 1.8f, 2.0f, 0.8f, 0.92f);

    @Test
    void advancesThroughTheAuthoredCoastalLifecycle() {
        assertEquals(CoastalWaveModel.Stage.INCOMING, sample(0.10f).stage());
        assertEquals(CoastalWaveModel.Stage.SHOALING, sample(0.32f).stage());
        assertEquals(CoastalWaveModel.Stage.BREAKING, sample(0.50f).stage());
        assertEquals(CoastalWaveModel.Stage.RUN_UP, sample(0.68f).stage());
        assertEquals(CoastalWaveModel.Stage.RETREAT, sample(0.90f).stage());
    }

    @Test
    void runUpAdvancesThenRetreatsWithoutPlacingWater() {
        CoastalWaveModel.Sample earlyRunUp = sample(0.60f);
        CoastalWaveModel.Sample lateRunUp = sample(0.76f);
        CoastalWaveModel.Sample retreat = sample(0.91f);

        assertTrue(lateRunUp.runUpDistanceBlocks() > earlyRunUp.runUpDistanceBlocks());
        assertTrue(retreat.runUpDistanceBlocks() < lateRunUp.runUpDistanceBlocks());
        assertTrue(lateRunUp.maximumRunUpDistanceBlocks() <= CoastalWaveProfile.TEMPERATE.runUpDistanceBlocks());
    }

    @Test
    void stormsRaiseBreakEnergyAndRunUp() {
        CoastalWaveModel.Sample calm = CoastalWaveModel.sampleAtPhase(
                3L, 0.50f, 7.4f, CoastalWaveProfile.TEMPERATE,
                OceanSeaState.CALM, 0.05f, 1.0f);
        CoastalWaveModel.Sample storm = CoastalWaveModel.sampleAtPhase(
                3L, 0.50f, 7.4f, CoastalWaveProfile.TEMPERATE,
                STORM, 0.05f, 1.0f);

        assertTrue(storm.energy() > calm.energy());
        assertTrue(storm.breakerLift() > calm.breakerLift());
        assertTrue(storm.maximumRunUpDistanceBlocks() > calm.maximumRunUpDistanceBlocks());
        assertTrue(storm.foam() > calm.foam());
    }

    @Test
    void calmLocalWeatherStillHasSubstantialOceanSwellOnEveryShore() {
        for (CoastalWaveProfile.ShoreType type : CoastalWaveProfile.ShoreType.values()) {
            CoastalWaveModel.Sample calm = CoastalWaveModel.sampleAtPhase(
                    3L, 0.50f, 7.4f, CoastalWaveProfile.forType(type),
                    OceanSeaState.CALM, 0.05f, -1.0f);
            assertTrue(calm.breakerLift() > 0.80f, type + " should retain ocean swell");
        }
        CoastalWaveModel.Sample storm = sample(0.50f);
        assertTrue(storm.breakerLift() > 2.5f, "storm crests should remain much taller");
    }

    @Test
    void crestDoesNotJumpBackwardOrDisappearAtStageBoundaries() {
        CoastalWaveModel.Sample incomingEnd = sample(0.23999f);
        CoastalWaveModel.Sample shoalingStart = sample(0.24f);
        assertEquals(incomingEnd.waveHeight(), shoalingStart.waveHeight(), 0.001f);
        assertEquals(incomingEnd.crestDistanceFromShoreBlocks(),
                shoalingStart.crestDistanceFromShoreBlocks(), 0.001f);

        CoastalWaveModel.Sample shoalingEnd = sample(0.42999f);
        CoastalWaveModel.Sample breakingStart = sample(0.43f);
        assertEquals(shoalingEnd.waveHeight(), breakingStart.breakerLift(), 0.001f);
        assertEquals(shoalingEnd.crestDistanceFromShoreBlocks(),
                breakingStart.crestDistanceFromShoreBlocks(), 0.001f);
        assertEquals(0.0f, sample(0.0f).waveHeight(), 0.001f);
        assertEquals(0.0f, sample(0.56999f).breakerLift(), 0.001f);
    }

    @Test
    void shallowGentleBathymetryMovesTheBreakerFartherOffshore() {
        CoastalWaveModel.Sample gentle = CoastalWaveModel.sampleAtPhase(
                3L, 0.50f, 7.4f, CoastalWaveProfile.TEMPERATE,
                STORM, 0.08f, 0.08f, 2.5f, 1.0f, 44L);
        CoastalWaveModel.Sample steep = CoastalWaveModel.sampleAtPhase(
                3L, 0.50f, 7.4f, CoastalWaveProfile.TEMPERATE,
                STORM, 0.08f, 0.90f, 5.5f, 1.0f, 44L);

        assertTrue(gentle.breakerDistanceBlocks() > steep.breakerDistanceBlocks());
        assertTrue(gentle.breakerDistanceBlocks()
                <= CoastalWaveProfile.TEMPERATE.breakerDistanceBlocks());
    }

    @Test
    void steepRockyShoreReducesRunUpButRaisesBreakCharacter() {
        CoastalWaveModel.Sample flatSand = CoastalWaveModel.sampleAtPhase(
                2L, 0.68f, 7.4f, CoastalWaveProfile.DUNE, STORM, 0.03f, 1.0f);
        CoastalWaveModel.Sample steepRock = CoastalWaveModel.sampleAtPhase(
                2L, 0.68f, 7.4f, CoastalWaveProfile.ROCKY, STORM, 0.70f, 1.0f);
        CoastalWaveModel.Sample rockyBreak = CoastalWaveModel.sampleAtPhase(
                2L, 0.50f, 7.4f, CoastalWaveProfile.ROCKY, STORM, 0.70f, 1.0f);
        CoastalWaveModel.Sample flatRockyBreak = CoastalWaveModel.sampleAtPhase(
                2L, 0.50f, 7.4f, CoastalWaveProfile.ROCKY, STORM, 0.02f, 1.0f);

        assertTrue(steepRock.maximumRunUpDistanceBlocks()
                < flatSand.maximumRunUpDistanceBlocks());
        assertTrue(rockyBreak.spray() > 0.0f);
        assertTrue(rockyBreak.spray() > flatRockyBreak.spray());
    }

    @Test
    void stableSegmentAndGameTimeProduceTheSameSample() {
        CoastalWaveModel.Sample first = CoastalWaveModel.sample(
                0x24A4B7L, 88_240L, 0.35f, CoastalWaveProfile.COLD,
                STORM, 0.12f, 0.4f);
        CoastalWaveModel.Sample second = CoastalWaveModel.sample(
                0x24A4B7L, 88_240L, 0.35f, CoastalWaveProfile.COLD,
                STORM, 0.12f, 0.4f);

        assertEquals(first, second);
    }

    @Test
    void veryOldWorldsRetainTickLevelWaveProgression() {
        CoastalWaveModel.Sample first = CoastalWaveModel.sample(
                0x71B3L, 2_000_000_000L, 0.0f, CoastalWaveProfile.TEMPERATE,
                STORM, 0.08f, 0.2f);
        CoastalWaveModel.Sample next = CoastalWaveModel.sample(
                0x71B3L, 2_000_000_001L, 0.0f, CoastalWaveProfile.TEMPERATE,
                STORM, 0.08f, 0.2f);
        CoastalWaveModel.Sample interpolated = CoastalWaveModel.sample(
                0x71B3L, 2_000_000_000L, 0.5f, CoastalWaveProfile.TEMPERATE,
                STORM, 0.08f, 0.2f);

        assertTrue(Math.abs(next.normalizedPhase() - first.normalizedPhase()) > 0.0001f);
        assertTrue(Math.abs(interpolated.normalizedPhase() - first.normalizedPhase()) > 0.0001f);
        assertTrue(Math.abs(next.normalizedPhase() - interpolated.normalizedPhase()) > 0.0001f);
    }

    @Test
    void segmentDefensivelyCopiesTopologyAndNormalizesItsNormal() {
        CoastalSegment.RunUpCell cell = new CoastalSegment.RunUpCell(4, 63, 9, 1.0f);
        CoastalSegment segment = new CoastalSegment(
                9L, CoastalWaveProfile.TEMPERATE, 4, 63.875f, 8,
                3.0f, 4.0f, 0.15f, 3.5f, 0.22f,
                List.of(new CoastalSegment.ShorelinePoint(
                        4,
                        63.875f,
                        8,
                        List.of(cell),
                        List.of(new CoastalSegment.NearshoreCell(4, 63.875f, 8, 1.5f, 0.0f))
                ))
        );

        assertEquals(1.0f,
                (float) Math.hypot(segment.landwardNormalX(), segment.landwardNormalZ()),
                0.0001f);
        assertEquals(1, segment.shoreline().size());
        assertEquals(1, segment.shoreline().getFirst().runUpCells().size());
    }

    private static CoastalWaveModel.Sample sample(float phase) {
        return CoastalWaveModel.sampleAtPhase(
                1L, phase, 7.4f, CoastalWaveProfile.TEMPERATE,
                STORM, 0.08f, 0.8f);
    }
}
