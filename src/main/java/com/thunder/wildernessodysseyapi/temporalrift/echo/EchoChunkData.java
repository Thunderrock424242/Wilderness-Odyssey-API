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
import java.util.Map;

/** One protection bit and at most sixteen observable material remnants per chunk. No inventory data. */
public final class EchoChunkData implements INBTSerializable<CompoundTag> {
    public static final int MAX_MARKERS = 16;
    private boolean playerModified;
    private final Map<BlockPos, ResourceLocation> markers = new LinkedHashMap<>();
    private Runnable dirty = () -> { };

    /** The owning chunk supplies its ordinary save-dirty callback. */
    public void setDirtyListener(Runnable dirty) { this.dirty = dirty; }

    /** Protects all future player edits in this chunk from incoming reality echoes. */
    public void markPlayerEdited(BlockPos position) {
        playerModified = true;
        markers.remove(position);
        dirty.run();
    }

    public boolean playerModified() { return playerModified; }

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
        tag.putBoolean("player_modified", playerModified);
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
        playerModified = tag.getBoolean("player_modified");
        markers.clear();
        ListTag list = tag.getList("remnants", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(MAX_MARKERS, list.size()); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation block = ResourceLocation.tryParse(entry.getString("block"));
            if (block != null) markers.put(BlockPos.of(entry.getLong("position")), block);
        }
    }
}
