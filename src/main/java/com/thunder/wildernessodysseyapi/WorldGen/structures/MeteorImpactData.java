package com.thunder.wildernessodysseyapi.WorldGen.structures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores the location of the meteor impact zone so other systems
 * can reference it (e.g. player spawn logic).
 */
public class MeteorImpactData extends SavedData {
    private static final String DATA_NAME = "wo_meteor_location";
    private static final String VERSION_KEY = "version";
    private static final int CURRENT_VERSION = 2;
    private final List<Long> impactPositions = new ArrayList<>();
    private int version = CURRENT_VERSION;

    public MeteorImpactData() {}

    public MeteorImpactData(CompoundTag tag, HolderLookup.Provider registries) {
        this.version = tag.contains(VERSION_KEY, Tag.TAG_INT) ? tag.getInt(VERSION_KEY) : 1;
        loadPositions(tag);
        migrateIfNeeded();

    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        tag.putInt(VERSION_KEY, version);
        tag.putLongArray("positions", impactPositions.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    /** Set all known impact positions and mark the data dirty. */
    public void setImpactPositions(List<BlockPos> positions) {
        List<Long> newPositions = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            newPositions.add(pos.asLong());
        }
        if (!impactPositions.equals(newPositions)) {
            impactPositions.clear();
            impactPositions.addAll(newPositions);
            setDirty();
        }
    }

    /**
     * @return immutable list of stored impact positions.
     */
    public List<BlockPos> getImpactPositions() {
        List<BlockPos> positions = new ArrayList<>(impactPositions.size());
        for (long entry : impactPositions) {
            positions.add(BlockPos.of(entry));
        }
        return Collections.unmodifiableList(positions);
    }

    /**
     * @return {@code true} if at least one impact position has been recorded.
     */
    public boolean hasImpactPositions() {
        return !impactPositions.isEmpty();
    }


    /** Retrieve the data instance for the given world. */
    public static MeteorImpactData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MeteorImpactData::new, MeteorImpactData::new),
                DATA_NAME
        );
    }

    private void loadPositions(CompoundTag tag) {
        if (tag.contains("positions", Tag.TAG_LONG_ARRAY)) {
            long[] arr = tag.getLongArray("positions");
            for (long entry : arr) {
                impactPositions.add(entry);
            }
        } else if (tag.contains("pos")) {
            impactPositions.add(tag.getLong("pos"));
        }
    }

    private void migrateIfNeeded() {
        if (version < CURRENT_VERSION) {
            version = CURRENT_VERSION;
            setDirty();
        }
    }
}
