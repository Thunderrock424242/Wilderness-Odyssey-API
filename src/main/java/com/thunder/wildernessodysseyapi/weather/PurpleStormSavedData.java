package com.thunder.wildernessodysseyapi.weather;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

public class PurpleStormSavedData extends SavedData {
    public static final String DATA_NAME = "wildernessodysseyapi_purple_storm";

    private long nextRollGameTime;
    private long eventEndGameTime;
    private boolean active;

    public PurpleStormSavedData() {
    }

    public PurpleStormSavedData(CompoundTag tag, HolderLookup.Provider registries) {
        this.nextRollGameTime = tag.getLong("NextRollGameTime");
        this.eventEndGameTime = tag.getLong("EventEndGameTime");
        this.active = tag.getBoolean("Active");
    }

    public long nextRollGameTime() {
        return nextRollGameTime;
    }

    public void setNextRollGameTime(long nextRollGameTime) {
        this.nextRollGameTime = nextRollGameTime;
        setDirty();
    }

    public long eventEndGameTime() {
        return eventEndGameTime;
    }

    public void setEventEndGameTime(long eventEndGameTime) {
        this.eventEndGameTime = eventEndGameTime;
        setDirty();
    }

    public boolean active() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        setDirty();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        tag.putLong("NextRollGameTime", nextRollGameTime);
        tag.putLong("EventEndGameTime", eventEndGameTime);
        tag.putBoolean("Active", active);
        return tag;
    }
}
