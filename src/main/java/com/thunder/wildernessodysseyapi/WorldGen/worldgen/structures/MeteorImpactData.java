package com.thunder.wildernessodysseyapi.WorldGen.worldgen.structures;

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
    private final List<Long> impactPositions = new ArrayList<>();
    private long bunkerPos = Long.MIN_VALUE;

    public MeteorImpactData() {}

    public MeteorImpactData(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("positions", Tag.TAG_LONG_ARRAY)) {
            long[] arr = tag.getLongArray("positions");
            for (long entry : arr) {
                impactPositions.add(entry);
            }
        } else if (tag.contains("pos")) {
            impactPositions.add(tag.getLong("pos"));
        }

        if (tag.contains("bunker")) {
            bunkerPos = tag.getLong("bunker");
        }
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        tag.putLongArray("positions", impactPositions.stream().mapToLong(Long::longValue).toArray());
        if (bunkerPos != Long.MIN_VALUE) {
            tag.putLong("bunker", bunkerPos);
        }
        return tag;
    }

    /** Set all known impact positions and mark the data dirty. */
    public void setImpactPositions(List<BlockPos> positions) {
        impactPositions.clear();
        for (BlockPos pos : positions) {
            impactPositions.add(pos.asLong());
        }
        setDirty();
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

    /** Store the bunker location and mark the data dirty. */
    public void setBunkerPos(BlockPos pos) {
        bunkerPos = pos.asLong();
        setDirty();
    }

    /**
     * @return the stored bunker position or {@code null} if not yet set.
     */
    public BlockPos getBunkerPos() {
        return bunkerPos == Long.MIN_VALUE ? null : BlockPos.of(bunkerPos);
    }

    /** Retrieve the data instance for the given world. */
    public static MeteorImpactData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MeteorImpactData::new, MeteorImpactData::new),
                DATA_NAME
        );
    }
}
