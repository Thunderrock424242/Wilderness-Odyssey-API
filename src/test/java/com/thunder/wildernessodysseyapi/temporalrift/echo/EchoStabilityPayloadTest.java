package com.thunder.wildernessodysseyapi.temporalrift.echo;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EchoStabilityPayloadTest {
    @Test
    void compactSummaryRoundTripsWithoutFractureCoordinatesOrSeed() {
        var payload = new EchoStabilityPayload(ResourceLocation.parse("wildernessodysseyapi:the_echo"),
                1000L, -25L, 77, 77, true, false);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            EchoStabilityPayload.STREAM_CODEC.encode(buffer, payload);
            assertTrue(buffer.readableBytes() < 80);
            assertEquals(payload, EchoStabilityPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void outOfRangeAmountsAreClamped() {
        var payload = new EchoStabilityPayload(ResourceLocation.parse("wildernessodysseyapi:the_echo"), 0, 0,
                Integer.MAX_VALUE, -1, false, false);
        assertEquals(100, payload.intensity());
        assertEquals(0, payload.riftfallIntensity());
    }
}
