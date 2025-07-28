package com.thunder.wildernessodysseyapi.WorldGen.worldgen.structures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * Stores the location of the meteor impact zone so other systems
 * can reference it (e.g. player spawn logic).
 */
public class MeteorImpactData extends SavedData {
    private static final String DATA_NAME = "wo_meteor_location";
    private long impactPos = Long.MIN_VALUE;

    public MeteorImpactData() {}

    public MeteorImpactData(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("pos")) {
            impactPos = tag.getLong("pos");
        }
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        tag.putLong("pos", impactPos);
        return tag;
    }

    /** Set the impact position and mark the data dirty. */
    public void setImpactPos(BlockPos pos) {
        impactPos = pos.asLong();
        setDirty();
    }

    /**
     * @return the stored impact position or null if not yet set.
     */
    public BlockPos getImpactPos() {
        return impactPos == Long.MIN_VALUE ? null : BlockPos.of(impactPos);
    }

    /** Retrieve the data instance for the given world. */
    public static MeteorImpactData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MeteorImpactData::new, MeteorImpactData::new),
                DATA_NAME
        );
    }
}
