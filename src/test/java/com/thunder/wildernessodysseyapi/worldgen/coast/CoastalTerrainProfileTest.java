package com.thunder.wildernessodysseyapi.worldgen.coast;

import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoastalTerrainProfileTest {

    @Test
    void temperateTransitionRunsFromStrandlineToMeadow() {
        assertEquals(CoastalTerrainProfile.Zone.STRANDLINE,
                CoastalTerrainProfile.zone(CoastalWaveProfile.ShoreType.TEMPERATE, 1));
        assertEquals(CoastalTerrainProfile.Zone.OPEN_BEACH,
                CoastalTerrainProfile.zone(CoastalWaveProfile.ShoreType.TEMPERATE, 5));
        assertEquals(CoastalTerrainProfile.Zone.DUNE,
                CoastalTerrainProfile.zone(CoastalWaveProfile.ShoreType.TEMPERATE, 9));
        assertEquals(CoastalTerrainProfile.Zone.COASTAL_MEADOW,
                CoastalTerrainProfile.zone(CoastalWaveProfile.ShoreType.TEMPERATE, 16));
    }

    @Test
    void rockAndGlacialProfilesUseTheirOwnBands() {
        assertEquals(CoastalTerrainProfile.Zone.ROCKY_STRAND,
                CoastalTerrainProfile.zone(CoastalWaveProfile.ShoreType.ROCKY, 2));
        assertEquals(CoastalTerrainProfile.Zone.ROCKY_SLOPE,
                CoastalTerrainProfile.zone(CoastalWaveProfile.ShoreType.ROCKY, 8));
        assertEquals(CoastalTerrainProfile.Zone.ICE_STRAND,
                CoastalTerrainProfile.zone(CoastalWaveProfile.ShoreType.GLACIAL, 2));
        assertEquals(CoastalTerrainProfile.Zone.SNOWFIELD,
                CoastalTerrainProfile.zone(CoastalWaveProfile.ShoreType.GLACIAL, 10));
    }

    @Test
    void onlyDuneBandsCanRaiseTerrain() {
        assertEquals(0, CoastalTerrainProfile.duneRise(
                CoastalWaveProfile.ShoreType.DUNE,
                CoastalTerrainProfile.Zone.OPEN_BEACH,
                1.0,
                3));
        assertEquals(3, CoastalTerrainProfile.duneRise(
                CoastalWaveProfile.ShoreType.DUNE,
                CoastalTerrainProfile.Zone.DUNE,
                1.0,
                3));
        assertEquals(1, CoastalTerrainProfile.duneRise(
                CoastalWaveProfile.ShoreType.TEMPERATE,
                CoastalTerrainProfile.Zone.DUNE,
                1.0,
                3));
        assertEquals(0, CoastalTerrainProfile.duneRise(
                CoastalWaveProfile.ShoreType.ROCKY,
                CoastalTerrainProfile.Zone.DUNE,
                1.0,
                3));
    }

    @Test
    void tropicalBeachHasWideOpenSandAndNoDuneRise() {
        assertEquals(CoastalTerrainProfile.Zone.OPEN_BEACH,
                CoastalTerrainProfile.zone(CoastalWaveProfile.ShoreType.TROPICAL, 9));
        assertEquals(CoastalTerrainProfile.Zone.COASTAL_MEADOW,
                CoastalTerrainProfile.zone(CoastalWaveProfile.ShoreType.TROPICAL, 12));
        assertEquals(0, CoastalTerrainProfile.duneRise(
                CoastalWaveProfile.ShoreType.TROPICAL, CoastalTerrainProfile.Zone.DUNE, 1.0, 4));
        assertEquals(CoastalTerrainProfile.Detail.DRIFTWOOD, CoastalTerrainProfile.detail(
                CoastalWaveProfile.ShoreType.TROPICAL, CoastalTerrainProfile.Zone.OPEN_BEACH,
                0.0, 0.9, 1.0));
        assertEquals(CoastalTerrainProfile.Detail.PALM, CoastalTerrainProfile.detail(
                CoastalWaveProfile.ShoreType.TROPICAL, CoastalTerrainProfile.Zone.COASTAL_MEADOW,
                0.0, 0.2, 1.0));
    }

    @Test
    void tropicalGradingIsGentleBoundedAndDoesNotRaiseOrDrainTerrain() {
        assertEquals(62, CoastalTerrainProfile.tropicalSurfaceHeight(65, 63, 1, 4));
        assertEquals(64, CoastalTerrainProfile.tropicalSurfaceHeight(67, 63, 8, 4));
        assertEquals(66, CoastalTerrainProfile.tropicalSurfaceHeight(70, 63, 2, 4));
        assertEquals(62, CoastalTerrainProfile.tropicalSurfaceHeight(62, 63, 12, 4));
        assertEquals(61, CoastalTerrainProfile.tropicalSurfaceHeight(61, 63, 1, 4));
        assertEquals(80, CoastalTerrainProfile.tropicalSurfaceHeight(80, 63, 1, 4));
        assertEquals(67, CoastalTerrainProfile.tropicalSurfaceHeight(67, 63, 21, 4));
        assertEquals(67, CoastalTerrainProfile.tropicalSurfaceHeight(67, 63, 1, 0));
    }

    @Test
    void tropicalGradingTapersBackToTheInlandBankWithoutACutoffWall() {
        int previous = CoastalTerrainProfile.tropicalSurfaceHeight(70, 63, 1, 4);
        for (int distance = 2; distance <= 21; distance++) {
            int next = CoastalTerrainProfile.tropicalSurfaceHeight(70, 63, distance, 4);
            assertTrue(Math.abs(next - previous) <= 1, "Bank cutoff wall at distance " + distance);
            previous = next;
        }
        assertEquals(70, previous);
    }

    @Test
    void detailSelectionIsSparseConfigurableAndBiomeAware() {
        assertEquals(CoastalTerrainProfile.Detail.NONE, CoastalTerrainProfile.detail(
                CoastalWaveProfile.ShoreType.TEMPERATE,
                CoastalTerrainProfile.Zone.STRANDLINE,
                0.0,
                0.0,
                0.0));
        assertEquals(CoastalTerrainProfile.Detail.TIDE_POOL, CoastalTerrainProfile.detail(
                CoastalWaveProfile.ShoreType.TEMPERATE,
                CoastalTerrainProfile.Zone.STRANDLINE,
                0.0,
                0.0,
                1.0));
        assertEquals(CoastalTerrainProfile.Detail.SEA_STACK, CoastalTerrainProfile.detail(
                CoastalWaveProfile.ShoreType.ROCKY,
                CoastalTerrainProfile.Zone.ROCKY_STRAND,
                0.0,
                0.0,
                1.0));
        assertEquals(CoastalTerrainProfile.Detail.ICE_FRAGMENT, CoastalTerrainProfile.detail(
                CoastalWaveProfile.ShoreType.GLACIAL,
                CoastalTerrainProfile.Zone.GLACIAL_BEACH,
                0.0,
                0.5,
                1.0));
        assertEquals(CoastalTerrainProfile.Detail.NONE, CoastalTerrainProfile.detail(
                CoastalWaveProfile.ShoreType.TROPICAL,
                CoastalTerrainProfile.Zone.OPEN_BEACH,
                0.9,
                0.0,
                1.0));
    }

    @Test
    void duneRidgesTaperOnBothSidesAndStayContinuousAcrossChunkAndNegativeCoordinates() {
        var type = CoastalWaveProfile.ShoreType.DUNE;
        assertEquals(0, CoastalTerrainProfile.duneHeight(type, 1, 1, 1, 4));
        assertEquals(0, CoastalTerrainProfile.duneHeight(type, 21, 1, 1, 4));
        assertTrue(CoastalTerrainProfile.duneHeight(type, 13, 0.5, 0.5, 4) >= 3);
        for (int z = -32; z <= 32; z++) {
            int previous = 0;
            for (int x = -32; x <= 32; x++) {
                double broad = CoastalTerrainProfile.noise(31, x, z, 29, 5);
                double fine = CoastalTerrainProfile.noise(31, x, z, 9, 7);
                int rise = CoastalTerrainProfile.duneHeight(type, 13, broad, fine, 4);
                assertTrue(rise >= 0 && rise <= 4);
                if (x > -32) assertTrue(Math.abs(rise - previous) <= 1, "No chunk-coordinate ridge steps");
                previous = rise;
                assertEquals(broad, CoastalTerrainProfile.noise(31, x, z, 29, 5));
                if (x % 16 == 0) {
                    assertTrue(Math.abs(broad - CoastalTerrainProfile.noise(31, x - 1, z, 29, 5)) < 0.12);
                }
            }
        }
    }

    @Test
    void eachBiomeKeepsDistinctBandsAndPolarOffshoreFormationsStayWithTheGlacialOwner() {
        assertEquals(CoastalTerrainProfile.Zone.DUNE,
                CoastalTerrainProfile.zone(CoastalWaveProfile.ShoreType.DUNE, 18));
        assertEquals(CoastalTerrainProfile.Zone.COASTAL_MEADOW,
                CoastalTerrainProfile.zone(CoastalWaveProfile.ShoreType.TEMPERATE, 18));
        assertEquals(CoastalTerrainProfile.Zone.SNOWY_MEADOW,
                CoastalTerrainProfile.zone(CoastalWaveProfile.ShoreType.COLD, 12));
        for (int roll = 0; roll < 100; roll++) {
            assertTrue(CoastalTerrainProfile.detail(CoastalWaveProfile.ShoreType.GLACIAL,
                    CoastalTerrainProfile.Zone.ICE_STRAND, 0, roll / 100.0, 1)
                    != CoastalTerrainProfile.Detail.SEA_STACK);
        }
    }

    @Test
    void deterministicDetailRollsRespondMonotonicallyToDensityAcrossAllProfiles() {
        for (var type : CoastalWaveProfile.ShoreType.values()) {
            int sparse = 0;
            int full = 0;
            for (int index = 0; index < 10000; index++) {
                double presence = CoastalTerrainProfile.hashUnit(10, index, -index, 17);
                double variant = CoastalTerrainProfile.hashUnit(10, index, -index, 23);
                var zone = CoastalTerrainProfile.zone(type, 6);
                var low = CoastalTerrainProfile.detail(type, zone, presence, variant, 0.25);
                var high = CoastalTerrainProfile.detail(type, zone, presence, variant, 1);
                if (low != CoastalTerrainProfile.Detail.NONE) {
                    sparse++;
                    assertEquals(low, high);
                }
                if (high != CoastalTerrainProfile.Detail.NONE) full++;
            }
            assertTrue(sparse > 200 && full > sparse * 3 && full < 5000, type.toString());
        }
    }
}
