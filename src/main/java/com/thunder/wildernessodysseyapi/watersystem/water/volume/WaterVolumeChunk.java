package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Stores sparse canonical water cells for one Minecraft chunk.
 *
 * <p>One full block uses {@value #UNITS_PER_BLOCK} fixed-point volume units.
 * Sparse storage keeps untouched world-generation water out of chunk NBT until
 * it is imported or changed by the replacement simulation. Runtime mutations
 * happen on the logical server; clients receive immutable network snapshots.</p>
 */
public final class WaterVolumeChunk implements INBTSerializable<CompoundTag> {

    /** Independent durable format for sparse authority cells. */
    public static final int FORMAT_VERSION = 1;
    /** Fixed-point volume represented by one full block of water. */
    public static final int UNITS_PER_BLOCK = 4_096;
    /** Cell originated from an existing vanilla block and has not been disturbed. */
    public static final int FLAG_IMPORTED = 1;
    /** Vanilla fluid state is being maintained as a compatibility projection. */
    public static final int FLAG_COMPATIBILITY_PROJECTED = 1 << 1;
    /** Water lives inside another block's waterlogged fluid state; do not replace that host. */
    public static final int FLAG_HOSTED_WATER = 1 << 2;
    /** Local detailed cell is stable and should not consume active-flow ticks until disturbed nearby. */
    public static final int FLAG_SLEEPING = 1 << 3;
    /** Sparse state intentionally replaces an immutable generated-water baseline cell. */
    public static final int FLAG_GENERATED_OVERRIDE = 1 << 4;
    /** Zero-volume override that keeps drained generated water from reappearing through metadata. */
    public static final int FLAG_DRY_OVERRIDE = 1 << 5;
    /** Conserved volume hidden behind a solid until terrain exposes the cell. */
    public static final int FLAG_DISPLACEMENT_RESERVOIR = 1 << 6;
    /** Exact reversible floodwater owned by the temporary-flood ledger. */
    public static final int FLAG_TEMPORARY_FLOOD = 1 << 7;
    /** Primitive integers encoded for each persisted or networked cell. */
    public static final int SERIALIZED_CELL_STRIDE = 7;
    /** Structural limit prevents malformed attachments from allocating unbounded maps. */
    public static final int MAX_PERSISTED_CELLS = 131_072;
    /** Recent single-cell revisions retained for bounded incremental client sync. */
    public static final int MAX_DELTA_HISTORY = 4_096;

    private static final int PACKED_POSITION_MASK = 0x000F_FFFF;
    private static final String FORMAT_KEY = "format_version";
    private static final String CELL_COUNT_KEY = "cell_count";
    private static final String REVISION_KEY = "revision";
    private static final String CELL_DATA_KEY = "cells";
    private final Map<Integer, WaterCell> cells = new HashMap<>();
    private final ArrayDeque<CellDelta> deltaHistory = new ArrayDeque<>();
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
     * A zero-volume value removes the sparse entry unless it is an explicit dry
     * override for generated water.
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
        Map<Integer, WaterCell> decoded = decodeCellData(data, MAX_PERSISTED_CELLS);
        cells.clear();
        cells.putAll(decoded);
        this.revision = Math.max(0L, revision);
        deltaHistory.clear();
        dirty = false;
    }

    /**
     * Returns a contiguous, bounded delta after a client-owned revision.
     *
     * <p>When history has expired, {@link DeltaSnapshot#available()} is false and
     * the synchronizer must fall back to the existing paged baseline. Multiple
     * writes to the same position within the returned range are coalesced to the
     * final cell or tombstone while {@code toRevision} still advances across
     * every underlying revision.</p>
     */
    public DeltaSnapshot deltaSince(long fromRevision, int maximumChanges) {
        int boundedMaximum = Math.max(1, Math.min(MAX_DELTA_HISTORY, maximumChanges));
        if (fromRevision == revision) {
            return new DeltaSnapshot(true, fromRevision, revision, 0,
                    new int[0], new int[0], true);
        }
        if (fromRevision < 0L || fromRevision > revision || deltaHistory.isEmpty()) {
            return DeltaSnapshot.unavailable(fromRevision, revision);
        }

        long requiredFirstRevision = fromRevision + 1L;
        if (deltaHistory.getFirst().revision() > requiredFirstRevision) {
            return DeltaSnapshot.unavailable(fromRevision, revision);
        }

        LinkedHashMap<Integer, WaterCell> latest = new LinkedHashMap<>();
        long expectedRevision = requiredFirstRevision;
        int processedChanges = 0;
        for (CellDelta delta : deltaHistory) {
            if (delta.revision() <= fromRevision) {
                continue;
            }
            if (delta.revision() != expectedRevision) {
                return DeltaSnapshot.unavailable(fromRevision, revision);
            }
            if (processedChanges >= boundedMaximum) {
                break;
            }
            latest.put(delta.packedPosition(), delta.cell());
            processedChanges++;
            expectedRevision++;
        }
        if (processedChanges <= 0) {
            return DeltaSnapshot.unavailable(fromRevision, revision);
        }

        long toRevision = fromRevision + processedChanges;
        Map<Integer, WaterCell> upserts = new HashMap<>();
        int tombstoneCount = 0;
        for (Map.Entry<Integer, WaterCell> entry : latest.entrySet()) {
            if (entry.getValue() == null) {
                tombstoneCount++;
            } else {
                upserts.put(entry.getKey(), entry.getValue());
            }
        }
        int[] tombstones = new int[tombstoneCount];
        int tombstoneIndex = 0;
        for (Map.Entry<Integer, WaterCell> entry : latest.entrySet()) {
            if (entry.getValue() == null) {
                tombstones[tombstoneIndex++] = entry.getKey();
            }
        }
        Arrays.sort(tombstones);
        return new DeltaSnapshot(
                true,
                fromRevision,
                toRevision,
                processedChanges,
                encodeCells(upserts),
                tombstones,
                toRevision == revision
        );
    }

    /** Merges a validated network delta into one complete sparse snapshot array. */
    public static int[] mergeNetworkDelta(int[] baseline, int[] upserts, int[] tombstones) {
        Map<Integer, WaterCell> merged = new TreeMap<>(Integer::compareUnsigned);
        merged.putAll(decodeCellData(baseline, MAX_PERSISTED_CELLS));
        Map<Integer, WaterCell> decodedUpserts = decodeCellData(upserts, MAX_DELTA_HISTORY);
        int[] safeTombstones = tombstones == null ? new int[0] : tombstones.clone();
        if (safeTombstones.length > MAX_DELTA_HISTORY) {
            throw new IllegalArgumentException("Canonical water delta exceeds tombstone limit");
        }
        Set<Integer> uniqueTombstones = new HashSet<>(safeTombstones.length);
        for (int packedPosition : safeTombstones) {
            validatePackedPosition(packedPosition);
            if (!uniqueTombstones.add(packedPosition)) {
                throw new IllegalArgumentException("Duplicate canonical water tombstone " + packedPosition);
            }
            merged.remove(packedPosition);
        }
        merged.putAll(decodedUpserts);
        return encodeCells(merged);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(FORMAT_KEY, FORMAT_VERSION);
        tag.putInt(CELL_COUNT_KEY, cells.size());
        tag.putLong(REVISION_KEY, revision);
        tag.putIntArray(CELL_DATA_KEY, encodeCells());
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        int format = tag.contains(FORMAT_KEY, Tag.TAG_INT) ? tag.getInt(FORMAT_KEY) : 0;
        if (format < 0 || format > FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported canonical water format " + format);
        }
        int[] encodedCells = tag.getIntArray(CELL_DATA_KEY);
        Map<Integer, WaterCell> decoded = decodeCellData(encodedCells, MAX_PERSISTED_CELLS);
        if (format > 0) {
            if (!tag.contains(CELL_COUNT_KEY, Tag.TAG_INT)
                    || tag.getInt(CELL_COUNT_KEY) != decoded.size()) {
                throw new IllegalArgumentException("Canonical water cell count does not match payload");
            }
        }
        cells.clear();
        cells.putAll(decoded);
        revision = Math.max(0L, tag.getLong(REVISION_KEY));
        deltaHistory.clear();
        dirty = false;
    }

    private void setPacked(int packedPosition, WaterCell cell, boolean notify) {
        validatePackedPosition(packedPosition);
        WaterCell sanitized = cell == null ? WaterCell.EMPTY : cell.sanitized();
        WaterCell previous;
        boolean retainDryOverride = sanitized.volumeUnits == 0
                && (sanitized.flags & FLAG_DRY_OVERRIDE) != 0;
        if (sanitized.volumeUnits == 0 && !retainDryOverride) {
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
            recordDelta(packedPosition, retainDryOverride || sanitized.volumeUnits > 0 ? sanitized : null);
            dirty = true;
            dirtyListener.run();
        }
    }

    private int[] encodeCells() {
        return encodeCells(cells);
    }

    private static int[] encodeCells(Map<Integer, WaterCell> source) {
        List<Map.Entry<Integer, WaterCell>> entries = new ArrayList<>(source.entrySet());
        entries.sort((left, right) -> Integer.compareUnsigned(left.getKey(), right.getKey()));
        int cellCount = entries.size();
        int[] data = new int[cellCount * SERIALIZED_CELL_STRIDE];
        int index = 0;
        for (Map.Entry<Integer, WaterCell> entry : entries) {
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

    private static Map<Integer, WaterCell> decodeCellData(int[] source, int maximumCells) {
        int[] data = source == null ? new int[0] : source;
        if (data.length % SERIALIZED_CELL_STRIDE != 0) {
            throw new IllegalArgumentException("Canonical water payload has a trailing partial cell");
        }
        int cellCount = data.length / SERIALIZED_CELL_STRIDE;
        if (cellCount > maximumCells) {
            throw new IllegalArgumentException("Canonical water payload exceeds " + maximumCells + " cells");
        }

        Map<Integer, WaterCell> decoded = new HashMap<>(Math.max(16, cellCount * 4 / 3));
        for (int index = 0; index < cellCount; index++) {
            int offset = index * SERIALIZED_CELL_STRIDE;
            int packedPosition = data[offset];
            validatePackedPosition(packedPosition);
            int volumeUnits = data[offset + 1];
            float velocityX = Float.intBitsToFloat(data[offset + 2]);
            float velocityY = Float.intBitsToFloat(data[offset + 3]);
            float velocityZ = Float.intBitsToFloat(data[offset + 4]);
            int temperatureMilliKelvin = data[offset + 6];
            if (volumeUnits < 0 || volumeUnits > UNITS_PER_BLOCK
                    || !Float.isFinite(velocityX)
                    || !Float.isFinite(velocityY)
                    || !Float.isFinite(velocityZ)
                    || temperatureMilliKelvin < 0) {
                throw new IllegalArgumentException("Invalid canonical water cell at packed position "
                        + packedPosition);
            }
            WaterCell previous = decoded.put(packedPosition, new WaterCell(
                    data[offset + 1],
                    velocityX,
                    velocityY,
                    velocityZ,
                    data[offset + 5],
                    temperatureMilliKelvin
            ));
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate canonical water cell " + packedPosition);
            }
        }
        return decoded;
    }

    private static void validatePackedPosition(int packedPosition) {
        if ((packedPosition & ~PACKED_POSITION_MASK) != 0) {
            throw new IllegalArgumentException("Invalid canonical water packed position " + packedPosition);
        }
    }

    private void recordDelta(int packedPosition, WaterCell cell) {
        deltaHistory.addLast(new CellDelta(revision, packedPosition, cell));
        while (deltaHistory.size() > MAX_DELTA_HISTORY) {
            deltaHistory.removeFirst();
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

        /** Returns whether this water is hosted by another block, such as kelp or a waterlogged fence. */
        public boolean hostedWater() {
            return (flags & FLAG_HOSTED_WATER) != 0;
        }

        /** Returns whether this detailed local cell is asleep until neighboring water changes. */
        public boolean sleeping() {
            return (flags & FLAG_SLEEPING) != 0;
        }

        /** Returns whether solid displacement has temporarily hidden this conserved volume. */
        public boolean displacementReservoir() {
            return (flags & FLAG_DISPLACEMENT_RESERVOIR) != 0;
        }

        /** Returns whether recession may consider this exact canonical cell. */
        public boolean temporaryFlood() {
            return (flags & FLAG_TEMPORARY_FLOOD) != 0;
        }

        /** Returns a copy with additional provenance flags preserved through synchronization. */
        public WaterCell withAddedFlags(int addedFlags) {
            if ((flags & addedFlags) == addedFlags) {
                return this;
            }
            return new WaterCell(volumeUnits, velocityX, velocityY, velocityZ,
                    flags | addedFlags, temperatureMilliKelvin).sanitized();
        }

        /** Returns a copy with selected runtime flags cleared. */
        public WaterCell withoutFlags(int removedFlags) {
            if ((flags & removedFlags) == 0) {
                return this;
            }
            return new WaterCell(volumeUnits, velocityX, velocityY, velocityZ,
                    flags & ~removedFlags, temperatureMilliKelvin).sanitized();
        }

        private static float finiteOrZero(float value) {
            return Float.isFinite(value) ? value : 0.0f;
        }
    }

    /** Packed entry used by simulation and networking without exposing mutable maps. */
    public record CellEntry(int packedPosition, WaterCell cell) {
    }

    private record CellDelta(long revision, int packedPosition, WaterCell cell) {
    }

    /** Contiguous sparse changes suitable for one bounded network delta. */
    public record DeltaSnapshot(
            boolean available,
            long fromRevision,
            long toRevision,
            int changeCount,
            int[] upsertData,
            int[] tombstones,
            boolean caughtUp
    ) {
        public DeltaSnapshot {
            upsertData = upsertData == null ? new int[0] : upsertData.clone();
            tombstones = tombstones == null ? new int[0] : tombstones.clone();
        }

        @Override
        public int[] upsertData() {
            return upsertData.clone();
        }

        @Override
        public int[] tombstones() {
            return tombstones.clone();
        }

        private static DeltaSnapshot unavailable(long fromRevision, long currentRevision) {
            return new DeltaSnapshot(false, fromRevision, currentRevision, 0,
                    new int[0], new int[0], false);
        }
    }
}
