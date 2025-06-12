package com.thunder.wildernessodysseyapi.WorldVersionChecker;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

public class WorldVersionData extends SavedData {
    public static final String FILE_NAME = "novaapi_version";
    private int major = 0;
    private int minor = 0;

    public WorldVersionData() {
        this.major = 0;
        this.minor = 0;
    }

    public WorldVersionData(CompoundTag tag) {
        this.major = tag.getInt("WorldVersionMajor");
        this.minor = tag.getInt("WorldVersionMinor");
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putInt("WorldVersionMajor", major);
        tag.putInt("WorldVersionMinor", minor);
        return tag;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return save(tag);
    }

    public static SavedData.Factory<WorldVersionData> factory() {
        return new SavedData.Factory<>(
                WorldVersionData::new,
                (tag, provider) -> new WorldVersionData(tag)
        );
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public void setVersion(int major, int minor) {
        this.major = major;
        this.minor = minor;
        setDirty();
    }
}

