package com.thunder.wildernessodysseyapi.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class StructureLocationData extends SavedData {
    private static final String FILE_ID = "my_structure_location";
    private BlockPos structurePos = null;

    public static StructureLocationData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(tag -> {
            StructureLocationData data = new StructureLocationData();
            if (tag.contains("X")) {
                data.structurePos = new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
            }
            return data;
        }, () -> new StructureLocationData(), FILE_ID);
    }

    public void setStructurePos(BlockPos pos) {
        this.structurePos = pos;
        setDirty();
    }

    public BlockPos getStructurePos() {
        return structurePos;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (structurePos != null) {
            tag.putInt("X", structurePos.getX());
            tag.putInt("Y", structurePos.getY());
            tag.putInt("Z", structurePos.getZ());
        }
        return tag;
    }

    /**
     * @param compoundTag
     * @param provider
     * @return
     */
    @Override
    public CompoundTag save(CompoundTag compoundTag, HolderLookup.Provider provider) {
        return null;
    }
}
