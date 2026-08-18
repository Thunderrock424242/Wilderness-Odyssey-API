package com.thunder.wildernessodysseyapi.weather.networking;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemStage;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies bounds and quantization for the low-frequency storm-audio snapshot. */
class DistantThunderSystemSyncPayloadTest {

    private static final ResourceLocation OVERWORLD = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "overworld"
    );

    @Test
    void codecRoundTripPreservesStormIdentityMotionAndConvectiveState() {
        var storm = new DistantThunderSystemSyncPayload.StormSnapshot(
                42L,
                WeatherSystemType.TORNADO,
                WeatherSystemStage.MATURE,
                -4_125.75,
                8_432.5,
                480.0,
                0.87,
                -0.72,
                0.31,
                0.83,
                PrecipitationType.HAIL,
                0.76,
                0.91,
                0.84,
                0.72
        );
        var original = new DistantThunderSystemSyncPayload(
                OVERWORLD,
                DistantThunderSystemSyncPayload.DATA_VERSION,
                9L,
                true,
                List.of(storm)
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            DistantThunderSystemSyncPayload.STREAM_CODEC.encode(buffer, original);
            DistantThunderSystemSyncPayload decoded =
                    DistantThunderSystemSyncPayload.STREAM_CODEC.decode(buffer);
            var decodedStorm = decoded.storms().getFirst();

            assertEquals(OVERWORLD, decoded.dimension());
            assertEquals(9L, decoded.sequence());
            assertTrue(decoded.enabled());
            assertEquals(42L, decodedStorm.id());
            assertEquals(WeatherSystemType.TORNADO, decodedStorm.type());
            assertEquals(WeatherSystemStage.MATURE, decodedStorm.stage());
            assertEquals(-4_125.75, decodedStorm.centerX());
            assertEquals(8_432.5, decodedStorm.centerZ());
            assertEquals(480.0, decodedStorm.radiusBlocks(), 0.001);
            assertEquals(-0.72, decodedStorm.motionX(), 1.0 / 32_767.0);
            assertEquals(0.31, decodedStorm.motionZ(), 1.0 / 32_767.0);
            assertEquals(PrecipitationType.HAIL, decodedStorm.precipitationType());
            assertEquals(0.91, decodedStorm.stormEnergy(), 1.0 / 255.0);
            assertEquals(0.84, decodedStorm.instability(), 1.0 / 255.0);
            assertEquals(0.72, decodedStorm.thunderPotential(), 1.0 / 255.0);
        } finally {
            buffer.release();
        }
    }

    @Test
    void atmosphericFrontCannotEnterThunderPayload() {
        assertThrows(IllegalArgumentException.class, () -> new DistantThunderSystemSyncPayload.StormSnapshot(
                1L,
                WeatherSystemType.COLD_FRONT,
                WeatherSystemStage.MATURE,
                0.0, 0.0, 300.0, 0.9, -1.0, 0.0, 0.9,
                PrecipitationType.RAIN, 0.9, 0.9, 0.9, 0.9
        ));
    }

    @Test
    void disabledPayloadMustBeEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new DistantThunderSystemSyncPayload(
                OVERWORLD,
                DistantThunderSystemSyncPayload.DATA_VERSION,
                1L,
                false,
                List.of(new DistantThunderSystemSyncPayload.StormSnapshot(
                        2L,
                        WeatherSystemType.STORM,
                        WeatherSystemStage.MATURE,
                        0.0, 0.0, 300.0, 0.9, -1.0, 0.0, 0.9,
                        PrecipitationType.RAIN, 0.9, 0.9, 0.9, 0.9
                ))
        ));
    }

    @Test
    void codecRejectsOversizedCountBeforeAllocatingEntries() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeResourceLocation(OVERWORLD);
            buffer.writeVarInt(DistantThunderSystemSyncPayload.DATA_VERSION);
            buffer.writeVarLong(1L);
            buffer.writeBoolean(true);
            buffer.writeVarInt(DistantThunderSystemSyncPayload.MAX_STORMS + 1);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> DistantThunderSystemSyncPayload.STREAM_CODEC.decode(buffer)
            );
        } finally {
            buffer.release();
        }
    }
}
