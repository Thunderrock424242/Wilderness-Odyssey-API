package com.thunder.wildernessodysseyapi.dataengine.network;

import com.thunder.wildernessodysseyapi.dataengine.queue.UpdatePriority;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataPacketBatchTest {
    @Test
    void compactCodecRoundTripsMultipleDeltas() {
        DataDelta first = new DataDelta(
                ResourceLocation.fromNamespaceAndPath("test", "weather"),
                14L,
                1L,
                UpdatePriority.HIGH,
                new byte[]{3, 5, 8}
        );
        DataDelta second = new DataDelta(
                ResourceLocation.fromNamespaceAndPath("test", "water"),
                22L,
                6L,
                UpdatePriority.NORMAL,
                new byte[]{13, 21}
        );
        DataPacketBatch original = new DataPacketBatch(List.of(first, second));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            DataPacketBatch.STREAM_CODEC.encode(buffer, original);
            DataPacketBatch decoded = DataPacketBatch.STREAM_CODEC.decode(buffer);

            assertEquals(2, decoded.entries().size());
            assertEquals(first.systemId(), decoded.entries().get(0).systemId());
            assertEquals(first.changedFields(), decoded.entries().get(0).changedFields());
            assertArrayEquals(first.body(), decoded.entries().get(0).body());
            assertEquals(second.priority(), decoded.entries().get(1).priority());
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsEmptyOrOversizedBodies() {
        assertThrows(IllegalArgumentException.class, () -> new DataPacketBatch(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DataDelta(
                ResourceLocation.fromNamespaceAndPath("test", "large"),
                0L,
                1L,
                UpdatePriority.NORMAL,
                new byte[DataDelta.MAXIMUM_BODY_BYTES + 1]
        ));
    }
}
