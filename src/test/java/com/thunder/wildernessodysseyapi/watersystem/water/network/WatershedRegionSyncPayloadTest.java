package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.DrainageDirection;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.WaterFeature;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WatershedChunkState;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the strict compact watershed wire format and decode bounds. */
class WatershedRegionSyncPayloadTest {

    @Test
    void codecRoundTripPreservesPackedChunkConditions() {
        WatershedChunkState state = WatershedChunkState.create(
                991L,
                72,
                DrainageDirection.SOUTH,
                WaterFeature.RIVER,
                0.74f,
                123456L,
                0.88f,
                20L
        );
        WatershedRegionSyncPayload original = new WatershedRegionSyncPayload(
                true,
                List.of(new WatershedRegionSyncPayload.ChunkSnapshot(-3, 8, state.packed()))
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            WatershedRegionSyncPayload.STREAM_CODEC.encode(buffer, original);
            WatershedRegionSyncPayload decoded = WatershedRegionSyncPayload.STREAM_CODEC.decode(buffer);

            assertTrue(decoded.enabled());
            assertEquals(1, decoded.chunks().size());
            assertEquals(-3, decoded.chunks().getFirst().chunkX());
            assertEquals(8, decoded.chunks().getFirst().chunkZ());
            assertEquals(state.conditions(), WatershedChunkState.fromPacked(
                    decoded.chunks().getFirst().packed()).conditions());
            assertEquals(
                    state.packed().drainageDirectionBits(),
                    decoded.chunks().getFirst().packed().drainageDirectionBits()
            );
        } finally {
            buffer.release();
        }
    }

    @Test
    void disabledPayloadDropsCellsAndExcessDecodeIsRejected() {
        WatershedChunkState state = WatershedChunkState.create(
                1L, 64, DrainageDirection.SINK, WaterFeature.NONE,
                0.0f, WatershedChunkState.NO_REPRESENTATIVE, 0.88f, 0L
        );
        WatershedRegionSyncPayload disabled = new WatershedRegionSyncPayload(
                false,
                List.of(new WatershedRegionSyncPayload.ChunkSnapshot(0, 0, state.packed()))
        );
        assertFalse(disabled.enabled());
        assertTrue(disabled.chunks().isEmpty());

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeBoolean(true);
            buffer.writeVarInt(WatershedRegionSyncPayload.MAX_CHUNKS + 1);
            assertThrows(IllegalArgumentException.class, () ->
                    WatershedRegionSyncPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void constructorRejectsUnboundedWindows() {
        WatershedChunkState state = WatershedChunkState.create(
                1L, 64, DrainageDirection.SINK, WaterFeature.NONE,
                0.0f, WatershedChunkState.NO_REPRESENTATIVE, 0.88f, 0L
        );
        List<WatershedRegionSyncPayload.ChunkSnapshot> chunks = new ArrayList<>();
        for (int index = 0; index <= WatershedRegionSyncPayload.MAX_CHUNKS; index++) {
            chunks.add(new WatershedRegionSyncPayload.ChunkSnapshot(index, 0, state.packed()));
        }
        assertThrows(IllegalArgumentException.class, () ->
                new WatershedRegionSyncPayload(true, chunks));
    }
}
