package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers conservation bookkeeping for solid-block displacement. */
class CanonicalWaterDisplacementTest {

    @Test
    void retainsEveryUnitThatDestinationsCannotAccept() {
        WaterVolumeChunk.WaterCell source = new WaterVolumeChunk.WaterCell(
                WaterVolumeChunk.UNITS_PER_BLOCK,
                0.3f,
                -0.1f,
                0.2f,
                WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED | WaterVolumeChunk.FLAG_SLEEPING,
                WaterVolumeChunk.WaterCell.DEFAULT_TEMPERATURE_MILLI_KELVIN
        );
        int movedUnits = 1_537;

        WaterVolumeChunk.WaterCell residual =
                CanonicalWater.retainedDisplacementResidual(source, movedUnits);

        assertAll(
                () -> assertEquals(source.volumeUnits(), movedUnits + residual.volumeUnits()),
                () -> assertTrue(residual.displacementReservoir()),
                () -> assertFalse(residual.hostedWater()),
                () -> assertFalse(residual.sleeping()),
                () -> assertEquals(0,
                        residual.flags() & WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED),
                () -> assertEquals(source.velocityX(), residual.velocityX()),
                () -> assertEquals(source.temperatureMilliKelvin(),
                        residual.temperatureMilliKelvin())
        );
    }

    @Test
    void omitsResidualWhenEveryUnitMoved() {
        WaterVolumeChunk.WaterCell source = WaterVolumeChunk.WaterCell.still(
                WaterVolumeChunk.UNITS_PER_BLOCK,
                WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED
        );

        WaterVolumeChunk.WaterCell residual =
                CanonicalWater.retainedDisplacementResidual(source, source.volumeUnits());

        assertEquals(WaterVolumeChunk.WaterCell.EMPTY, residual);
    }

    @Test
    void retainedReservoirIsNotReportedAsOccupiableWater() {
        WaterVolumeChunk.WaterCell source = WaterVolumeChunk.WaterCell.still(
                WaterVolumeChunk.UNITS_PER_BLOCK,
                WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED
        );
        WaterVolumeChunk.WaterCell residual =
                CanonicalWater.retainedDisplacementResidual(source, 512);

        WildernessWaterAuthority.CellAuthority authority =
                WildernessWaterAuthority.fromCanonical(residual, false, false);

        assertAll(
                () -> assertEquals(
                        WildernessWaterAuthority.WaterSource.DISPLACEMENT_RESERVOIR,
                        authority.source()
                ),
                () -> assertFalse(authority.water()),
                () -> assertTrue(authority.authorityOwned()),
                () -> assertFalse(authority.replacementSurfaceSafe()),
                () -> assertEquals(residual.volumeUnits(), authority.volumeUnits()),
                () -> assertTrue(authority.canonicalTracked())
        );
    }
}
