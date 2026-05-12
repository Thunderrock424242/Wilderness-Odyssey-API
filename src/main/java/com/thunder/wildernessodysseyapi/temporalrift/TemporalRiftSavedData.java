package com.thunder.wildernessodysseyapi.temporalrift;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

public class TemporalRiftSavedData extends SavedData {
    public static final String DATA_NAME = MOD_ID + "_temporal_rift";
    public static final SavedData.Factory<TemporalRiftSavedData> FACTORY =
            new SavedData.Factory<>(TemporalRiftSavedData::new, TemporalRiftSavedData::load);

    private boolean riftOpen;
    private long nextRiftDay;
    private long riftCloseGameTime;
    private long transientReturnRiftCloseGameTime;
    @Nullable
    private BlockPos riftPosition;
    @Nullable
    private BlockPos beforeSkyRiftPosition;
    @Nullable
    private BlockPos beforeGroundRiftPosition;
    @Nullable
    private BlockPos transientReturnRiftPosition;

    public boolean isRiftOpen() {
        return riftOpen;
    }

    public long getNextRiftDay() {
        return nextRiftDay;
    }

    public long getRiftCloseGameTime() {
        return riftCloseGameTime;
    }

    public long getTransientReturnRiftCloseGameTime() {
        return transientReturnRiftCloseGameTime;
    }

    public @Nullable BlockPos getRiftPosition() {
        return riftPosition;
    }

    public @Nullable BlockPos getBeforeSkyRiftPosition() {
        return beforeSkyRiftPosition;
    }

    public @Nullable BlockPos getBeforeGroundRiftPosition() {
        return beforeGroundRiftPosition;
    }

    public @Nullable BlockPos getTransientReturnRiftPosition() {
        return transientReturnRiftPosition;
    }

    public void setRiftOpen(boolean riftOpen) {
        this.riftOpen = riftOpen;
        setDirty();
    }

    public void setNextRiftDay(long nextRiftDay) {
        this.nextRiftDay = nextRiftDay;
        setDirty();
    }

    public void setRiftCloseGameTime(long riftCloseGameTime) {
        this.riftCloseGameTime = riftCloseGameTime;
        setDirty();
    }

    public void setTransientReturnRiftCloseGameTime(long transientReturnRiftCloseGameTime) {
        this.transientReturnRiftCloseGameTime = transientReturnRiftCloseGameTime;
        setDirty();
    }

    public void setRiftPosition(@Nullable BlockPos riftPosition) {
        this.riftPosition = riftPosition == null ? null : riftPosition.immutable();
        setDirty();
    }

    public void setBeforeSkyRiftPosition(@Nullable BlockPos beforeSkyRiftPosition) {
        this.beforeSkyRiftPosition = beforeSkyRiftPosition == null ? null : beforeSkyRiftPosition.immutable();
        setDirty();
    }

    public void setBeforeGroundRiftPosition(@Nullable BlockPos beforeGroundRiftPosition) {
        this.beforeGroundRiftPosition = beforeGroundRiftPosition == null ? null : beforeGroundRiftPosition.immutable();
        setDirty();
    }

    public void setTransientReturnRiftPosition(@Nullable BlockPos transientReturnRiftPosition) {
        this.transientReturnRiftPosition = transientReturnRiftPosition == null ? null : transientReturnRiftPosition.immutable();
        setDirty();
    }

    private static TemporalRiftSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TemporalRiftSavedData data = new TemporalRiftSavedData();
        data.riftOpen = tag.getBoolean("riftOpen");
        data.nextRiftDay = tag.getLong("nextRiftDay");
        data.riftCloseGameTime = tag.getLong("riftCloseGameTime");
        data.transientReturnRiftCloseGameTime = tag.getLong("transientReturnRiftCloseGameTime");
        if (tag.contains("riftPosX")) {
            data.riftPosition = new BlockPos(tag.getInt("riftPosX"), tag.getInt("riftPosY"), tag.getInt("riftPosZ"));
        }
        if (tag.contains("beforeSkyRiftPosX")) {
            data.beforeSkyRiftPosition = new BlockPos(
                    tag.getInt("beforeSkyRiftPosX"),
                    tag.getInt("beforeSkyRiftPosY"),
                    tag.getInt("beforeSkyRiftPosZ")
            );
        }
        if (tag.contains("beforeGroundRiftPosX")) {
            data.beforeGroundRiftPosition = new BlockPos(
                    tag.getInt("beforeGroundRiftPosX"),
                    tag.getInt("beforeGroundRiftPosY"),
                    tag.getInt("beforeGroundRiftPosZ")
            );
        }
        if (tag.contains("transientReturnRiftPosX")) {
            data.transientReturnRiftPosition = new BlockPos(
                    tag.getInt("transientReturnRiftPosX"),
                    tag.getInt("transientReturnRiftPosY"),
                    tag.getInt("transientReturnRiftPosZ")
            );
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("riftOpen", riftOpen);
        tag.putLong("nextRiftDay", nextRiftDay);
        tag.putLong("riftCloseGameTime", riftCloseGameTime);
        tag.putLong("transientReturnRiftCloseGameTime", transientReturnRiftCloseGameTime);
        if (riftPosition != null) {
            tag.putInt("riftPosX", riftPosition.getX());
            tag.putInt("riftPosY", riftPosition.getY());
            tag.putInt("riftPosZ", riftPosition.getZ());
        }
        if (beforeSkyRiftPosition != null) {
            tag.putInt("beforeSkyRiftPosX", beforeSkyRiftPosition.getX());
            tag.putInt("beforeSkyRiftPosY", beforeSkyRiftPosition.getY());
            tag.putInt("beforeSkyRiftPosZ", beforeSkyRiftPosition.getZ());
        }
        if (beforeGroundRiftPosition != null) {
            tag.putInt("beforeGroundRiftPosX", beforeGroundRiftPosition.getX());
            tag.putInt("beforeGroundRiftPosY", beforeGroundRiftPosition.getY());
            tag.putInt("beforeGroundRiftPosZ", beforeGroundRiftPosition.getZ());
        }
        if (transientReturnRiftPosition != null) {
            tag.putInt("transientReturnRiftPosX", transientReturnRiftPosition.getX());
            tag.putInt("transientReturnRiftPosY", transientReturnRiftPosition.getY());
            tag.putInt("transientReturnRiftPosZ", transientReturnRiftPosition.getZ());
        }
        return tag;
    }

    public static TemporalRiftSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }
}
