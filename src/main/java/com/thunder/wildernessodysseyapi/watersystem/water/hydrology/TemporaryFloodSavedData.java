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
 * Persists exact positions owned by localized temporary flooding.
 *
 * <p>This ledger never implies that water exists by itself. A position is
 * removable only while both this entry and the canonical temporary-flood flag
 * still exist on the matching namespaced projection. That two-key rule is what
 * protects permanent, player-placed, and other-mod water during recession.</p>
 */
public final class TemporaryFloodSavedData extends SavedData {

    private static final String DATA_NAME = ModConstants.MOD_ID + "_temporary_floodwater";
    private static final int DATA_VERSION = 2;
    private static final int HARD_MAX_ENTRIES = 65_536;
    private static final String VERSION_KEY = "version";
    private static final String POSITIONS = "positions";
    private static final String BASINS = "basins";
    private static final String PLACED_TICKS = "placed_ticks";
    private static final String ORIGINAL_STATES = "original_states";

    private final LinkedHashMap<Long, FloodEntry> entries = new LinkedHashMap<>();
    private final Map<Long, Integer> chunkCounts = new HashMap<>();

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
            return data;
        }
        long[] positions = tag.getLongArray(POSITIONS);
        long[] basins = tag.getLongArray(BASINS);
        long[] placedTicks = tag.getLongArray(PLACED_TICKS);
        ListTag originals = tag.getList(ORIGINAL_STATES, Tag.TAG_COMPOUND);
        int count = Math.min(positions.length, Math.min(basins.length, placedTicks.length));
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
            data.put(positions[index], new FloodEntry(
                    basins[index],
                    Math.max(0L, placedTicks[index]),
                    original
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
        int index = 0;
        for (Map.Entry<Long, FloodEntry> entry : entries.entrySet()) {
            positions[index] = entry.getKey();
            basins[index] = entry.getValue().basinId;
            placedTicks[index] = entry.getValue().placedTick;
            originals.add(entry.getValue().originalState == null
                    ? new CompoundTag()
                    : NbtUtils.writeBlockState(entry.getValue().originalState));
            index++;
        }
        tag.putInt(VERSION_KEY, DATA_VERSION);
        tag.putLongArray(POSITIONS, positions);
        tag.putLongArray(BASINS, basins);
        tag.putLongArray(PLACED_TICKS, placedTicks);
        tag.put(ORIGINAL_STATES, originals);
        return tag;
    }

    /** Records one position only after canonical flood placement succeeds. */
    public boolean record(BlockPos position, long basinId, long gameTime, int maximumEntries) {
        return record(
                position,
                basinId,
                gameTime,
                maximumEntries,
                null
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
        if (position == null
                || entries.containsKey(position.asLong())
                || entries.size() >= Math.max(1, maximumEntries)) {
            return false;
        }
        put(position.asLong(), new FloodEntry(
                basinId,
                Math.max(0L, gameTime),
                originalState
        ));
        setDirty();
        return true;
    }

    /** Forgets an entry without touching world or canonical water state. */
    public boolean forget(long packedPosition) {
        FloodEntry removed = entries.remove(packedPosition);
        if (removed == null) {
            return false;
        }
        decrementChunkCount(chunkKey(packedPosition));
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

    /** Returns the total exact flood ledger size. */
    public int size() {
        return entries.size();
    }

    /** Returns the saved original block state, or null for air/legacy entries. */
    public BlockState originalState(long packedPosition) {
        FloodEntry entry = entries.get(packedPosition);
        return entry == null ? null : entry.originalState;
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
        entries.put(position, entry);
        chunkCounts.merge(chunkKey(position), 1, Integer::sum);
    }

    private void decrementChunkCount(long chunkKey) {
        chunkCounts.computeIfPresent(chunkKey, (ignored, count) -> count <= 1 ? null : count - 1);
    }

    private static long chunkKey(long packedPosition) {
        BlockPos position = BlockPos.of(packedPosition);
        return ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4);
    }

    private record FloodEntry(long basinId, long placedTick, BlockState originalState) {
    }
}
