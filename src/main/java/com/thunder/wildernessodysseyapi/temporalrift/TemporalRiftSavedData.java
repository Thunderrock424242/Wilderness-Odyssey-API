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
    @Nullable
    private BlockPos riftPosition;

    public boolean isRiftOpen() {
        return riftOpen;
    }

    public long getNextRiftDay() {
        return nextRiftDay;
    }

    public long getRiftCloseGameTime() {
        return riftCloseGameTime;
    }

    public @Nullable BlockPos getRiftPosition() {
        return riftPosition;
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

    public void setRiftPosition(@Nullable BlockPos riftPosition) {
        this.riftPosition = riftPosition == null ? null : riftPosition.immutable();
        setDirty();
    }

    private static TemporalRiftSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TemporalRiftSavedData data = new TemporalRiftSavedData();
        data.riftOpen = tag.getBoolean("riftOpen");
        data.nextRiftDay = tag.getLong("nextRiftDay");
        data.riftCloseGameTime = tag.getLong("riftCloseGameTime");
        if (tag.contains("riftPosX")) {
            data.riftPosition = new BlockPos(tag.getInt("riftPosX"), tag.getInt("riftPosY"), tag.getInt("riftPosZ"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("riftOpen", riftOpen);
        tag.putLong("nextRiftDay", nextRiftDay);
        tag.putLong("riftCloseGameTime", riftCloseGameTime);
        if (riftPosition != null) {
            tag.putInt("riftPosX", riftPosition.getX());
            tag.putInt("riftPosY", riftPosition.getY());
            tag.putInt("riftPosZ", riftPosition.getZ());
        }
        return tag;
    }

    public static TemporalRiftSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }
}
