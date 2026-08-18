package com.thunder.wildernessodysseyapi.weather.networking;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.system.TrackedWeatherSystem;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemStage;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemType;
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
 * Synchronizes a bounded summary of persistent storms for client-side distant audio.
 *
 * <p>The server remains authoritative for storm identity and atmospheric fields.
 * Clients receive no mutation route and use these summaries only to choose local,
 * cosmetic thunder. Fronts are rejected because rain or pressure change alone is
 * not evidence of a thunderstorm.</p>
 */
public record DistantThunderSystemSyncPayload(
        ResourceLocation dimension,
        int dataVersion,
        long sequence,
        boolean enabled,
        List<StormSnapshot> storms
) implements CustomPacketPayload {

    public static final int DATA_VERSION = 1;
    public static final int MAX_STORMS = 64;

    /** Payload identifier used by NeoForge's client-bound play protocol. */
    public static final Type<DistantThunderSystemSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "distant_thunder_systems")
    );
    /** Bounded codec for the low-frequency, server-authored storm summaries. */
    public static final StreamCodec<FriendlyByteBuf, DistantThunderSystemSyncPayload> STREAM_CODEC =
            StreamCodec.of(DistantThunderSystemSyncPayload::encode, DistantThunderSystemSyncPayload::decode);

    public DistantThunderSystemSyncPayload {
        dimension = Objects.requireNonNull(dimension, "dimension");
        validateHeader(dataVersion, sequence);
        storms = List.copyOf(Objects.requireNonNull(storms, "storms"));
        if (storms.size() > MAX_STORMS) {
            throw new IllegalArgumentException("Distant-thunder payload exceeds the storm limit");
        }
        if (!enabled && !storms.isEmpty()) {
            throw new IllegalArgumentException("A disabled distant-thunder payload must be empty");
        }

        Set<Long> uniqueIds = new HashSet<>(storms.size());
        for (StormSnapshot storm : storms) {
            if (!uniqueIds.add(storm.id())) {
                throw new IllegalArgumentException("Distant-thunder payload contains duplicate storm ids");
            }
        }
    }

    /** Creates the explicit empty state sent when localized weather is disabled. */
    public static DistantThunderSystemSyncPayload disabled(ResourceLocation dimension, long sequence) {
        return new DistantThunderSystemSyncPayload(dimension, DATA_VERSION, sequence, false, List.of());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, DistantThunderSystemSyncPayload payload) {
        buffer.writeResourceLocation(payload.dimension);
        buffer.writeVarInt(payload.dataVersion);
        buffer.writeVarLong(payload.sequence);
        buffer.writeBoolean(payload.enabled);
        buffer.writeVarInt(payload.storms.size());
        for (StormSnapshot storm : payload.storms) {
            buffer.writeVarLong(storm.id);
            buffer.writeByte(storm.type.ordinal());
            buffer.writeByte(storm.stage.ordinal());
            buffer.writeDouble(storm.centerX);
            buffer.writeDouble(storm.centerZ);
            buffer.writeFloat((float) storm.radiusBlocks);
            buffer.writeByte(quantizeUnit(storm.intensity));
            buffer.writeShort(quantizeSignedUnit(storm.motionX));
            buffer.writeShort(quantizeSignedUnit(storm.motionZ));
            buffer.writeByte(quantizeUnit(storm.organization));
            buffer.writeByte(storm.precipitationType.ordinal());
            buffer.writeByte(quantizeUnit(storm.precipitationIntensity));
            buffer.writeByte(quantizeUnit(storm.stormEnergy));
            buffer.writeByte(quantizeUnit(storm.instability));
            buffer.writeByte(quantizeUnit(storm.thunderPotential));
        }
    }

    private static DistantThunderSystemSyncPayload decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        int dataVersion = buffer.readVarInt();
        long sequence = buffer.readVarLong();
        validateHeader(dataVersion, sequence);
        boolean enabled = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_STORMS) {
            throw new IllegalArgumentException("Invalid distant-thunder storm count: " + count);
        }
        if (!enabled && count != 0) {
            throw new IllegalArgumentException("A disabled distant-thunder payload must be empty");
        }

        List<StormSnapshot> storms = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            storms.add(new StormSnapshot(
                    buffer.readVarLong(),
                    enumValue(WeatherSystemType.values(), buffer.readUnsignedByte(), "weather-system type"),
                    enumValue(WeatherSystemStage.values(), buffer.readUnsignedByte(), "weather-system stage"),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readFloat(),
                    dequantizeUnit(buffer.readUnsignedByte()),
                    dequantizeSignedUnit(buffer.readShort()),
                    dequantizeSignedUnit(buffer.readShort()),
                    dequantizeUnit(buffer.readUnsignedByte()),
                    enumValue(PrecipitationType.values(), buffer.readUnsignedByte(), "precipitation type"),
                    dequantizeUnit(buffer.readUnsignedByte()),
                    dequantizeUnit(buffer.readUnsignedByte()),
                    dequantizeUnit(buffer.readUnsignedByte()),
                    dequantizeUnit(buffer.readUnsignedByte())
            ));
        }
        return new DistantThunderSystemSyncPayload(dimension, dataVersion, sequence, enabled, storms);
    }

    private static void validateHeader(int dataVersion, long sequence) {
        if (dataVersion != DATA_VERSION) {
            throw new IllegalArgumentException("Unsupported distant-thunder data version: " + dataVersion);
        }
        if (sequence < 0L) {
            throw new IllegalArgumentException("Distant-thunder sequence cannot be negative");
        }
    }

    private static int quantizeUnit(double value) {
        return Math.round((float) unit(value) * 255.0F);
    }

    private static float dequantizeUnit(int value) {
        return Math.max(0, Math.min(255, value)) / 255.0F;
    }

    private static int quantizeSignedUnit(double value) {
        double finite = Double.isFinite(value) ? value : 0.0;
        return Math.round((float) Math.max(-1.0, Math.min(1.0, finite)) * 32_767.0F);
    }

    private static float dequantizeSignedUnit(short value) {
        return Math.max(-1.0F, Math.min(1.0F, value / 32_767.0F));
    }

    private static <T> T enumValue(T[] values, int ordinal, String name) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid " + name + " ordinal: " + ordinal);
        }
        return values[ordinal];
    }

    private static double coordinate(double value) {
        double finite = Double.isFinite(value) ? value : 0.0;
        return Math.max(-30_000_000.0, Math.min(30_000_000.0, finite));
    }

    private static double unit(double value) {
        double finite = Double.isFinite(value) ? value : 0.0;
        return Math.max(0.0, Math.min(1.0, finite));
    }

    /**
     * Immutable audio-facing state derived from one persistent storm and its current cell sample.
     */
    public record StormSnapshot(
            long id,
            WeatherSystemType type,
            WeatherSystemStage stage,
            double centerX,
            double centerZ,
            double radiusBlocks,
            double intensity,
            double motionX,
            double motionZ,
            double organization,
            PrecipitationType precipitationType,
            double precipitationIntensity,
            double stormEnergy,
            double instability,
            double thunderPotential
    ) {
        public StormSnapshot {
            if (id <= 0L) {
                throw new IllegalArgumentException("Distant-thunder storm id must be positive");
            }
            type = Objects.requireNonNull(type, "type");
            if (!type.storm()) {
                throw new IllegalArgumentException("Atmospheric fronts cannot produce distant-thunder summaries");
            }
            stage = Objects.requireNonNull(stage, "stage");
            centerX = coordinate(centerX);
            centerZ = coordinate(centerZ);
            double finiteRadius = Double.isFinite(radiusBlocks) ? radiusBlocks : 16.0;
            radiusBlocks = Math.max(16.0, Math.min(8_192.0, finiteRadius));
            intensity = unit(intensity);
            double finiteMotionX = Double.isFinite(motionX) ? motionX : 0.0;
            double finiteMotionZ = Double.isFinite(motionZ) ? motionZ : 0.0;
            double motionLength = Math.hypot(finiteMotionX, finiteMotionZ);
            double motionScale = motionLength > 1.0 ? 1.0 / motionLength : 1.0;
            motionX = finiteMotionX * motionScale;
            motionZ = finiteMotionZ * motionScale;
            organization = unit(organization);
            precipitationType = Objects.requireNonNull(precipitationType, "precipitationType");
            precipitationIntensity = unit(precipitationIntensity);
            if (precipitationIntensity == 0.0) {
                precipitationType = PrecipitationType.NONE;
            }
            stormEnergy = unit(stormEnergy);
            instability = unit(instability);
            thunderPotential = unit(thunderPotential);
        }

        /** Captures the moving identity together with its authoritative current atmosphere sample. */
        public static StormSnapshot fromSystem(TrackedWeatherSystem system, WeatherSample sample) {
            Objects.requireNonNull(system, "system");
            WeatherSample safeSample = Objects.requireNonNullElse(sample, WeatherSample.CLEAR);
            return new StormSnapshot(
                    system.id(),
                    system.type(),
                    system.stage(),
                    system.centerX(),
                    system.centerZ(),
                    system.radiusBlocks(),
                    system.intensity(),
                    system.motion().x(),
                    system.motion().z(),
                    system.organization(),
                    safeSample.precipitationType(),
                    safeSample.precipitationIntensity(),
                    safeSample.stormEnergy(),
                    safeSample.instability(),
                    safeSample.thunderIntensity()
            );
        }
    }
}
