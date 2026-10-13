package com.thunder.wildernessodysseyapi.cinematic.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkipCinematicPayloadTest {
    @Test
    void preservesTheSequenceAndOriginalPlaybackBoundaryOnTheWire() {
        var payload = new SkipCinematicPayload(ResourceLocation.parse("test:intro"), 12_345L);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SkipCinematicPayload.STREAM_CODEC.encode(buffer, payload);
            var decoded = SkipCinematicPayload.STREAM_CODEC.decode(buffer);
            assertEquals(ResourceLocation.parse("test:intro"), decoded.sequenceId());
            assertEquals(12_345L, decoded.sequenceStartGameTime());
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsNegativePlaybackTimesFromTheWire() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeResourceLocation(ResourceLocation.parse("test:intro"));
            buffer.writeVarLong(-1L);
            assertThrows(DecoderException.class, () -> SkipCinematicPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
