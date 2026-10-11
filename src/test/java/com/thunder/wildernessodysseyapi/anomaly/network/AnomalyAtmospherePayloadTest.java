package com.thunder.wildernessodysseyapi.anomaly.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnomalyAtmospherePayloadTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void roundTripsBoundedLocalConclusions() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AnomalyAtmospherePayload original = new AnomalyAtmospherePayload(DIMENSION, 4, 72, 31);
            AnomalyAtmospherePayload.STREAM_CODEC.encode(buffer, original);
            assertEquals(original, AnomalyAtmospherePayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void acceptsAnExplicitNoZoneReset() {
        assertEquals(-1, new AnomalyAtmospherePayload(DIMENSION, -1, 0, 0).zone());
    }

    @Test
    void rejectsValuesOutsideTheWireContract() {
        assertThrows(IllegalArgumentException.class, () -> new AnomalyAtmospherePayload(DIMENSION, -2, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new AnomalyAtmospherePayload(DIMENSION, 5, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new AnomalyAtmospherePayload(DIMENSION, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new AnomalyAtmospherePayload(DIMENSION, 0, 101, 0));
        assertThrows(IllegalArgumentException.class, () -> new AnomalyAtmospherePayload(DIMENSION, 0, 0, 101));
        assertThrows(NullPointerException.class, () -> new AnomalyAtmospherePayload(null, 0, 0, 0));
    }

    @Test
    void rejectsMalformedDecodedStrength() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeResourceLocation(DIMENSION);
            buffer.writeVarInt(4);
            buffer.writeVarInt(100);
            buffer.writeVarInt(1_000);
            assertThrows(IllegalArgumentException.class, () -> AnomalyAtmospherePayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
