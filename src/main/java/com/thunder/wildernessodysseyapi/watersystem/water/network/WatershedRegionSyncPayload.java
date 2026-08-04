package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WatershedChunkState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Synchronizes a bounded immutable window of compact watershed chunk states.
 */
public record WatershedRegionSyncPayload(
        boolean enabled,
        List<ChunkSnapshot> chunks
) implements CustomPacketPayload {

    /** Largest possible configured 33 by 33 player window. */
    public static final int MAX_CHUNKS = 1_089;

    /** Payload identifier used by NeoForge's play protocol. */
    public static final Type<WatershedRegionSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "watershed_region")
    );
    /** Strict bounded codec using the same packed words as persistence. */
    public static final StreamCodec<FriendlyByteBuf, WatershedRegionSyncPayload> STREAM_CODEC =
            StreamCodec.of(WatershedRegionSyncPayload::encode, WatershedRegionSyncPayload::decode);

    public WatershedRegionSyncPayload {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        if (chunks.size() > MAX_CHUNKS) {
            throw new IllegalArgumentException("Watershed payload exceeds " + MAX_CHUNKS + " chunks");
        }
        if (!enabled && !chunks.isEmpty()) {
            chunks = List.of();
        }
    }

    /** Creates the explicit disabled-state payload used to clear stale clients. */
    public static WatershedRegionSyncPayload disabled() {
        return new WatershedRegionSyncPayload(false, List.of());
    }

    private static void encode(FriendlyByteBuf buffer, WatershedRegionSyncPayload payload) {
        buffer.writeBoolean(payload.enabled);
        buffer.writeVarInt(payload.chunks.size());
        for (ChunkSnapshot chunk : payload.chunks) {
            buffer.writeInt(chunk.chunkX);
            buffer.writeInt(chunk.chunkZ);
            WatershedChunkState.Packed packed = chunk.packed;
            buffer.writeLong(packed.basinId());
            buffer.writeLong(packed.terrainBits());
            buffer.writeLong(packed.hydrologyBits());
            buffer.writeLong(packed.environmentBits());
            buffer.writeLong(packed.flowBits());
            buffer.writeLong(packed.climateBits());
            buffer.writeLong(packed.drainageDirectionBits());
            buffer.writeLong(packed.drainageAccumulationBits());
            buffer.writeLong(packed.representativePosition());
            buffer.writeVarLong(packed.revision());
            buffer.writeVarInt(packed.activeFloodCells());
        }
    }

    private static WatershedRegionSyncPayload decode(FriendlyByteBuf buffer) {
        boolean enabled = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_CHUNKS) {
            throw new IllegalArgumentException("Invalid watershed chunk count: " + count);
        }
        List<ChunkSnapshot> chunks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int chunkX = buffer.readInt();
            int chunkZ = buffer.readInt();
            WatershedChunkState.Packed packed = new WatershedChunkState.Packed(
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readVarLong(),
                    0L,
                    0,
                    buffer.readVarInt()
            );
            chunks.add(new ChunkSnapshot(chunkX, chunkZ, packed));
        }
        return new WatershedRegionSyncPayload(enabled, chunks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** One chunk coordinate and its exact compact synchronized conditions. */
    public record ChunkSnapshot(int chunkX, int chunkZ, WatershedChunkState.Packed packed) {
        public ChunkSnapshot {
            if (packed == null) {
                throw new IllegalArgumentException("Packed watershed chunk state is required");
            }
        }

        /** Returns the stable signed chunk key used by client stores. */
        public long chunkKey() {
            return ((long) chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
        }
    }
}
