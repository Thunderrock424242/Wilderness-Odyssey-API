package com.thunder.wildernessodysseyapi.cinematic.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CinematicNarrationPayloadTest {
    private static final ResourceLocation SEQUENCE = ResourceLocation.parse("test:sequence");
    private static final ResourceLocation CUE = ResourceLocation.parse("test:cue");

    @Test
    void retainsTheAuthoritativeCueBoundary() {
        var payload = new CinematicNarrationPayload(SEQUENCE, CUE, 12_345L, 86);

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            CinematicNarrationPayload.STREAM_CODEC.encode(buffer, payload);
            assertEquals(payload, CinematicNarrationPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsInvalidCueTiming() {
        assertThrows(DecoderException.class, () -> new CinematicNarrationPayload(SEQUENCE, CUE, -1L, 86));
        assertThrows(DecoderException.class, () -> new CinematicNarrationPayload(SEQUENCE, CUE, 1L, 0));
        assertThrows(DecoderException.class, () -> new CinematicNarrationPayload(SEQUENCE, CUE, 1L, 1_201));
    }
}
