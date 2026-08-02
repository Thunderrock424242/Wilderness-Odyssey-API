package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Carries one contiguous sparse-water revision range after a paged baseline.
 *
 * <p>Updated cells retain the established seven-int representation. Removed
 * cells travel as packed-position tombstones so the client can delete sparse
 * overrides without receiving the complete chunk again.</p>
 *
 * @param chunkX chunk X coordinate
 * @param chunkZ chunk Z coordinate
 * @param fromRevision exact client revision required before applying
 * @param toRevision server revision after every included change
 * @param changeCount underlying contiguous revisions represented by this delta
 * @param upsertData fixed-stride final values for changed cells
 * @param tombstones packed positions removed during the revision range
 */
public record WaterVolumeDeltaPayload(
        int chunkX,
        int chunkZ,
        long fromRevision,
        long toRevision,
        int changeCount,
        int[] upsertData,
        int[] tombstones
) implements CustomPacketPayload {

    /** Payload identifier used by NeoForge's play protocol. */
    public static final Type<WaterVolumeDeltaPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "water_volume_delta")
    );
    /** Strict bounded codec for sparse revisions and tombstones. */
    public static final StreamCodec<FriendlyByteBuf, WaterVolumeDeltaPayload> STREAM_CODEC =
            StreamCodec.of(WaterVolumeDeltaPayload::encode, WaterVolumeDeltaPayload::decode);

    public WaterVolumeDeltaPayload {
        upsertData = upsertData == null ? new int[0] : upsertData.clone();
        tombstones = tombstones == null ? new int[0] : tombstones.clone();
        int upsertCount = upsertData.length / WaterVolumeChunk.SERIALIZED_CELL_STRIDE;
        if (fromRevision < 0L
                || toRevision <= fromRevision
                || toRevision - fromRevision != changeCount
                || changeCount < 1
                || changeCount > WaterVolumeChunk.MAX_DELTA_HISTORY
                || upsertData.length % WaterVolumeChunk.SERIALIZED_CELL_STRIDE != 0
                || upsertCount > WaterVolumeChunk.MAX_DELTA_HISTORY
                || tombstones.length > WaterVolumeChunk.MAX_DELTA_HISTORY
                || upsertCount + tombstones.length > changeCount) {
            throw new IllegalArgumentException("Invalid canonical water delta");
        }
    }

    /** Builds a payload from one server-owned contiguous delta snapshot. */
    public static WaterVolumeDeltaPayload from(
            int chunkX,
            int chunkZ,
            WaterVolumeChunk.DeltaSnapshot delta
    ) {
        if (delta == null || !delta.available() || delta.changeCount() <= 0) {
            throw new IllegalArgumentException("Cannot encode an unavailable canonical water delta");
        }
        return new WaterVolumeDeltaPayload(
                chunkX,
                chunkZ,
                delta.fromRevision(),
                delta.toRevision(),
                delta.changeCount(),
                delta.upsertData(),
                delta.tombstones()
        );
    }

    @Override
    public int[] upsertData() {
        return upsertData.clone();
    }

    @Override
    public int[] tombstones() {
        return tombstones.clone();
    }

    /** Applies immediately or queues behind the chunk's pending baseline. */
    public void apply(Level level) {
        ClientWaterVolumeSnapshots.accept(level, this);
    }

    long chunkKey() {
        return ((long) chunkX & 0xFFFF_FFFFL) | (((long) chunkZ & 0xFFFF_FFFFL) << 32);
    }

    private static void encode(FriendlyByteBuf buffer, WaterVolumeDeltaPayload payload) {
        buffer.writeInt(payload.chunkX);
        buffer.writeInt(payload.chunkZ);
        buffer.writeLong(payload.fromRevision);
        buffer.writeLong(payload.toRevision);
        buffer.writeVarInt(payload.changeCount);
        writeIntArray(buffer, payload.upsertData);
        writeIntArray(buffer, payload.tombstones);
    }

    private static WaterVolumeDeltaPayload decode(FriendlyByteBuf buffer) {
        int chunkX = buffer.readInt();
        int chunkZ = buffer.readInt();
        long fromRevision = buffer.readLong();
        long toRevision = buffer.readLong();
        int changeCount = buffer.readVarInt();
        int[] upserts = readIntArray(
                buffer,
                WaterVolumeChunk.MAX_DELTA_HISTORY * WaterVolumeChunk.SERIALIZED_CELL_STRIDE
        );
        int[] tombstones = readIntArray(buffer, WaterVolumeChunk.MAX_DELTA_HISTORY);
        return new WaterVolumeDeltaPayload(
                chunkX,
                chunkZ,
                fromRevision,
                toRevision,
                changeCount,
                upserts,
                tombstones
        );
    }

    private static void writeIntArray(FriendlyByteBuf buffer, int[] values) {
        buffer.writeVarInt(values.length);
        for (int value : values) {
            buffer.writeInt(value);
        }
    }

    private static int[] readIntArray(FriendlyByteBuf buffer, int maximumLength) {
        int length = buffer.readVarInt();
        if (length < 0 || length > maximumLength) {
            throw new IllegalArgumentException("Canonical water delta array exceeds its bound");
        }
        int[] values = new int[length];
        for (int index = 0; index < length; index++) {
            values[index] = buffer.readInt();
        }
        return values;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
