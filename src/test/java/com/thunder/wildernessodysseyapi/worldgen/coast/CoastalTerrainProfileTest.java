package com.thunder.wildernessodysseyapi.worldgen.coast;

import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                CoastalTerrainProfile.zone(CoastalWaveProfile.ShoreType.TEMPERATE, 14));
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
        assertEquals(67, CoastalTerrainProfile.tropicalSurfaceHeight(67, 63, 15, 4));
        assertEquals(67, CoastalTerrainProfile.tropicalSurfaceHeight(67, 63, 1, 0));
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
}
