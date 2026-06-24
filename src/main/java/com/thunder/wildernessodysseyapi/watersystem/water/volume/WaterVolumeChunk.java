package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores sparse canonical water cells for one Minecraft chunk.
 *
 * <p>One full block uses {@value #UNITS_PER_BLOCK} fixed-point volume units.
 * Sparse storage keeps untouched world-generation water out of chunk NBT until
 * it is imported or changed by the replacement simulation. Runtime mutations
 * happen on the logical server; clients receive immutable network snapshots.</p>
 */
public final class WaterVolumeChunk implements INBTSerializable<CompoundTag> {

    /** Fixed-point volume represented by one full block of water. */
    public static final int UNITS_PER_BLOCK = 4_096;
    /** Cell originated from an existing vanilla block and has not been disturbed. */
    public static final int FLAG_IMPORTED = 1;
    /** Vanilla fluid state is being maintained as a compatibility projection. */
    public static final int FLAG_COMPATIBILITY_PROJECTED = 1 << 1;
    /** Primitive integers encoded for each persisted or networked cell. */
    public static final int SERIALIZED_CELL_STRIDE = 7;

    private static final String REVISION_KEY = "revision";
    private static final String CELL_DATA_KEY = "cells";
    private final Map<Integer, WaterCell> cells = new HashMap<>();
    private long revision;
    private boolean dirty;
    private Runnable dirtyListener = () -> { };

    /** Sets the callback used to mark the owning chunk unsaved. */
    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> { } : dirtyListener;
    }

    /** Returns the immutable cell at a world position, or an empty cell. */
    public WaterCell get(BlockPos pos) {
        return cells.getOrDefault(pack(pos), WaterCell.EMPTY);
    }

    /** Returns whether this chunk has authoritative state for the position. */
    public boolean contains(BlockPos pos) {
        return cells.containsKey(pack(pos));
    }

    /**
     * Replaces one canonical cell and increments the synchronization revision.
     * A zero-volume value removes the sparse entry.
     */
    public void set(BlockPos pos, WaterCell cell) {
        setPacked(pack(pos), cell, true);
    }

    /** Returns a stable copy suitable for ticking or network encoding. */
    public List<CellEntry> snapshot() {
        List<CellEntry> snapshot = new ArrayList<>(cells.size());
        for (Map.Entry<Integer, WaterCell> entry : cells.entrySet()) {
            snapshot.add(new CellEntry(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(snapshot);
    }

    /** Current monotonically increasing synchronization revision. */
    public long revision() {
        return revision;
    }

    /** Returns whether this attachment changed since its last chunk save. */
    public boolean isDirty() {
        return dirty;
    }

    /** Clears the local dirty marker after chunk persistence completes. */
    public void clearDirty() {
        dirty = false;
    }

    /** Encodes the complete sparse state for paged client snapshots. */
    public int[] toNetworkArray() {
        return encodeCells();
    }

    /** Replaces client mirror state without marking the client chunk unsaved. */
    public void applyNetworkSnapshot(long revision, int[] data) {
        cells.clear();
        decodeCells(data, false);
        this.revision = Math.max(0L, revision);
        dirty = false;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(REVISION_KEY, revision);
        tag.putIntArray(CELL_DATA_KEY, encodeCells());
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        cells.clear();
        decodeCells(tag.getIntArray(CELL_DATA_KEY), false);
        revision = Math.max(0L, tag.getLong(REVISION_KEY));
        dirty = false;
    }

    private void setPacked(int packedPosition, WaterCell cell, boolean notify) {
        WaterCell sanitized = cell == null ? WaterCell.EMPTY : cell.sanitized();
        WaterCell previous;
        if (sanitized.volumeUnits == 0) {
            previous = cells.remove(packedPosition);
            if (previous == null) {
                return;
            }
        } else {
            previous = cells.put(packedPosition, sanitized);
            if (sanitized.equals(previous)) {
                return;
            }
        }

        if (notify) {
            revision++;
            dirty = true;
            dirtyListener.run();
        }
    }

    private int[] encodeCells() {
        int cellCount = cells.size();
        int[] data = new int[cellCount * SERIALIZED_CELL_STRIDE];
        int index = 0;
        for (Map.Entry<Integer, WaterCell> entry : cells.entrySet()) {
            int offset = index++ * SERIALIZED_CELL_STRIDE;
            WaterCell cell = entry.getValue();
            data[offset] = entry.getKey();
            data[offset + 1] = cell.volumeUnits;
            data[offset + 2] = Float.floatToIntBits(cell.velocityX);
            data[offset + 3] = Float.floatToIntBits(cell.velocityY);
            data[offset + 4] = Float.floatToIntBits(cell.velocityZ);
            data[offset + 5] = cell.flags;
            data[offset + 6] = cell.temperatureMilliKelvin;
        }
        return data;
    }

    private void decodeCells(int[] data, boolean notify) {
        int cellCount = data.length / SERIALIZED_CELL_STRIDE;
        for (int index = 0; index < cellCount; index++) {
            int offset = index * SERIALIZED_CELL_STRIDE;
            setPacked(data[offset], new WaterCell(
                    data[offset + 1],
                    Float.intBitsToFloat(data[offset + 2]),
                    Float.intBitsToFloat(data[offset + 3]),
                    Float.intBitsToFloat(data[offset + 4]),
                    data[offset + 5],
                    data[offset + 6]
            ), notify);
        }
    }

    /** Packs local X/Z and signed world Y into one compact integer key. */
    public static int pack(BlockPos pos) {
        return (pos.getX() & 15)
                | ((pos.getZ() & 15) << 4)
                | ((pos.getY() & 0xFFF) << 8);
    }

    /** Reconstructs a world position from a chunk position and packed key. */
    public static BlockPos unpack(int chunkX, int chunkZ, int packedPosition) {
        int localX = packedPosition & 15;
        int localZ = (packedPosition >>> 4) & 15;
        int y = (packedPosition >>> 8) & 0xFFF;
        if ((y & 0x800) != 0) {
            y -= 0x1000;
        }
        return new BlockPos((chunkX << 4) + localX, y, (chunkZ << 4) + localZ);
    }

    /** Immutable canonical state for one block-sized volume cell. */
    public record WaterCell(
            int volumeUnits,
            float velocityX,
            float velocityY,
            float velocityZ,
            int flags,
            int temperatureMilliKelvin
    ) {
        /** Standard liquid-water temperature used until thermal simulation supplies one. */
        public static final int DEFAULT_TEMPERATURE_MILLI_KELVIN = 293_150;
        /** Shared empty value returned for untracked positions. */
        public static final WaterCell EMPTY = new WaterCell(0, 0.0f, 0.0f, 0.0f, 0,
                DEFAULT_TEMPERATURE_MILLI_KELVIN);

        /** Creates still water with the supplied volume and provenance flags. */
        public static WaterCell still(int volumeUnits, int flags) {
            return new WaterCell(volumeUnits, 0.0f, 0.0f, 0.0f, flags,
                    DEFAULT_TEMPERATURE_MILLI_KELVIN).sanitized();
        }

        /** Returns a bounded, finite representation safe for persistence. */
        public WaterCell sanitized() {
            return new WaterCell(
                    Math.max(0, Math.min(UNITS_PER_BLOCK, volumeUnits)),
                    finiteOrZero(velocityX),
                    finiteOrZero(velocityY),
                    finiteOrZero(velocityZ),
                    flags,
                    Math.max(0, temperatureMilliKelvin)
            );
        }

        /** Returns the fractional fill height from zero to one. */
        public float fillFraction() {
            return volumeUnits / (float) UNITS_PER_BLOCK;
        }

        /** Returns whether this cell was lazily imported from vanilla state. */
        public boolean imported() {
            return (flags & FLAG_IMPORTED) != 0;
        }

        private static float finiteOrZero(float value) {
            return Float.isFinite(value) ? value : 0.0f;
        }
    }

    /** Packed entry used by simulation and networking without exposing mutable maps. */
    public record CellEntry(int packedPosition, WaterCell cell) {
    }
}
