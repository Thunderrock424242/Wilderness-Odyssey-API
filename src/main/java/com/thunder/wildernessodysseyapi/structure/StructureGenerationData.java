package com.thunder.wildernessodysseyapi.structure;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.server.level.ServerLevel;

public class StructureGenerationData extends SavedData {
    private static final String DATA_NAME = "wildernessodyssey_structure_data";
    private boolean structureGenerated;

    public static StructureGenerationData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                tag -> new StructureGenerationData(tag),
                StructureGenerationData::new,
                DATA_NAME
        );
    }

    public StructureGenerationData() {
        this.structureGenerated = false;
    }

    /**
     * @param tag
     * @param registries
     * @return
     */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return null;
    }

    public StructureGenerationData(CompoundTag tag) {
        this.structureGenerated = tag.getBoolean("structureGenerated");
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("structureGenerated", this.structureGenerated);
        return tag;
    }

    public boolean isStructureGenerated() {
        return structureGenerated;
    }

    public void setStructureGenerated(boolean generated) {
        this.structureGenerated = generated;
        this.setDirty();
    }
}
