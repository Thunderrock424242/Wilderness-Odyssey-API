package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies compact generation metadata without constructing a Minecraft level. */
class GeneratedWaterChunkTest {

    @Test
    void mergesSplitsAndRemovesVerticalRuns() {
        GeneratedWaterChunk generated = new GeneratedWaterChunk();
        GeneratedWaterChunk.Cell ocean = GeneratedWaterChunk.Cell.of(
                8, false, GeneratedWaterChunk.BodyType.OCEAN);
        BlockPos pos = new BlockPos(0, 61, 0);

        generated.recordCell(pos.below(), ocean);
        generated.recordCell(pos, ocean);
        generated.recordCell(pos.above(), ocean);
        assertEquals(1, generated.spanCount());
        assertEquals(60, generated.topSpan(0, 0).bottomY());
        assertEquals(62, generated.topSpan(0, 0).topY());

        generated.recordCell(pos, null);
        assertEquals(2, generated.spanCount());
        assertNull(generated.spanAt(pos));
        assertNotNull(generated.spanAt(pos.below()));
        assertNotNull(generated.spanAt(pos.above()));

        generated.recordCell(pos.below(), null);
        generated.recordCell(pos.above(), null);
        assertEquals(0, generated.spanCount());
    }

    @Test
    void identicalWritesDoNotIncrementRevision() {
        GeneratedWaterChunk generated = new GeneratedWaterChunk();
        GeneratedWaterChunk.Cell river = GeneratedWaterChunk.Cell.of(
                5, true, GeneratedWaterChunk.BodyType.RIVER);
        BlockPos pos = new BlockPos(4, -32, 7);

        assertTrue(generated.recordCell(pos, river));
        long revision = generated.revision();
        assertFalse(generated.recordCell(pos, river));
        assertEquals(revision, generated.revision());
    }

    @Test
    void roundTripsSignedHeightBodyWeightsAndBoundaryMasks() {
        GeneratedWaterChunk original = new GeneratedWaterChunk();
        original.recordCell(new BlockPos(15, -64, 0), GeneratedWaterChunk.Cell.of(
                4, true, GeneratedWaterChunk.BodyType.RIVER));
        assertTrue(original.recordSurfaceCover(new BlockPos(15, -63, 0), true));

        GeneratedWaterChunk decoded = new GeneratedWaterChunk();
        decoded.deserializeNBT(null, original.serializeNBT(null));
        GeneratedWaterChunk.WaterSpan span = decoded.spanAt(15, -64, 0);

        assertNotNull(span);
        assertEquals(4, span.cell().amount());
        assertTrue(span.cell().falling());
        assertEquals(GeneratedWaterChunk.BodyType.RIVER, span.cell().bodyType());
        assertEquals(255, span.cell().riverWeight());
        assertEquals(1 << 15, Short.toUnsignedInt(decoded.snapshot().northMask()));
        assertEquals(1, Short.toUnsignedInt(decoded.snapshot().eastMask()));
        assertTrue(decoded.snapshot().surfaceCovered(15, 0));
    }
}
