package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Synchronizes one sparse canonical water chunk to a nearby client.
 *
 * @param chunkX chunk X coordinate
 * @param chunkZ chunk Z coordinate
 * @param revision server attachment revision
 * @param pageIndex zero-based page index within this revision
 * @param pageCount total pages required to reconstruct this revision
 * @param cellData compact fixed-stride cell data for this page
 */
public record WaterVolumeChunkPayload(
        int chunkX,
        int chunkZ,
        long revision,
        int pageIndex,
        int pageCount,
        int[] cellData
) implements CustomPacketPayload {

    /** Keeps each payload bounded while allowing a complete chunk to span pages. */
    public static final int MAX_CELLS_PER_PAGE = 1_024;
    private static final int MAX_NETWORK_PAGES = 128;
    private static final int MAX_NETWORK_INTS = MAX_CELLS_PER_PAGE * WaterVolumeChunk.SERIALIZED_CELL_STRIDE;

    /** Payload identifier used by NeoForge's play protocol. */
    public static final Type<WaterVolumeChunkPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "water_volume_chunk")
    );
    /** Bounded binary codec for canonical volume snapshots. */
    public static final StreamCodec<FriendlyByteBuf, WaterVolumeChunkPayload> STREAM_CODEC =
            StreamCodec.of(WaterVolumeChunkPayload::encode, WaterVolumeChunkPayload::decode);

    public WaterVolumeChunkPayload {
        cellData = cellData == null ? new int[0] : cellData.clone();
        if (revision < 0L
                || pageCount < 1 || pageCount > MAX_NETWORK_PAGES
                || pageIndex < 0 || pageIndex >= pageCount
                || cellData.length > MAX_NETWORK_INTS
                || cellData.length % WaterVolumeChunk.SERIALIZED_CELL_STRIDE != 0) {
            throw new IllegalArgumentException("Invalid canonical water snapshot page");
        }
    }

    /** Returns a defensive copy of the mutable primitive payload array. */
    @Override
    public int[] cellData() {
        return cellData.clone();
    }

    /** Number of complete sparse cells carried by this bounded page. */
    public int cellCount() {
        return cellData.length / WaterVolumeChunk.SERIALIZED_CELL_STRIDE;
    }

    /** Builds bounded payload pages from a complete server chunk attachment. */
    public static List<WaterVolumeChunkPayload> pagesFromChunk(LevelChunk chunk, WaterVolumeChunk volume) {
        return pagesFromData(
                chunk.getPos().x,
                chunk.getPos().z,
                volume.revision(),
                volume.toNetworkArray()
        );
    }

    // Kept separate from LevelChunk access so paging invariants can be tested
    // without constructing a loaded Minecraft world.
    static List<WaterVolumeChunkPayload> pagesFromData(
            int chunkX,
            int chunkZ,
            long revision,
            int[] completeData
    ) {
        if (completeData == null
                || completeData.length % WaterVolumeChunk.SERIALIZED_CELL_STRIDE != 0) {
            throw new IllegalArgumentException("Invalid complete canonical water snapshot");
        }
        int pageCount = Math.max(1, (completeData.length + MAX_NETWORK_INTS - 1) / MAX_NETWORK_INTS);
        if (pageCount > MAX_NETWORK_PAGES) {
            throw new IllegalStateException("Canonical water chunk exceeds the network page limit: "
                    + completeData.length / WaterVolumeChunk.SERIALIZED_CELL_STRIDE + " cells");
        }

        List<WaterVolumeChunkPayload> pages = new ArrayList<>(pageCount);
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int from = pageIndex * MAX_NETWORK_INTS;
            int to = Math.min(completeData.length, from + MAX_NETWORK_INTS);
            pages.add(new WaterVolumeChunkPayload(
                    chunkX,
                    chunkZ,
                    revision,
                    pageIndex,
                    pageCount,
                    Arrays.copyOfRange(completeData, from, to)
            ));
        }
        return List.copyOf(pages);
    }

    /** Applies immediately or retains the snapshot until its client chunk loads. */
    public void apply(Level level) {
        ClientWaterVolumeSnapshots.accept(level, this);
    }

    /** Applies a fully assembled revision if its client chunk is loaded. */
    static boolean applySnapshotIfLoaded(
            Level level,
            int chunkX,
            int chunkZ,
            long revision,
            int[] completeData
    ) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return false;
        }
        ClientWaterSnapshotStore.publishSparse(level, chunkX, chunkZ, revision, completeData);
        return true;
    }

    /** Packed chunk coordinate used to retain only the newest pending revision. */
    long chunkKey() {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }

    private static void encode(FriendlyByteBuf buffer, WaterVolumeChunkPayload payload) {
        buffer.writeInt(payload.chunkX);
        buffer.writeInt(payload.chunkZ);
        buffer.writeLong(payload.revision);
        buffer.writeVarInt(payload.pageIndex);
        buffer.writeVarInt(payload.pageCount);
        buffer.writeVarInt(payload.cellData.length);
        for (int value : payload.cellData) {
            buffer.writeInt(value);
        }
    }

    private static WaterVolumeChunkPayload decode(FriendlyByteBuf buffer) {
        int chunkX = buffer.readInt();
        int chunkZ = buffer.readInt();
        long revision = buffer.readLong();
        int pageIndex = buffer.readVarInt();
        int pageCount = buffer.readVarInt();
        int length = buffer.readVarInt();
        if (length < 0 || length > MAX_NETWORK_INTS
                || length % WaterVolumeChunk.SERIALIZED_CELL_STRIDE != 0) {
            throw new IllegalArgumentException("Invalid canonical water cell payload length: " + length);
        }
        int[] data = new int[length];
        for (int index = 0; index < length; index++) {
            data[index] = buffer.readInt();
        }
        return new WaterVolumeChunkPayload(chunkX, chunkZ, revision, pageIndex, pageCount, data);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
