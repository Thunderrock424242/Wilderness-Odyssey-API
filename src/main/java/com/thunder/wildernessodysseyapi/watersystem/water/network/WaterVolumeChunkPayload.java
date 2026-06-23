package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Synchronizes one sparse canonical water chunk to a nearby client.
 *
 * @param chunkX chunk X coordinate
 * @param chunkZ chunk Z coordinate
 * @param revision server attachment revision
 * @param cellData compact fixed-stride cell data
 */
public record WaterVolumeChunkPayload(
        int chunkX,
        int chunkZ,
        long revision,
        int[] cellData
) implements CustomPacketPayload {

    private static final int MAX_NETWORK_CELLS = 16_384;
    private static final int MAX_NETWORK_INTS = MAX_NETWORK_CELLS * WaterVolumeChunk.SERIALIZED_CELL_STRIDE;

    /** Payload identifier used by NeoForge's play protocol. */
    public static final Type<WaterVolumeChunkPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "water_volume_chunk")
    );
    /** Bounded binary codec for canonical volume snapshots. */
    public static final StreamCodec<FriendlyByteBuf, WaterVolumeChunkPayload> STREAM_CODEC =
            StreamCodec.of(WaterVolumeChunkPayload::encode, WaterVolumeChunkPayload::decode);

    public WaterVolumeChunkPayload {
        cellData = cellData == null ? new int[0] : cellData.clone();
        if (cellData.length > MAX_NETWORK_INTS
                || cellData.length % WaterVolumeChunk.SERIALIZED_CELL_STRIDE != 0) {
            throw new IllegalArgumentException("Invalid canonical water cell payload length: " + cellData.length);
        }
    }

    /** Returns a defensive copy of the mutable primitive payload array. */
    @Override
    public int[] cellData() {
        return cellData.clone();
    }

    /** Builds a payload from a server chunk attachment. */
    public static WaterVolumeChunkPayload fromChunk(LevelChunk chunk, WaterVolumeChunk volume) {
        return new WaterVolumeChunkPayload(
                chunk.getPos().x,
                chunk.getPos().z,
                volume.revision(),
                volume.toNetworkArray()
        );
    }

    /** Applies the snapshot only when its client chunk is already loaded. */
    public void apply(Level level) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return;
        }
        WaterVolumeChunk volume = chunk.getData(ModAttachments.WATER_VOLUME);
        if (revision >= volume.revision()) {
            volume.applyNetworkSnapshot(revision, cellData);
        }
    }

    private static void encode(FriendlyByteBuf buffer, WaterVolumeChunkPayload payload) {
        buffer.writeInt(payload.chunkX);
        buffer.writeInt(payload.chunkZ);
        buffer.writeLong(payload.revision);
        buffer.writeVarInt(payload.cellData.length);
        for (int value : payload.cellData) {
            buffer.writeInt(value);
        }
    }

    private static WaterVolumeChunkPayload decode(FriendlyByteBuf buffer) {
        int chunkX = buffer.readInt();
        int chunkZ = buffer.readInt();
        long revision = buffer.readLong();
        int length = buffer.readVarInt();
        if (length < 0 || length > MAX_NETWORK_INTS
                || length % WaterVolumeChunk.SERIALIZED_CELL_STRIDE != 0) {
            throw new IllegalArgumentException("Invalid canonical water cell payload length: " + length);
        }
        int[] data = new int[length];
        for (int index = 0; index < length; index++) {
            data[index] = buffer.readInt();
        }
        return new WaterVolumeChunkPayload(chunkX, chunkZ, revision, data);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
