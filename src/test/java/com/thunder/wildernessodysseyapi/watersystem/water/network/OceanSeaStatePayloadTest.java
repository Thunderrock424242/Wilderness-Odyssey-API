package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the strict regional sea-state wire format. */
class OceanSeaStatePayloadTest {

    @Test
    void codecRoundTripPreservesRegionalCells() {
        OceanSeaState.Sample sample = new OceanSeaState.Sample(
                0.82f, -0.6f, 0.8f, 17.0f, 1.6f, 2.1f, 0.72f, 0.88f
        );
        OceanSeaStatePayload original = new OceanSeaStatePayload(
                true,
                128,
                List.of(
                        new OceanSeaStatePayload.CellSnapshot(-3, 7, sample),
                        new OceanSeaStatePayload.CellSnapshot(-2, 7, OceanSeaState.CALM)
                )
        );

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            OceanSeaStatePayload.STREAM_CODEC.encode(buffer, original);
            OceanSeaStatePayload decoded = OceanSeaStatePayload.STREAM_CODEC.decode(buffer);

            assertTrue(decoded.localized());
            assertEquals(128, decoded.cellSize());
            assertEquals(2, decoded.cells().size());
            assertEquals(-3, decoded.cells().getFirst().cellX());
            assertEquals(7, decoded.cells().getFirst().cellZ());
            assertEquals(sample.strength(), decoded.cells().getFirst().sample().strength());
            assertEquals(sample.windDirectionX(), decoded.cells().getFirst().sample().windDirectionX());
            assertEquals(sample.windDirectionZ(), decoded.cells().getFirst().sample().windDirectionZ());
        } finally {
            buffer.release();
        }
    }

    @Test
    void disabledPayloadCannotRetainRegionalState() {
        OceanSeaStatePayload payload = new OceanSeaStatePayload(
                false,
                128,
                List.of(new OceanSeaStatePayload.CellSnapshot(0, 0, OceanSeaState.CALM))
        );

        assertFalse(payload.localized());
        assertTrue(payload.cells().isEmpty());
    }

    @Test
    void constructorAndCodecRejectExcessCells() {
        List<OceanSeaStatePayload.CellSnapshot> cells = new ArrayList<>();
        for (int index = 0; index <= OceanSeaStatePayload.MAX_CELLS; index++) {
            cells.add(new OceanSeaStatePayload.CellSnapshot(index, 0, OceanSeaState.CALM));
        }
        assertThrows(IllegalArgumentException.class, () ->
                new OceanSeaStatePayload(true, 128, cells));

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeBoolean(true);
            buffer.writeVarInt(128);
            buffer.writeVarInt(OceanSeaStatePayload.MAX_CELLS + 1);
            assertThrows(IllegalArgumentException.class, () ->
                    OceanSeaStatePayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void constructorAndCodecRejectInvalidCellSizes() {
        assertThrows(IllegalArgumentException.class, () ->
                new OceanSeaStatePayload(true, 63, List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new OceanSeaStatePayload(true, 513, List.of()));

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeBoolean(true);
            buffer.writeVarInt(0);
            buffer.writeVarInt(0);
            assertThrows(IllegalArgumentException.class, () ->
                    OceanSeaStatePayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
