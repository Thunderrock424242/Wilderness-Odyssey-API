package com.thunder.wildernessodysseyapi.weather.networking;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationIntensity;
import com.thunder.wildernessodysseyapi.weather.api.WindSettings;
import com.thunder.wildernessodysseyapi.weather.integration.LocalizedPrecipitationPolicy;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the bounded and quantized regional weather wire format. */
class WeatherRegionSyncPayloadTest {

    private static final ResourceLocation OVERWORLD = ResourceLocation.fromNamespaceAndPath(
            "minecraft",
            "overworld"
    );

    @Test
    void codecRoundTripPreservesHeaderAndQuantizedCellState() {
        WindSettings windSettings = new WindSettings(true, 3.5F, 4.0F, 6.5F, 2.1F, 30.0F);
        var cell = new WeatherRegionSyncPayload.CellSnapshot(
                -12,
                8,
                42L,
                -17.25f,
                0.73f,
                0.94f,
                -0.35f,
                0.82f,
                0.64f,
                0.51f,
                0.89f,
                0.77f,
                PrecipitationType.SNOW,
                0.46f,
                0.81f,
                -0.18f,
                0.91f,
                0.82f,
                0.44f,
                0.67f,
                0.31f
        );
        var original = new WeatherRegionSyncPayload(
                OVERWORLD,
                WeatherRegionSyncPayload.DATA_VERSION,
                19L,
                true,
                true,
                256,
                -10,
                7,
                windSettings,
                List.of(cell)
        );

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            WeatherRegionSyncPayload.STREAM_CODEC.encode(buffer, original);
            WeatherRegionSyncPayload decoded = WeatherRegionSyncPayload.STREAM_CODEC.decode(buffer);
            WeatherRegionSyncPayload.CellSnapshot decodedCell = decoded.cells().getFirst();

            assertEquals(OVERWORLD, decoded.dimension());
            assertEquals(WeatherRegionSyncPayload.DATA_VERSION, decoded.dataVersion());
            assertEquals(19L, decoded.sequence());
            assertEquals(true, decoded.enabled());
            assertEquals(true, decoded.replaceRegion());
            assertEquals(256, decoded.cellSize());
            assertEquals(-10, decoded.centerCellX());
            assertEquals(7, decoded.centerCellZ());
            assertEquals(windSettings, decoded.windSettings());
            assertEquals(-12, decodedCell.cellX());
            assertEquals(8, decodedCell.cellZ());
            assertEquals(42L, decodedCell.revision());
            assertEquals(-17.25f, decodedCell.temperature(), 0.003f);
            assertEquals(0.73f, decodedCell.humidity(), 1.0f / 255.0f);
            assertEquals(0.94f, decodedCell.pressure(), 1.0f / 65_535.0f);
            assertEquals(-0.35f, decodedCell.windX(), 1.0f / 32_767.0f);
            assertEquals(0.82f, decodedCell.windZ(), 1.0f / 32_767.0f);
            assertEquals(0.64f, decodedCell.cloudWater(), 1.0f / 255.0f);
            assertEquals(0.51f, decodedCell.instability(), 1.0f / 255.0f);
            assertEquals(0.89f, decodedCell.stormEnergy(), 1.0f / 255.0f);
            assertEquals(0.77f, decodedCell.precipitationIntensity(), 1.0f / 63.0f);
            assertEquals(PrecipitationType.SNOW, decodedCell.precipitationType());
            assertEquals(0.46f, decodedCell.verticalMotion(), 1.0f / 32_767.0f);
            assertEquals(0.81f, decodedCell.cloudDepth(), 1.0f / 255.0f);
            assertEquals(-0.18f, decodedCell.cloudWindX(), 1.0f / 32_767.0f);
            assertEquals(0.91f, decodedCell.cloudWindZ(), 1.0f / 32_767.0f);
            assertEquals(0.82f, decodedCell.surfaceWetness(), 1.0f / 255.0f);
            assertEquals(0.44f, decodedCell.puddleCoverage(), 1.0f / 255.0f);
            assertEquals(0.67f, decodedCell.snowpack(), 1.0f / 255.0f);
            assertEquals(0.31f, decodedCell.frozenFraction(), 1.0f / 255.0f);
        } finally {
            buffer.release();
        }
    }

    @Test
    void functionalRainBoundaryMatchesSixBitWireRounding() {
        var wet = precipitationCell(0, 0.025F);
        var dry = precipitationCell(1, 0.020F);
        var payload = new WeatherRegionSyncPayload(
                OVERWORLD,
                WeatherRegionSyncPayload.DATA_VERSION,
                20L,
                true,
                true,
                256,
                0,
                0,
                List.of(wet, dry)
        );

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            assertTrue(LocalizedPrecipitationPolicy.hasPrecipitation(wet.sample()));
            assertFalse(LocalizedPrecipitationPolicy.hasPrecipitation(dry.sample()));

            WeatherRegionSyncPayload.STREAM_CODEC.encode(buffer, payload);
            WeatherRegionSyncPayload decoded = WeatherRegionSyncPayload.STREAM_CODEC.decode(buffer);

            assertTrue(LocalizedPrecipitationPolicy.hasPrecipitation(decoded.cells().get(0).sample()));
            assertFalse(LocalizedPrecipitationPolicy.hasPrecipitation(decoded.cells().get(1).sample()));
            assertEquals(2.0F / 63.0F, decoded.cells().get(0).precipitationIntensity(), 1.0E-7F);
            assertEquals(1.0F / 63.0F, decoded.cells().get(1).precipitationIntensity(), 1.0E-7F);
        } finally {
            buffer.release();
        }
    }

    @Test
    void endpointQuantizationPrecedesClientSpatialInterpolation() {
        var payload = new WeatherRegionSyncPayload(
                OVERWORLD,
                WeatherRegionSyncPayload.DATA_VERSION,
                21L,
                true,
                true,
                256,
                0,
                0,
                List.of(precipitationCell(0, 0.0F), precipitationCell(1, 0.047F))
        );

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            WeatherRegionSyncPayload.STREAM_CODEC.encode(buffer, payload);
            WeatherRegionSyncPayload decoded = WeatherRegionSyncPayload.STREAM_CODEC.decode(buffer);
            double decodedMidpoint = (
                    decoded.cells().get(0).precipitationIntensity()
                            + decoded.cells().get(1).precipitationIntensity()
            ) * 0.5;

            assertFalse(PrecipitationIntensity.isFunctional(0.047 * 0.5));
            assertTrue(PrecipitationIntensity.isFunctional(decodedMidpoint));
        } finally {
            buffer.release();
        }
    }

    @Test
    void zeroIntensityWireBucketClearsItsPrecipitationType() {
        var traceSnow = new WeatherRegionSyncPayload.CellSnapshot(
                0,
                0,
                0L,
                -5.0F,
                0.9F,
                1.0F,
                0.0F,
                0.0F,
                0.8F,
                0.5F,
                0.5F,
                0.001F,
                PrecipitationType.SNOW
        );
        var payload = new WeatherRegionSyncPayload(
                OVERWORLD,
                WeatherRegionSyncPayload.DATA_VERSION,
                22L,
                true,
                true,
                256,
                0,
                0,
                List.of(traceSnow)
        );

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            WeatherRegionSyncPayload.STREAM_CODEC.encode(buffer, payload);
            WeatherRegionSyncPayload decoded = WeatherRegionSyncPayload.STREAM_CODEC.decode(buffer);

            assertEquals(0.0F, decoded.cells().getFirst().precipitationIntensity());
            assertEquals(PrecipitationType.NONE, decoded.cells().getFirst().precipitationType());
        } finally {
            buffer.release();
        }
    }

    @Test
    void constructorDefensivelyCopiesCells() {
        List<WeatherRegionSyncPayload.CellSnapshot> mutableCells = new ArrayList<>();
        mutableCells.add(clearCell(2, 3));
        WeatherRegionSyncPayload payload = new WeatherRegionSyncPayload(
                OVERWORLD,
                WeatherRegionSyncPayload.DATA_VERSION,
                1L,
                true,
                true,
                256,
                2,
                3,
                mutableCells
        );

        mutableCells.clear();

        assertEquals(1, payload.cells().size());
        assertThrows(UnsupportedOperationException.class, () -> payload.cells().clear());
    }

    @Test
    void codecRejectsExcessCellCountBeforeReadingCellData() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeResourceLocation(OVERWORLD);
            buffer.writeVarInt(WeatherRegionSyncPayload.DATA_VERSION);
            buffer.writeVarLong(1L);
            buffer.writeByte(1); // enabled, delta payload
            buffer.writeVarInt(256);
            buffer.writeVarInt(0); // zig-zag encoded center X
            buffer.writeVarInt(0); // zig-zag encoded center Z
            buffer.writeVarInt(WeatherRegionSyncPayload.MAX_CELLS + 1);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> WeatherRegionSyncPayload.STREAM_CODEC.decode(buffer)
            );
        } finally {
            buffer.release();
        }
    }

    @Test
    void codecRoundTripPreservesHailType() {
        var payload = new WeatherRegionSyncPayload(
                OVERWORLD,
                WeatherRegionSyncPayload.DATA_VERSION,
                2L,
                true,
                false,
                256,
                0,
                0,
                List.of(new WeatherRegionSyncPayload.CellSnapshot(
                        0, 0, 0L, 8.0F, 0.9F, 0.96F,
                        0.2F, 0.1F, 0.9F, 0.8F, 0.9F, 0.75F,
                        PrecipitationType.HAIL
                ))
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            WeatherRegionSyncPayload.STREAM_CODEC.encode(buffer, payload);
            WeatherRegionSyncPayload decoded = WeatherRegionSyncPayload.STREAM_CODEC.decode(buffer);
            assertEquals(PrecipitationType.HAIL, decoded.cells().getFirst().precipitationType());
        } finally {
            buffer.release();
        }
    }

    @Test
    void disabledPayloadMustBeAnEmptyReplacement() {
        assertThrows(IllegalArgumentException.class, () -> new WeatherRegionSyncPayload(
                OVERWORLD,
                WeatherRegionSyncPayload.DATA_VERSION,
                3L,
                false,
                false,
                256,
                0,
                0,
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new WeatherRegionSyncPayload(
                OVERWORLD,
                WeatherRegionSyncPayload.DATA_VERSION,
                4L,
                false,
                true,
                256,
                0,
                0,
                List.of(clearCell(0, 0))
        ));
    }

    private static WeatherRegionSyncPayload.CellSnapshot clearCell(int cellX, int cellZ) {
        return new WeatherRegionSyncPayload.CellSnapshot(
                cellX,
                cellZ,
                0L,
                15.0f,
                0.4f,
                1.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                PrecipitationType.NONE
        );
    }

    private static WeatherRegionSyncPayload.CellSnapshot precipitationCell(
            int cellX,
            float intensity
    ) {
        return new WeatherRegionSyncPayload.CellSnapshot(
                cellX,
                0,
                0L,
                15.0F,
                0.9F,
                1.0F,
                0.0F,
                0.0F,
                0.8F,
                0.5F,
                0.5F,
                intensity,
                PrecipitationType.RAIN
        );
    }
}
