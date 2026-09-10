package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.AtmosphericWaterExchange;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphericWaterExchangeTest {
    @Test
    void captureDoesNotConsumeAndAcknowledgementPreservesNewPublications() {
        AtmosphericWaterExchange.ReceiptBook book = new AtmosphericWaterExchange.ReceiptBook();
        long chunk = ChunkPos.asLong(0, 0);
        AtmosphereCellKey cell = new AtmosphereCellKey(0, 0);
        book.publish(chunk, 900L, 300L, 0L, 256.0, 20L);
        AtmosphericWaterExchange.Receipt first = book.capture(cell, 16);
        assertEquals(first, book.capture(cell, 16), "discarded work must not lose feedback");
        book.publish(chunk, 700L, 200L, 0L, 256.0, 40L);
        book.acknowledge(first);
        book.acknowledge(first);
        AtmosphericWaterExchange.Receipt remaining = book.capture(cell, 16);
        assertEquals(700L, remaining.precipitationMilliUnits());
        assertEquals(200L, remaining.evaporationMilliUnits());
        book.acknowledge(remaining);
        assertEquals(0L, book.capture(cell, 16).evaporationMilliUnits());
        assertEquals(1.0, book.capture(cell, 16).coveredFraction());
    }

    @Test
    void duplicateIntervalsAreNotCountedAndNegativeVolumeIsRejected() {
        AtmosphericWaterExchange.ReceiptBook book = new AtmosphericWaterExchange.ReceiptBook();
        long chunk = ChunkPos.asLong(0, 0);
        assertTrue(book.publish(chunk, 5L, 7L, 11L, 256.0, 20L));
        assertFalse(book.publish(chunk, 5L, 7L, 11L, 256.0, 20L));
        assertFalse(book.publish(chunk, 5L, 7L, 11L, 256.0, 19L));
        assertThrows(IllegalArgumentException.class,
                () -> book.publish(chunk, -1L, 0L, 0L, 256.0, 21L));
        assertEquals(7L, book.capture(new AtmosphereCellKey(0, 0), 16).evaporationMilliUnits());
    }

    @Test
    void negativeChunksAndChangedAtmosphereResolutionRetainExactReceipts() {
        AtmosphericWaterExchange.ReceiptBook book = new AtmosphericWaterExchange.ReceiptBook();
        book.publish(ChunkPos.asLong(-1, -1), 19L, 31L, 0L, 256.0, 20L);
        assertEquals(31L, book.capture(new AtmosphereCellKey(-1, -1), 16).evaporationMilliUnits());
        AtmosphericWaterExchange.Receipt larger = book.capture(new AtmosphereCellKey(-1, -1), 32);
        assertEquals(31L, larger.evaporationMilliUnits());
        assertEquals(0.25, larger.coveredFraction());
    }

    @Test
    void catchUpKeepsSnowProjectionWithoutReplayingFlux() {
        AtmosphericWaterExchange.ReceiptBook book = new AtmosphericWaterExchange.ReceiptBook();
        long snow = AtmosphericWaterExchange.MILLI_UNITS_PER_CUBIC_METRE * 256L / 20L;
        book.publish(ChunkPos.asLong(0, 0), 40L, 80L, snow, 256.0, 20L);
        AtmosphericWaterExchange.Receipt receipt = book.capture(new AtmosphereCellKey(0, 0), 16);
        assertEquals(1.0, receipt.snowCoverage(), 1.0E-9);
        assertEquals(0L, receipt.withoutFlux().evaporationMilliUnits());
        assertEquals(receipt.coveredFraction(), receipt.withoutFlux().coveredFraction());
        assertEquals(receipt.snowCoverage(), receipt.withoutFlux().snowCoverage());
    }
}
