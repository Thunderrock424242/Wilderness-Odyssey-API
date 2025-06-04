package com.thunder.wildernessodysseyapi.WorldVersionChecker;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

public class WorldVersionData extends SavedData {
    public static final String FILE_NAME = "novaapi_version";
    private int version = 0;

    /** No-arg constructor: required by SavedData.Factory<T> */
    public WorldVersionData() {
        this.version = 0;
    }

    /** NBT constructor: load from tag */
    public WorldVersionData(CompoundTag tag) {
        this.version = tag.getInt("WorldVersion");
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putInt("WorldVersion", version);
        return tag;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        // Delegate to the single-argument save(...)
        return this.save(tag);
    }

    public static SavedData.Factory<WorldVersionData> factory() {
        return new SavedData.Factory<>(
                // (1) No-arg supplier → calls the no-arg constructor
                WorldVersionData::new,

                // (2) Loader from NBT → new WorldVersionData(tag)
                (tag, provider) -> new WorldVersionData(tag)
        );
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
        setDirty();
    }
}
