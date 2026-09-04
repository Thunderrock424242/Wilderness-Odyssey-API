package com.thunder.wildernessodysseyapi.watersystem.water.render;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the generation-aware fallback-to-custom renderer handoff. */
class WaterSurfaceHandoffTest {

    @Test
    void publishesOnlyAfterEveryExpectedSectionUpload() {
        WaterSurfaceHandoff handoff = new WaterSurfaceHandoff();
        long lower = SectionPos.asLong(3, 4, -2);
        long upper = SectionPos.asLong(3, 5, -2);
        long chunk = ChunkPos.asLong(3, -2);
        WaterSurfaceHandoff.SectionMask lowerMask = mask(1, 70);
        WaterSurfaceHandoff.SectionMask upperMask = mask(200);

        handoff.beginSuppression(chunk, Map.of(lower, lowerMask, upper, upperMask));
        assertTrue(handoff.suppressionRequested(chunk));
        assertFalse(handoff.customVisible(chunk));

        WaterHandoffReceipt lowerReceipt = compile(handoff, lower, 1, 70);
        assertTrue(lowerReceipt.valid());
        assertFalse(handoff.acknowledgeUpload(lowerReceipt));
        assertFalse(handoff.customVisible(chunk));

        WaterHandoffReceipt upperReceipt = compile(handoff, upper, 200);
        assertTrue(upperReceipt.valid());
        assertTrue(handoff.acknowledgeUpload(upperReceipt));
        assertTrue(handoff.customVisible(chunk));
        assertTrue(handoff.shouldSuppressTop(lower, 1));
        assertTrue(handoff.shouldSuppressTop(upper, 200));
    }

    @Test
    void skippedFluidCallbacksCannotStrandAlreadySuppressedTops() {
        WaterSurfaceHandoff handoff = new WaterSurfaceHandoff();
        long section = SectionPos.asLong(0, 4, 0);
        long chunk = ChunkPos.asLong(0, 0);
        handoff.beginSuppression(chunk, Map.of(section, mask(4, 5)));

        handoff.beginCompilation(section);
        assertTrue(handoff.shouldSuppressTop(section, 4));
        WaterHandoffReceipt receipt = handoff.finishCompilation(section);

        assertTrue(receipt.valid());
        assertFalse(handoff.customVisible(chunk));
        assertTrue(handoff.acknowledgeUpload(receipt));
        assertTrue(handoff.customVisible(chunk));
    }

    @Test
    void identicalSnapshotRefreshKeepsTheInFlightReceiptValid() {
        WaterSurfaceHandoff handoff = new WaterSurfaceHandoff();
        long section = SectionPos.asLong(3, 3, -4);
        long chunk = ChunkPos.asLong(3, -4);
        Map<Long, WaterSurfaceHandoff.SectionMask> sections = Map.of(section, mask(1, 2));
        long generation = handoff.beginSuppression(chunk, sections);
        WaterHandoffReceipt receipt = compile(handoff, section, 1, 2);

        assertEquals(generation, handoff.beginSuppression(chunk, sections));
        assertTrue(handoff.acknowledgeUpload(receipt));
        assertTrue(handoff.customVisible(chunk));
    }

    @Test
    void identicalRefreshRetainsSectionsThatAlreadyUploaded() {
        WaterSurfaceHandoff handoff = new WaterSurfaceHandoff();
        long lower = SectionPos.asLong(0, 3, 0);
        long upper = SectionPos.asLong(0, 4, 0);
        long chunk = ChunkPos.asLong(0, 0);
        Map<Long, WaterSurfaceHandoff.SectionMask> sections = Map.of(
                lower, mask(1), upper, mask(2));
        handoff.beginSuppression(chunk, sections);
        assertFalse(handoff.acknowledgeUpload(compile(handoff, lower, 1)));

        handoff.beginSuppression(chunk, sections);
        assertTrue(handoff.acknowledgeUpload(compile(handoff, upper, 2)));
        assertTrue(handoff.customVisible(chunk));
    }

    @Test
    void fullyOccludedSectionStillCompletesItsUploadHandshake() {
        WaterSurfaceHandoff handoff = new WaterSurfaceHandoff();
        long section = SectionPos.asLong(0, 3, 0);
        long chunk = ChunkPos.asLong(0, 0);
        handoff.beginSuppression(chunk, Map.of(section, mask(1)));
        handoff.beginCompilation(section);

        assertTrue(handoff.acknowledgeUpload(handoff.finishCompilation(section)));
        assertTrue(handoff.customVisible(chunk));
    }

    @Test
    void compilationStartedBeforeIntentKeepsTheFallback() {
        WaterSurfaceHandoff handoff = new WaterSurfaceHandoff();
        long section = SectionPos.asLong(-7, 2, 11);
        long chunk = ChunkPos.asLong(-7, 11);

        handoff.beginCompilation(section);
        handoff.beginSuppression(chunk, Map.of(section, mask(12)));

        assertFalse(handoff.shouldSuppressTop(section, 12));
        assertFalse(handoff.finishCompilation(section).valid());
        assertFalse(handoff.customVisible(chunk));
    }

    @Test
    void staleGenerationCannotPublishNewerReplacement() {
        WaterSurfaceHandoff handoff = new WaterSurfaceHandoff();
        long section = SectionPos.asLong(5, 3, 8);
        long chunk = ChunkPos.asLong(5, 8);
        handoff.beginSuppression(chunk, Map.of(section, mask(9)));
        WaterHandoffReceipt stale = compile(handoff, section, 9);

        handoff.beginSuppression(chunk, Map.of(section, mask(10)));

        assertFalse(handoff.acknowledgeUpload(stale));
        assertFalse(handoff.customVisible(chunk));
        handoff.beginCompilation(section);
        assertFalse(handoff.shouldSuppressTop(section, 9));
        assertTrue(handoff.shouldSuppressTop(section, 10));
    }

    private static WaterHandoffReceipt compile(
            WaterSurfaceHandoff handoff,
            long section,
            int... columns
    ) {
        handoff.beginCompilation(section);
        for (int column : columns) {
            assertTrue(handoff.shouldSuppressTop(section, column));
        }
        return handoff.finishCompilation(section);
    }

    private static WaterSurfaceHandoff.SectionMask mask(int... columns) {
        long[] masks = new long[4];
        for (int column : columns) {
            masks[column >>> 6] |= 1L << (column & 63);
        }
        return new WaterSurfaceHandoff.SectionMask(masks[0], masks[1], masks[2], masks[3]);
    }
}
