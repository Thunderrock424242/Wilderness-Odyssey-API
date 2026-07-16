package com.thunder.wildernessodysseyapi.weather.networking;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereView;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Sends a bounded region of server-owned atmospheric cells to one client.
 *
 * <p>Full payloads replace the client's current region, while delta payloads
 * merge only cells whose revisions changed. The wire format uses relative cell
 * coordinates and fixed-point atmospheric values so routine weather updates do
 * not send doubles or the dimension's complete grid.</p>
 *
 * @param dimension authoritative level identifier
 * @param dataVersion atmospheric snapshot schema version
 * @param sequence per-player monotonic server sequence
 * @param enabled whether localized weather is enabled in this dimension
 * @param replaceRegion whether the client must discard its previous region first
 * @param cellSize atmospheric cell width in blocks
 * @param centerCellX center cell X for this region
 * @param centerCellZ center cell Z for this region
 * @param cells immutable full-region or changed-cell snapshots
 */
public record WeatherRegionSyncPayload(
        ResourceLocation dimension,
        int dataVersion,
        long sequence,
        boolean enabled,
        boolean replaceRegion,
        int cellSize,
        int centerCellX,
        int centerCellZ,
        List<CellSnapshot> cells
) implements CustomPacketPayload {

    /** Current atmospheric snapshot schema understood by server and client. */
    public static final int DATA_VERSION = 1;
    /** Descriptive alias used by payload construction and validation code. */
    public static final int CURRENT_DATA_VERSION = DATA_VERSION;
    /** Hard cap for a 17 by 17 region around one player. */
    public static final int MAX_CELLS = 289;
    /** Largest supported distance from the payload's center cell. */
    public static final int MAX_CELL_OFFSET = 8;

    private static final int MIN_CELL_SIZE = 16;
    private static final int MAX_CELL_SIZE = 4_096;
    private static final int FLAG_ENABLED = 1;
    private static final int FLAG_REPLACE_REGION = 1 << 1;
    private static final int KNOWN_FLAGS = FLAG_ENABLED | FLAG_REPLACE_REGION;

    private static final float MIN_TEMPERATURE = -80.0f;
    private static final float MAX_TEMPERATURE = 60.0f;
    private static final float MIN_PRESSURE = 0.5f;
    private static final float MAX_PRESSURE = 1.5f;
    private static final int UNSIGNED_SHORT_MAX = 65_535;
    private static final int UNSIGNED_BYTE_MAX = 255;
    private static final int SIGNED_UNIT_MAX = 32_767;
    private static final int PRECIPITATION_INTENSITY_MAX = 63;

    /** Payload identifier used by NeoForge's client-bound play protocol. */
    public static final Type<WeatherRegionSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "weather_region_sync")
    );
    /** Custom codec that validates all variable-size data before allocation. */
    public static final StreamCodec<FriendlyByteBuf, WeatherRegionSyncPayload> STREAM_CODEC =
            StreamCodec.of(WeatherRegionSyncPayload::encode, WeatherRegionSyncPayload::decode);

    public WeatherRegionSyncPayload {
        dimension = Objects.requireNonNull(dimension, "dimension");
        cells = List.copyOf(Objects.requireNonNull(cells, "cells"));
        validateHeader(dataVersion, sequence, cellSize);
        if (cells.size() > MAX_CELLS) {
            throw new IllegalArgumentException("Weather region exceeds the network cell limit: " + cells.size());
        }
        if (!enabled && (!replaceRegion || !cells.isEmpty())) {
            throw new IllegalArgumentException("A disabled weather payload must be an empty region reset");
        }

        Set<Long> uniqueCells = new HashSet<>(cells.size());
        for (CellSnapshot cell : cells) {
            long relativeX = (long) cell.cellX() - centerCellX;
            long relativeZ = (long) cell.cellZ() - centerCellZ;
            if (Math.abs(relativeX) > MAX_CELL_OFFSET || Math.abs(relativeZ) > MAX_CELL_OFFSET) {
                throw new IllegalArgumentException("Weather cell lies outside the bounded payload region");
            }
            if (!uniqueCells.add(packCell(cell.cellX(), cell.cellZ()))) {
                throw new IllegalArgumentException("Weather region contains duplicate cell coordinates");
            }
        }
    }

    /** Creates the explicit reset sent when localized weather is disabled. */
    public static WeatherRegionSyncPayload disabled(
            ResourceLocation dimension,
            long sequence,
            int cellSize,
            int centerCellX,
            int centerCellZ
    ) {
        return new WeatherRegionSyncPayload(
                dimension,
                CURRENT_DATA_VERSION,
                sequence,
                false,
                true,
                cellSize,
                centerCellX,
                centerCellZ,
                List.of()
        );
    }

    private static void encode(FriendlyByteBuf buffer, WeatherRegionSyncPayload payload) {
        buffer.writeResourceLocation(payload.dimension);
        buffer.writeVarInt(payload.dataVersion);
        buffer.writeVarLong(payload.sequence);
        int flags = (payload.enabled ? FLAG_ENABLED : 0)
                | (payload.replaceRegion ? FLAG_REPLACE_REGION : 0);
        buffer.writeByte(flags);
        buffer.writeVarInt(payload.cellSize);
        writeSignedVarInt(buffer, payload.centerCellX);
        writeSignedVarInt(buffer, payload.centerCellZ);
        buffer.writeVarInt(payload.cells.size());

        for (CellSnapshot cell : payload.cells) {
            buffer.writeByte(cell.cellX - payload.centerCellX);
            buffer.writeByte(cell.cellZ - payload.centerCellZ);
            buffer.writeVarLong(cell.revision);
            buffer.writeShort(quantizeRange(
                    cell.temperature,
                    MIN_TEMPERATURE,
                    MAX_TEMPERATURE,
                    UNSIGNED_SHORT_MAX
            ));
            buffer.writeByte(quantizeUnit(cell.humidity, UNSIGNED_BYTE_MAX));
            buffer.writeShort(quantizeRange(
                    cell.pressure,
                    MIN_PRESSURE,
                    MAX_PRESSURE,
                    UNSIGNED_SHORT_MAX
            ));
            buffer.writeShort(quantizeSignedUnit(cell.windX));
            buffer.writeShort(quantizeSignedUnit(cell.windZ));
            buffer.writeByte(quantizeUnit(cell.cloudWater, UNSIGNED_BYTE_MAX));
            buffer.writeByte(quantizeUnit(cell.instability, UNSIGNED_BYTE_MAX));
            buffer.writeByte(quantizeUnit(cell.stormEnergy, UNSIGNED_BYTE_MAX));

            // Precipitation type needs only two bits, leaving the remaining six
            // for intensity without another byte per cell.
            int precipitation = precipitationTypeId(cell.precipitationType) << 6;
            precipitation |= quantizeUnit(cell.precipitationIntensity, PRECIPITATION_INTENSITY_MAX);
            buffer.writeByte(precipitation);
        }
    }

    private static WeatherRegionSyncPayload decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        int dataVersion = buffer.readVarInt();
        long sequence = buffer.readVarLong();
        int flags = buffer.readUnsignedByte();
        if ((flags & ~KNOWN_FLAGS) != 0) {
            throw new IllegalArgumentException("Weather payload contains unknown flags: " + flags);
        }
        boolean enabled = (flags & FLAG_ENABLED) != 0;
        boolean replaceRegion = (flags & FLAG_REPLACE_REGION) != 0;
        int cellSize = buffer.readVarInt();
        validateHeader(dataVersion, sequence, cellSize);
        int centerCellX = readSignedVarInt(buffer);
        int centerCellZ = readSignedVarInt(buffer);
        int cellCount = buffer.readVarInt();

        // Validate the untrusted count before constructing either the result
        // list or a duplicate-detection set.
        if (cellCount < 0 || cellCount > MAX_CELLS) {
            throw new IllegalArgumentException("Invalid weather region cell count: " + cellCount);
        }
        if (!enabled && (!replaceRegion || cellCount != 0)) {
            throw new IllegalArgumentException("A disabled weather payload must be an empty region reset");
        }

        List<CellSnapshot> cells = new ArrayList<>(cellCount);
        Set<Long> uniqueCells = new HashSet<>(cellCount);
        for (int index = 0; index < cellCount; index++) {
            int cellX = addRelativeCell(centerCellX, buffer.readByte());
            int cellZ = addRelativeCell(centerCellZ, buffer.readByte());
            long revision = buffer.readVarLong();
            if (revision < 0L) {
                throw new IllegalArgumentException("Weather cell revision cannot be negative");
            }
            if (!uniqueCells.add(packCell(cellX, cellZ))) {
                throw new IllegalArgumentException("Weather region contains duplicate cell coordinates");
            }

            float temperature = dequantizeRange(
                    buffer.readUnsignedShort(),
                    MIN_TEMPERATURE,
                    MAX_TEMPERATURE,
                    UNSIGNED_SHORT_MAX
            );
            float humidity = dequantizeUnit(buffer.readUnsignedByte(), UNSIGNED_BYTE_MAX);
            float pressure = dequantizeRange(
                    buffer.readUnsignedShort(),
                    MIN_PRESSURE,
                    MAX_PRESSURE,
                    UNSIGNED_SHORT_MAX
            );
            float windX = dequantizeSignedUnit(buffer.readShort());
            float windZ = dequantizeSignedUnit(buffer.readShort());
            float cloudWater = dequantizeUnit(buffer.readUnsignedByte(), UNSIGNED_BYTE_MAX);
            float instability = dequantizeUnit(buffer.readUnsignedByte(), UNSIGNED_BYTE_MAX);
            float stormEnergy = dequantizeUnit(buffer.readUnsignedByte(), UNSIGNED_BYTE_MAX);
            int precipitation = buffer.readUnsignedByte();
            PrecipitationType precipitationType = precipitationTypeFromId(precipitation >>> 6);
            float precipitationIntensity = dequantizeUnit(
                    precipitation & PRECIPITATION_INTENSITY_MAX,
                    PRECIPITATION_INTENSITY_MAX
            );
            cells.add(new CellSnapshot(
                    cellX,
                    cellZ,
                    revision,
                    temperature,
                    humidity,
                    pressure,
                    windX,
                    windZ,
                    cloudWater,
                    instability,
                    stormEnergy,
                    precipitationIntensity,
                    precipitationType
            ));
        }

        return new WeatherRegionSyncPayload(
                dimension,
                dataVersion,
                sequence,
                enabled,
                replaceRegion,
                cellSize,
                centerCellX,
                centerCellZ,
                cells
        );
    }

    private static void validateHeader(int dataVersion, long sequence, int cellSize) {
        if (dataVersion != CURRENT_DATA_VERSION) {
            throw new IllegalArgumentException("Unsupported weather snapshot data version: " + dataVersion);
        }
        if (sequence < 0L) {
            throw new IllegalArgumentException("Weather snapshot sequence cannot be negative");
        }
        if (cellSize < MIN_CELL_SIZE || cellSize > MAX_CELL_SIZE) {
            throw new IllegalArgumentException("Invalid atmospheric cell size: " + cellSize);
        }
    }

    private static int quantizeRange(float value, float minimum, float maximum, int quantizedMaximum) {
        float normalized = (clamp(value, minimum, maximum) - minimum) / (maximum - minimum);
        return Math.round(normalized * quantizedMaximum);
    }

    private static int quantizeUnit(float value, int quantizedMaximum) {
        return Math.round(clamp(value, 0.0f, 1.0f) * quantizedMaximum);
    }

    private static int quantizeSignedUnit(float value) {
        return Math.round(clamp(value, -1.0f, 1.0f) * SIGNED_UNIT_MAX);
    }

    private static float dequantizeRange(int value, float minimum, float maximum, int quantizedMaximum) {
        return minimum + (maximum - minimum) * value / quantizedMaximum;
    }

    private static float dequantizeUnit(int value, int quantizedMaximum) {
        return (float) value / quantizedMaximum;
    }

    private static float dequantizeSignedUnit(short value) {
        return clamp((float) value / SIGNED_UNIT_MAX, -1.0f, 1.0f);
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int precipitationTypeId(PrecipitationType type) {
        return switch (type) {
            case NONE -> 0;
            case RAIN -> 1;
            case SNOW -> 2;
        };
    }

    private static PrecipitationType precipitationTypeFromId(int typeId) {
        return switch (typeId) {
            case 0 -> PrecipitationType.NONE;
            case 1 -> PrecipitationType.RAIN;
            case 2 -> PrecipitationType.SNOW;
            default -> throw new IllegalArgumentException("Invalid precipitation type id: " + typeId);
        };
    }

    private static void writeSignedVarInt(FriendlyByteBuf buffer, int value) {
        buffer.writeVarInt((value << 1) ^ (value >> 31));
    }

    private static int readSignedVarInt(FriendlyByteBuf buffer) {
        int encoded = buffer.readVarInt();
        return (encoded >>> 1) ^ -(encoded & 1);
    }

    private static int addRelativeCell(int center, byte offset) {
        long result = (long) center + offset;
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Weather cell coordinate overflows the integer range");
        }
        return (int) result;
    }

    private static long packCell(int cellX, int cellZ) {
        return ((long) cellX & 0xFFFFFFFFL) | ((long) cellZ << 32);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Immutable, render-independent state for one authoritative atmosphere cell.
     *
     * <p>The record stores convenient dequantized values after receipt. The
     * codec clamps and quantizes every scalar on the wire; {@link #sample()}
     * reconstructs the shared read-only API model used by client consumers.</p>
     */
    public record CellSnapshot(
            int cellX,
            int cellZ,
            long revision,
            float temperature,
            float humidity,
            float pressure,
            float windX,
            float windZ,
            float cloudWater,
            float instability,
            float stormEnergy,
            float precipitationIntensity,
            PrecipitationType precipitationType
    ) {

        public CellSnapshot {
            if (revision < 0L) {
                throw new IllegalArgumentException("Weather cell revision cannot be negative");
            }
            precipitationType = Objects.requireNonNull(precipitationType, "precipitationType");

            // Reuse the public model's canonical bounds so payloads built by
            // server code and payloads decoded from the network behave alike.
            WeatherSample bounded = new WeatherSample(
                    temperature,
                    humidity,
                    pressure,
                    new WindVector(windX, windZ),
                    cloudWater,
                    instability,
                    stormEnergy,
                    precipitationIntensity,
                    precipitationType
            );
            temperature = (float) bounded.temperature();
            humidity = (float) bounded.humidity();
            pressure = (float) bounded.pressure();
            windX = (float) bounded.wind().x();
            windZ = (float) bounded.wind().z();
            cloudWater = (float) bounded.cloudWater();
            instability = (float) bounded.instability();
            stormEnergy = (float) bounded.stormEnergy();
            precipitationIntensity = (float) bounded.precipitationIntensity();
            precipitationType = bounded.precipitationType();
        }

        /** Copies one immutable grid view into its network representation. */
        public static CellSnapshot fromView(AtmosphereView view) {
            Objects.requireNonNull(view, "view");
            return fromSample(view.key(), view.revision(), view.sample());
        }

        /** Copies a shared weather sample into its network representation. */
        public static CellSnapshot fromSample(
                AtmosphereCellKey key,
                long revision,
                WeatherSample sample
        ) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(sample, "sample");
            return new CellSnapshot(
                    key.x(),
                    key.z(),
                    revision,
                    (float) sample.temperature(),
                    (float) sample.humidity(),
                    (float) sample.pressure(),
                    (float) sample.wind().x(),
                    (float) sample.wind().z(),
                    (float) sample.cloudWater(),
                    (float) sample.instability(),
                    (float) sample.stormEnergy(),
                    (float) sample.precipitationIntensity(),
                    sample.precipitationType()
            );
        }

        /** Reconstructs the immutable weather API sample used by client state. */
        public WeatherSample sample() {
            return new WeatherSample(
                    temperature,
                    humidity,
                    pressure,
                    new WindVector(windX, windZ),
                    cloudWater,
                    instability,
                    stormEnergy,
                    precipitationIntensity,
                    precipitationType
            );
        }
    }
}
