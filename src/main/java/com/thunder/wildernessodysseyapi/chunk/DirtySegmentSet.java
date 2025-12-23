package com.thunder.wildernessodysseyapi.chunk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tracks which portions of a chunk payload have changed since the last persisted snapshot.
 *
 * The set distinguishes between top-level data segments and per-section changes so downstream
 * serializers can copy only the affected data during a save batch.
 */
final class DirtySegmentSet {
    private final Set<String> dirtyKeys;
    private final Set<Integer> dirtySectionY;
    private final boolean fullChunkDirty;

    private DirtySegmentSet(Set<String> dirtyKeys, Set<Integer> dirtySectionY, boolean fullChunkDirty) {
        this.dirtyKeys = dirtyKeys;
        this.dirtySectionY = dirtySectionY;
        this.fullChunkDirty = fullChunkDirty;
    }

    static DirtySegmentSet fullChunk() {
        return new DirtySegmentSet(Set.of(), Set.of(), true);
    }

    static DirtySegmentSet none() {
        return new DirtySegmentSet(Set.of(), Set.of(), false);
    }

    static DirtySegmentSet diff(CompoundTag baseline, CompoundTag updated) {
        if (baseline == null) {
            return fullChunk();
        }

        Set<String> dirtyKeys = new HashSet<>();
        Set<Integer> dirtySections = new HashSet<>();

        for (String key : updated.getAllKeys()) {
            if ("sections".equals(key)) {
                continue;
            }
            if (!baseline.contains(key) || !baseline.get(key).equals(updated.get(key))) {
                dirtyKeys.add(key);
            }
        }

        for (String key : baseline.getAllKeys()) {
            if ("sections".equals(key)) {
                continue;
            }
            if (!updated.contains(key)) {
                dirtyKeys.add(key);
            }
        }

        Map<Integer, CompoundTag> baselineSections = indexSections(baseline);
        Map<Integer, CompoundTag> updatedSections = indexSections(updated);

        for (Map.Entry<Integer, CompoundTag> entry : updatedSections.entrySet()) {
            CompoundTag baselineSection = baselineSections.get(entry.getKey());
            if (baselineSection == null || !baselineSection.equals(entry.getValue())) {
                dirtySections.add(entry.getKey());
            }
        }

        for (Integer baselineY : baselineSections.keySet()) {
            if (!updatedSections.containsKey(baselineY)) {
                dirtySections.add(baselineY);
            }
        }

        if (dirtyKeys.isEmpty() && dirtySections.isEmpty()) {
            return none();
        }

        return new DirtySegmentSet(Collections.unmodifiableSet(dirtyKeys), Collections.unmodifiableSet(dirtySections), false);
    }

    DirtySegmentSet mergedWith(DirtySegmentSet other) {
        if (fullChunkDirty || other.fullChunkDirty) {
            return fullChunk();
        }

        Set<String> mergedKeys = new HashSet<>(dirtyKeys);
        mergedKeys.addAll(other.dirtyKeys);

        Set<Integer> mergedSections = new HashSet<>(dirtySectionY);
        mergedSections.addAll(other.dirtySectionY);

        if (mergedKeys.isEmpty() && mergedSections.isEmpty()) {
            return none();
        }
        return new DirtySegmentSet(Collections.unmodifiableSet(mergedKeys), Collections.unmodifiableSet(mergedSections), false);
    }

    boolean hasEntries() {
        return fullChunkDirty || !dirtyKeys.isEmpty() || !dirtySectionY.isEmpty();
    }

    boolean isFullChunkDirty() {
        return fullChunkDirty;
    }

    Set<String> topLevelKeys() {
        return dirtyKeys;
    }

    Set<Integer> sectionYLevels() {
        return dirtySectionY;
    }

    static Map<Integer, CompoundTag> indexSections(CompoundTag tag) {
        if (!tag.contains("sections", Tag.TAG_LIST)) {
            return Map.of();
        }
        ListTag list = tag.getList("sections", Tag.TAG_COMPOUND);
        Map<Integer, CompoundTag> indexed = new HashMap<>();
        for (Tag element : list) {
            if (!(element instanceof CompoundTag section)) {
                continue;
            }
            if (!section.contains("Y")) {
                continue;
            }
            indexed.put((int) section.getByte("Y"), section);
        }
        return indexed;
    }
}
