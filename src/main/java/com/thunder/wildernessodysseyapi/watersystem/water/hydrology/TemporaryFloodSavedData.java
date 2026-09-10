package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists exact positions owned by reversible watershed surface water.
 *
 * <p>This ledger never implies that water exists by itself. A position is
 * removable only while both this entry and the canonical temporary-flood flag
 * still exist on the matching namespaced projection. That two-key rule is what
 * protects permanent, player-placed, and other-mod water during recession.</p>
 */
public final class TemporaryFloodSavedData extends SavedData {

    private static final String DATA_NAME = ModConstants.MOD_ID + "_temporary_floodwater";
    private static final int DATA_VERSION = 5;
    /** Legacy claims have no regional debit; their return is an explicit legacy boundary input. */
    public static final long LEGACY_FUNDING = Long.MIN_VALUE;
    private static final int HARD_MAX_ENTRIES = 65_536;
    private static final String VERSION_KEY = "version";
    private static final String POSITIONS = "positions";
    private static final String BASINS = "basins";
    private static final String PLACED_TICKS = "placed_ticks";
    private static final String ORIGINAL_STATES = "original_states";
    private static final String KINDS = "kinds";
    private static final String OWNED_UNITS = "owned_units";

    private final LinkedHashMap<Long, FloodEntry> entries = new LinkedHashMap<>();
    private final Map<Long, Integer> chunkCounts = new HashMap<>();
    private final Map<Long, int[]> chunkKindCounts = new HashMap<>();

    /** Returns the dimension-owned exact temporary-flood ledger. */
    public static TemporaryFloodSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(TemporaryFloodSavedData::new, TemporaryFloodSavedData::load),
                DATA_NAME
        );
    }

    static TemporaryFloodSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TemporaryFloodSavedData data = new TemporaryFloodSavedData();
        int version = tag == null ? 0 : tag.getInt(VERSION_KEY);
        if (version < 1 || version > DATA_VERSION) {
            throw new IllegalArgumentException("Unsupported temporary water ledger version " + version);
        }
        long[] positions = tag.getLongArray(POSITIONS);
        long[] basins = tag.getLongArray(BASINS);
        long[] placedTicks = tag.getLongArray(PLACED_TICKS);
        ListTag originals = tag.getList(ORIGINAL_STATES, Tag.TAG_COMPOUND);
        byte[] kinds = tag.getByteArray(KINDS);
        int[] ownedUnits = tag.getIntArray(OWNED_UNITS);
        long[] funding = tag.getLongArray("funding_regions");
        int count = Math.min(positions.length, Math.min(basins.length, placedTicks.length));
        if (count != positions.length || count != basins.length || count != placedTicks.length
                || count > HARD_MAX_ENTRIES || version >= 4 && ownedUnits.length != count
                || version >= 5 && funding.length != count) {
            throw new IllegalArgumentException("Truncated or oversized temporary water ledger");
        }
        for (int index = 0; index < count && data.entries.size() < HARD_MAX_ENTRIES; index++) {
            CompoundTag originalTag = version >= 2 && index < originals.size()
                    ? originals.getCompound(index)
                    : new CompoundTag();
            BlockState original = !originalTag.isEmpty() && registries != null
                    ? NbtUtils.readBlockState(
                    registries.lookupOrThrow(Registries.BLOCK),
                    originalTag
            )
                    : null;
            SurfaceWaterKind migratedKind = version >= 3 && index < kinds.length
                    ? SurfaceWaterKind.fromId(Byte.toUnsignedInt(kinds[index]))
                    : SurfaceWaterKind.FLOOD;
            int migratedOwnedUnits = version >= 4 && index < ownedUnits.length
                    ? ownedUnits[index]
                    : migratedKind == SurfaceWaterKind.WETLAND
                    ? WaterVolumeChunk.UNITS_PER_BLOCK / 2
                    : WaterVolumeChunk.UNITS_PER_BLOCK;
            if (migratedOwnedUnits < 0 || migratedOwnedUnits > WaterVolumeChunk.UNITS_PER_BLOCK
                    || data.entries.containsKey(positions[index])) {
                throw new IllegalArgumentException("Invalid or duplicate temporary water claim");
            }
            data.put(positions[index], new FloodEntry(
                    basins[index],
                    Math.max(0L, placedTicks[index]),
                    original,
                    migratedKind,
                    clampOwnedUnits(migratedOwnedUnits),
                    version >= 5 && index < funding.length ? funding[index] : LEGACY_FUNDING
            ));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] positions = new long[entries.size()];
        long[] basins = new long[entries.size()];
        long[] placedTicks = new long[entries.size()];
        ListTag originals = new ListTag();
        byte[] kinds = new byte[entries.size()];
        int[] ownedUnits = new int[entries.size()];
        long[] funding = new long[entries.size()];
        int index = 0;
        for (Map.Entry<Long, FloodEntry> entry : entries.entrySet()) {
            positions[index] = entry.getKey();
            basins[index] = entry.getValue().basinId;
            placedTicks[index] = entry.getValue().placedTick;
            originals.add(entry.getValue().originalState == null
                    ? new CompoundTag()
                    : NbtUtils.writeBlockState(entry.getValue().originalState));
            kinds[index] = (byte) entry.getValue().kind.ordinal();
            ownedUnits[index] = entry.getValue().ownedUnits;
            funding[index] = entry.getValue().fundingRegion;
            index++;
        }
        tag.putInt(VERSION_KEY, DATA_VERSION);
        tag.putLongArray(POSITIONS, positions);
        tag.putLongArray(BASINS, basins);
        tag.putLongArray(PLACED_TICKS, placedTicks);
        tag.put(ORIGINAL_STATES, originals);
        tag.putByteArray(KINDS, kinds);
        tag.putIntArray(OWNED_UNITS, ownedUnits);
        tag.putLongArray("funding_regions", funding);
        return tag;
    }

    /** Records one position only after canonical flood placement succeeds. */
    public boolean record(BlockPos position, long basinId, long gameTime, int maximumEntries) {
        return record(
                position,
                basinId,
                gameTime,
                maximumEntries,
                null,
                SurfaceWaterKind.FLOOD,
                WaterVolumeChunk.UNITS_PER_BLOCK
        );
    }

    /** Records the exact original replaceable state for reversible recession. */
    public boolean record(
            BlockPos position,
            long basinId,
            long gameTime,
            int maximumEntries,
            BlockState originalState
    ) {
        return record(
                position,
                basinId,
                gameTime,
                maximumEntries,
                originalState,
                SurfaceWaterKind.FLOOD,
                WaterVolumeChunk.UNITS_PER_BLOCK
        );
    }

    /** Records one exact reversible surface-water cell and its ownership kind. */
    public boolean record(
            BlockPos position,
            long basinId,
            long gameTime,
            int maximumEntries,
            BlockState originalState,
            SurfaceWaterKind kind
    ) {
        return record(position, basinId, gameTime, maximumEntries, originalState, kind,
                kind == SurfaceWaterKind.WETLAND ? WaterVolumeChunk.UNITS_PER_BLOCK / 2 : WaterVolumeChunk.UNITS_PER_BLOCK);
    }

    /** Records one exact reversible surface-water claim and its owned unit count. */
    public boolean record(
            BlockPos position,
            long basinId,
            long gameTime,
            int maximumEntries,
            BlockState originalState,
            SurfaceWaterKind kind,
            int ownedUnits
    ) {
        return record(position, basinId, gameTime, maximumEntries, originalState, kind,
                ownedUnits, LEGACY_FUNDING);
    }

    /** Records the reservoir source separately from the displayed basin identity. */
    public boolean record(BlockPos position, long basinId, long gameTime, int maximumEntries,
                          BlockState originalState, SurfaceWaterKind kind, int ownedUnits,
                          long fundingRegion) {
        if (position == null
                || entries.containsKey(position.asLong())
                || ownedUnits <= 0 || ownedUnits > WaterVolumeChunk.UNITS_PER_BLOCK
                || entries.size() >= Math.max(1, Math.min(HARD_MAX_ENTRIES, maximumEntries))) {
            return false;
        }
        put(position.asLong(), new FloodEntry(
                basinId,
                Math.max(0L, gameTime),
                originalState,
                kind == null || kind == SurfaceWaterKind.NONE
                        ? SurfaceWaterKind.FLOOD
                        : kind,
                clampOwnedUnits(ownedUnits), fundingRegion
        ));
        setDirty();
        return true;
    }

    /**
     * Moves exact ownership only after the destination has accepted canonical volume.
     *
     * <p>The caller rolls back destination writes if this transaction is rejected.</p>
     */
    public boolean transferOwnedUnits(
            BlockPos source,
            BlockPos target,
            int transferredUnits,
            long gameTime,
            int maximumEntries,
            BlockState targetOriginalState
    ) {
        if (source == null || target == null || source.equals(target) || transferredUnits <= 0) {
            return false;
        }
        FloodEntry sourceEntry = entries.get(source.asLong());
        if (sourceEntry == null || sourceEntry.ownedUnits < transferredUnits) {
            return false;
        }
        FloodEntry targetEntry = entries.get(target.asLong());
        if (targetEntry == null) {
            if (entries.size() >= Math.max(1, Math.min(HARD_MAX_ENTRIES, maximumEntries))) {
                return false;
            }
            targetEntry = new FloodEntry(
                    sourceEntry.basinId,
                    Math.max(0L, gameTime),
                    targetOriginalState,
                    sourceEntry.kind,
                    0, sourceEntry.fundingRegion
            );
            put(target.asLong(), targetEntry);
        } else if (targetEntry.basinId != sourceEntry.basinId
                || targetEntry.fundingRegion != sourceEntry.fundingRegion
                || targetEntry.kind != sourceEntry.kind
                || targetEntry.ownedUnits > WaterVolumeChunk.UNITS_PER_BLOCK - transferredUnits) {
            return false;
        }
        sourceEntry.ownedUnits -= transferredUnits;
        targetEntry.ownedUnits += transferredUnits;
        setDirty();
        return true;
    }

    /** Returns the exact canonical units claimed at one position. */
    public int ownedUnits(long packedPosition) {
        FloodEntry entry = entries.get(packedPosition);
        return entry == null ? 0 : entry.ownedUnits;
    }

    /** Releases an externally withdrawn claim without crediting water back to a reservoir. */
    public int releaseOwnedUnits(BlockPos position, int requested) {
        FloodEntry entry = entries.get(position.asLong());
        if (entry == null) return 0;
        int released = Math.min(entry.ownedUnits, Math.max(0, requested));
        entry.ownedUnits -= released;
        if (released > 0) setDirty();
        return released;
    }

    public long fundingRegion(long position) {
        FloodEntry entry = entries.get(position);
        return entry == null ? LEGACY_FUNDING : entry.fundingRegion;
    }

    /** Exact detailed inventory is a memorandum, never also stored in regional reservoirs. */
    public long ownedMilliUnits(long fundingRegion) {
        long total = 0;
        for (FloodEntry entry : entries.values()) {
            if (entry.fundingRegion == fundingRegion) total += entry.ownedUnits * 1000L;
        }
        return total;
    }

    /** Forgets an entry without touching world or canonical water state. */
    public boolean forget(long packedPosition) {
        FloodEntry removed = entries.remove(packedPosition);
        if (removed == null) {
            return false;
        }
        decrementChunkCount(chunkKey(packedPosition), removed.kind);
        setDirty();
        return true;
    }

    /** Returns a fair bounded candidate window and rotates it to the back. */
    public List<Long> recessionCandidates(int maximumCandidates) {
        int maximum = Math.max(0, Math.min(entries.size(), maximumCandidates));
        if (maximum == 0) {
            return List.of();
        }
        List<Long> result = new ArrayList<>(maximum);
        Iterator<Map.Entry<Long, FloodEntry>> iterator = entries.entrySet().iterator();
        List<Map.Entry<Long, FloodEntry>> rotated = new ArrayList<>(maximum);
        while (iterator.hasNext() && result.size() < maximum) {
            Map.Entry<Long, FloodEntry> entry = iterator.next();
            result.add(entry.getKey());
            rotated.add(Map.entry(entry.getKey(), entry.getValue()));
            iterator.remove();
        }
        for (Map.Entry<Long, FloodEntry> entry : rotated) {
            entries.put(entry.getKey(), entry.getValue());
        }
        return List.copyOf(result);
    }

    /** Returns exact temporary-flood positions currently tracked in a chunk. */
    public int countInChunk(long chunkKey) {
        return chunkCounts.getOrDefault(chunkKey, 0);
    }

    /** Returns the exact tracked count for one ownership kind in a chunk. */
    public int countInChunk(long chunkKey, SurfaceWaterKind kind) {
        if (kind == null) {
            return 0;
        }
        int[] counts = chunkKindCounts.get(chunkKey);
        return counts == null ? 0 : counts[kind.ordinal()];
    }

    /** Returns all pond, wetland, and spring cells tracked in a chunk. */
    public int standingWaterCountInChunk(long chunkKey) {
        int[] counts = chunkKindCounts.get(chunkKey);
        if (counts == null) {
            return 0;
        }
        return counts[SurfaceWaterKind.RAIN_POND.ordinal()]
                + counts[SurfaceWaterKind.WETLAND.ordinal()]
                + counts[SurfaceWaterKind.SPRING.ordinal()];
    }

    /** Returns the strongest synchronized standing-water classification in a chunk. */
    public SurfaceWaterKind dominantStandingKind(long chunkKey) {
        int[] counts = chunkKindCounts.get(chunkKey);
        if (counts == null) {
            return SurfaceWaterKind.NONE;
        }
        if (counts[SurfaceWaterKind.SPRING.ordinal()] > 0) {
            return SurfaceWaterKind.SPRING;
        }
        if (counts[SurfaceWaterKind.RAIN_POND.ordinal()] > 0) {
            return SurfaceWaterKind.RAIN_POND;
        }
        return counts[SurfaceWaterKind.WETLAND.ordinal()] > 0
                ? SurfaceWaterKind.WETLAND
                : SurfaceWaterKind.NONE;
    }

    /** Returns the total exact flood ledger size. */
    public int size() {
        return entries.size();
    }

    /** Returns the saved original block state, or null for air/legacy entries. */
    public BlockState originalState(long packedPosition) {
        FloodEntry entry = entries.get(packedPosition);
        return entry == null ? null : entry.originalState;
    }

    /** Returns the ownership category for one tracked position. */
    public SurfaceWaterKind kind(long packedPosition) {
        FloodEntry entry = entries.get(packedPosition);
        return entry == null ? SurfaceWaterKind.NONE : entry.kind;
    }

    /** Returns the server tick on which one tracked position was placed. */
    public long placedTick(long packedPosition) {
        FloodEntry entry = entries.get(packedPosition);
        return entry == null ? 0L : entry.placedTick;
    }

    /** Returns the standing-water kind at an exact position, if one is tracked. */
    public SurfaceWaterKind standingKindAt(BlockPos position) {
        if (position == null) {
            return SurfaceWaterKind.NONE;
        }
        SurfaceWaterKind kind = kind(position.asLong());
        return kind.standingWater() ? kind : SurfaceWaterKind.NONE;
    }

    /** Pure recession gate used by runtime code and preservation tests. */
    public static boolean mayRemoveTrackedCell(
            boolean ledgerTracked,
            int canonicalFlags,
            boolean matchingWildernessProjection
    ) {
        return ledgerTracked
                && (canonicalFlags & WaterVolumeChunk.FLAG_TEMPORARY_FLOOD) != 0
                && matchingWildernessProjection;
    }

    private void put(long position, FloodEntry entry) {
        FloodEntry previous = entries.get(position);
        if (previous != null) decrementChunkCount(chunkKey(position), previous.kind);
        entries.put(position, entry);
        long chunkKey = chunkKey(position);
        chunkCounts.merge(chunkKey, 1, Integer::sum);
        int[] counts = chunkKindCounts.computeIfAbsent(
                chunkKey,
                ignored -> new int[SurfaceWaterKind.values().length]
        );
        counts[entry.kind.ordinal()]++;
    }

    private void decrementChunkCount(long chunkKey, SurfaceWaterKind kind) {
        chunkCounts.computeIfPresent(chunkKey, (ignored, count) -> count <= 1 ? null : count - 1);
        chunkKindCounts.computeIfPresent(chunkKey, (ignored, counts) -> {
            int index = kind == null ? SurfaceWaterKind.FLOOD.ordinal() : kind.ordinal();
            counts[index] = Math.max(0, counts[index] - 1);
            for (int count : counts) {
                if (count > 0) {
                    return counts;
                }
            }
            return null;
        });
    }

    private static long chunkKey(long packedPosition) {
        BlockPos position = BlockPos.of(packedPosition);
        return ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4);
    }

    private static int clampOwnedUnits(int units) {
        return Math.max(0, Math.min(WaterVolumeChunk.UNITS_PER_BLOCK, units));
    }

    private static final class FloodEntry {
        private final long basinId;
        private final long placedTick;
        private final BlockState originalState;
        private final SurfaceWaterKind kind;
        private int ownedUnits;
        private final long fundingRegion;

        private FloodEntry(long basinId, long placedTick, BlockState originalState,
                           SurfaceWaterKind kind, int ownedUnits, long fundingRegion) {
            this.basinId = basinId;
            this.placedTick = placedTick;
            this.originalState = originalState;
            this.kind = kind;
            this.ownedUnits = clampOwnedUnits(ownedUnits);
            this.fundingRegion = fundingRegion;
        }
    }
}
