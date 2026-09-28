package com.thunder.wildernessodysseyapi.temporalrift.echo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.INBTSerializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Sparse player-edit protection plus at most sixteen observable material remnants per chunk. No inventory data. */
public final class EchoChunkData implements INBTSerializable<CompoundTag> {
    public static final int MAX_MARKERS = 16;
    public static final int MAX_PROTECTED_POSITIONS = 2048;
    /** Old saves only stored a chunk-wide flag. Never discard that existing protection. */
    private boolean legacyChunkProtection;
    private final Set<BlockPos> protectedPositions = new LinkedHashSet<>();
    private final Map<BlockPos, ResourceLocation> markers = new LinkedHashMap<>();
    private Runnable dirty = () -> { };

    /** The owning chunk supplies its ordinary save-dirty callback. */
    public void setDirtyListener(Runnable dirty) { this.dirty = dirty; }

    /** Protects individual edited blocks, preserving legacy all-chunk protection if present. */
    public void markPlayerEdited(BlockPos position) {
        boolean changed = markers.remove(position) != null;
        if (!legacyChunkProtection && !protectedPositions.contains(position)) {
            if (protectedPositions.size() >= MAX_PROTECTED_POSITIONS) {
                // Preserve all existing edits when sparse protection reaches its safety limit.
                protectedPositions.clear();
                legacyChunkProtection = true;
            } else {
                protectedPositions.add(position.immutable());
            }
            changed = true;
        }
        if (changed) dirty.run();
    }

    /** True when this chunk has any player edits (including legacy saves). */
    public boolean playerModified() { return legacyChunkProtection || !protectedPositions.isEmpty(); }

    /** Legacy saves protect whole chunks; new saves protect only recorded positions. */
    public boolean protects(BlockPos position) {
        return legacyChunkProtection || protectedPositions.contains(position);
    }

    /** Stores evidence of an actual applied remnant so later explorers can discover it. */
    public void record(BlockPos position, ResourceLocation blockId) {
        if (!markers.containsKey(position) && markers.size() >= MAX_MARKERS) {
            markers.remove(markers.keySet().iterator().next());
        }
        markers.put(position.immutable(), blockId);
        dirty.run();
    }

    /** Loaded-chunk evidence only; readers still verify the block and visibility before granting discovery. */
    public Map<BlockPos, ResourceLocation> markers() { return Collections.unmodifiableMap(markers); }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("player_modified", legacyChunkProtection);
        tag.putLongArray("protected_positions", protectedPositions.stream().mapToLong(BlockPos::asLong).toArray());
        ListTag list = new ListTag();
        for (var marker : markers.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("position", marker.getKey().asLong());
            entry.putString("block", marker.getValue().toString());
            list.add(entry);
        }
        tag.put("remnants", list);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        legacyChunkProtection = tag.getBoolean("player_modified");
        protectedPositions.clear();
        if (!legacyChunkProtection) {
            long[] savedPositions = tag.getLongArray("protected_positions");
            if (savedPositions.length > MAX_PROTECTED_POSITIONS) {
                // Malformed or future oversized data fails safe rather than exposing player builds.
                legacyChunkProtection = true;
            } else {
                for (long position : savedPositions) protectedPositions.add(BlockPos.of(position));
            }
        }
        markers.clear();
        ListTag list = tag.getList("remnants", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(MAX_MARKERS, list.size()); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation block = ResourceLocation.tryParse(entry.getString("block"));
            if (block != null) markers.put(BlockPos.of(entry.getLong("position")), block);
        }
    }
}
