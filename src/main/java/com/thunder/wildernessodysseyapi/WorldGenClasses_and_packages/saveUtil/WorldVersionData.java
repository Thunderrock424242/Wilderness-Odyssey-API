package com.thunder.wildernessodysseyapi.WorldGenClasses_and_packages.saveUtil;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.io.IOException;
import java.nio.file.Path;

public abstract class WorldVersionData extends SavedData {
    public static final String FILE_NAME = "novaapi_version";

    private int version = 0;

    public WorldVersionData() {}
    public WorldVersionData(CompoundTag tag) {
        this.version = tag.getInt("WorldVersion");
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("WorldVersion", version);
        return tag;
    }

    public static WorldVersionData load(CompoundTag tag) {
        return new WorldVersionData(tag) {
            /**
             * @param compoundTag
             * @param provider
             * @return
             */
            @Override
            public CompoundTag save(CompoundTag compoundTag, HolderLookup.Provider provider) {
                return null;
            }
        };
    }

    public static WorldVersionData getOrCreate(LevelStorageSource.LevelStorageAccess access) {
        Path worldPath = access.getLevelPath(LevelResource.ROOT);
        Path dataPath = worldPath.resolve("data").resolve(FILE_NAME + ".dat");

        try {
            CompoundTag nbt = CompressedStreamTools.read(dataPath.toFile());
            return new WorldVersionData(nbt) {
                /**
                 * @param compoundTag
                 * @param provider
                 * @return
                 */
                @Override
                public CompoundTag save(CompoundTag compoundTag, HolderLookup.Provider provider) {
                    return null;
                }
            };
        } catch (IOException e) {
            return new WorldVersionData() {
                /**
                 * @param compoundTag
                 * @param provider
                 * @return
                 */
                @Override
                public CompoundTag save(CompoundTag compoundTag, HolderLookup.Provider provider) {
                    return null;
                }
            }; // fallback if no version exists
        }
    }

    public void setVersion(int version) {
        this.version = version;
        setDirty();
    }

    public int getVersion() {
        return version;
    }
}
