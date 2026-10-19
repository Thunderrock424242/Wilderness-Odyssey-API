package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.ArrayList;
import java.util.List;

/**
 * Chunk-owned generation evidence and read-only reach metadata. CARVERS captures
 * survive interrupted generation, then are discarded when the tile is processed.
 * Only completed reach data is synchronized; this is never a water inventory.
 */
public final class RiverGenerationChunk implements INBTSerializable<CompoundTag> {
    private static final int FORMAT = 1;
    private static final int STRIDE = 5;
    public static final StreamCodec<RegistryFriendlyByteBuf, RiverGenerationChunk> STREAM_CODEC =
            StreamCodec.of(RiverGenerationChunk::encode, RiverGenerationChunk::decode);
    private long fingerprint;
    private int[] terrain;
    private boolean processed;
    private long basinId;
    private int[] reaches = new int[0];
    private Snapshot snapshot = Snapshot.EMPTY;

    public static RiverGenerationChunk captured(long fingerprint, int[] terrain) {
        if (terrain.length != 512) throw new IllegalArgumentException("Expected 256 captured columns");
        var data = new RiverGenerationChunk();
        data.fingerprint = fingerprint;
        data.terrain = terrain.clone();
        return data;
    }

    public boolean hasTerrain(long expectedFingerprint) {
        return !processed && terrain != null && fingerprint == expectedFingerprint;
    }

    public int height(int x, int z) { return (short) terrain[index(x, z) * 2]; }
    public boolean water(int x, int z) { return (terrain[index(x, z) * 2] & 0x10000) != 0; }
    public int naturalMask(int x, int z) { return terrain[index(x, z) * 2 + 1] & 0xFFFF; }
    public boolean naturalAt(int x, int y, int z) {
        int depth = height(x, z) - y;
        return depth >= 0 && depth < 16 && (naturalMask(x, z) & (1 << depth)) != 0;
    }
    public boolean processed() { return processed; }
    public long basinId() { return basinId; }

    /** Completes this chunk's part of a tile, retaining at most one reach per column. */
    public void complete(long basinId, int tileOffsetX, int tileOffsetZ, List<RiverChannelColumn> columns) {
        List<Integer> encoded = new ArrayList<>();
        for (var column : columns) {
            int x = column.x() - tileOffsetX;
            int z = column.z() - tileOffsetZ;
            if (x < 0 || x >= 16 || z < 0 || z >= 16) continue;
            encoded.add(index(x, z) | (column.kind().ordinal() << 8));
            encoded.add((column.waterY() & 0xFFFF) | (column.floorY() << 16));
            encoded.add(column.topY());
            encoded.add((Math.round(column.directionX() * 32767) & 0xFFFF)
                    | (Math.round(column.directionZ() * 32767) << 16));
            encoded.add(Math.round(column.speed() * 1000));
        }
        int[] next = encoded.stream().mapToInt(Integer::intValue).toArray();
        validate(next);
        this.reaches = next;
        this.basinId = basinId;
        this.processed = true;
        this.terrain = null;
        this.snapshot = new Snapshot(basinId, next);
    }

    /** Sample at a chunk-local column; directions are unit vectors and speed is m/s. */
    public Reach reachAt(int x, int z) {
        return snapshot.reachAt(x, z);
    }

    public Snapshot snapshot() { return snapshot; }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putInt("format", FORMAT);
        tag.putBoolean("processed", processed);
        tag.putLong("basin", basinId);
        if (terrain != null) {
            tag.putLong("settings", fingerprint);
            tag.putIntArray("terrain", terrain);
        }
        if (reaches.length > 0) tag.putIntArray("reaches", reaches);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        if (tag.getInt("format") != FORMAT) throw new IllegalArgumentException("Unsupported river metadata version");
        int[] capture = tag.contains("terrain") ? tag.getIntArray("terrain") : null;
        if (capture != null && capture.length != 512) throw new IllegalArgumentException("Truncated river terrain capture");
        int[] next = tag.getIntArray("reaches");
        validate(next);
        processed = tag.getBoolean("processed");
        basinId = tag.getLong("basin");
        fingerprint = tag.getLong("settings");
        terrain = processed ? null : capture;
        reaches = next;
        snapshot = new Snapshot(basinId, reaches);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, RiverGenerationChunk value) {
        buffer.writeBoolean(value.processed);
        buffer.writeLong(value.basinId);
        buffer.writeVarIntArray(value.reaches);
    }

    private static RiverGenerationChunk decode(RegistryFriendlyByteBuf buffer) {
        var value = new RiverGenerationChunk();
        value.processed = buffer.readBoolean();
        value.basinId = buffer.readLong();
        value.reaches = buffer.readVarIntArray(256 * STRIDE);
        validate(value.reaches);
        value.snapshot = new Snapshot(value.basinId, value.reaches);
        return value;
    }

    private static void validate(int[] values) {
        if (values.length % STRIDE != 0 || values.length > 256 * STRIDE) {
            throw new IllegalArgumentException("Invalid river reach count");
        }
        boolean[] used = new boolean[256];
        for (int i = 0; i < values.length; i += STRIDE) {
            int key = values[i] & 255;
            int kind = values[i] >>> 8;
            int waterY = (short) values[i + 1];
            int floorY = (short) (values[i + 1] >>> 16);
            if (used[key] || kind >= RiverChannelColumn.Kind.values().length || floorY >= waterY
                    || values[i + 2] < waterY || values[i + 2] > waterY + 4
                    || values[i + 4] < 0 || values[i + 4] > 8000) {
                throw new IllegalArgumentException("Invalid river reach column");
            }
            used[key] = true;
        }
    }

    private static int index(int x, int z) {
        if (x < 0 || x >= 16 || z < 0 || z >= 16) throw new IllegalArgumentException("Expected chunk-local coordinates");
        return x + z * 16;
    }

    public record Reach(int waterY, int floorY, int topY, float directionX, float directionZ,
                        float speed, RiverChannelColumn.Kind kind) { }

    /** Immutable direct-indexed compact view shared with client water snapshots. */
    public static final class Snapshot {
        public static final Snapshot EMPTY = new Snapshot(0, new int[0]);
        private final long basinId;
        private final int[] data;
        private final short[] offsets;

        private Snapshot(long basinId, int[] data) {
            this.basinId = basinId;
            this.data = data.clone();
            offsets = data.length == 0 ? new short[0] : new short[256];
            for (int i = 0; i < data.length; i += STRIDE) offsets[data[i] & 255] = (short) (i + 1);
        }
        public long basinId() { return basinId; }
        public int estimatedBytes() { return data.length * Integer.BYTES + offsets.length * Short.BYTES; }
        public Reach reachAt(int x, int z) {
            int key = index(x, z);
            if (offsets.length == 0 || offsets[key] == 0) return null;
            int i = offsets[key] - 1;
            return new Reach((short) data[i + 1], (short) (data[i + 1] >>> 16), data[i + 2],
                    (short) data[i + 3] / 32767f, (short) (data[i + 3] >>> 16) / 32767f,
                    data[i + 4] / 1000f, RiverChannelColumn.Kind.values()[data[i] >>> 8]);
        }
    }
}
