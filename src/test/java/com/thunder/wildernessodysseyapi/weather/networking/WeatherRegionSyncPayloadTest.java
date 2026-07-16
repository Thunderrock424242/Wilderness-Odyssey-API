package com.thunder.wildernessodysseyapi.weather.networking;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies the bounded and quantized regional weather wire format. */
class WeatherRegionSyncPayloadTest {

    private static final ResourceLocation OVERWORLD = ResourceLocation.fromNamespaceAndPath(
            "minecraft",
            "overworld"
    );

    @Test
    void codecRoundTripPreservesHeaderAndQuantizedCellState() {
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
                PrecipitationType.SNOW
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
    void codecRejectsUnknownPrecipitationType() {
        var payload = new WeatherRegionSyncPayload(
                OVERWORLD,
                WeatherRegionSyncPayload.DATA_VERSION,
                2L,
                true,
                false,
                256,
                0,
                0,
                List.of(clearCell(0, 0))
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            WeatherRegionSyncPayload.STREAM_CODEC.encode(buffer, payload);
            buffer.setByte(buffer.writerIndex() - 1, 0b1100_0000);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> WeatherRegionSyncPayload.STREAM_CODEC.decode(buffer)
            );
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
}
