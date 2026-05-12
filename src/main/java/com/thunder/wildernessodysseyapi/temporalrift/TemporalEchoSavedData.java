package com.thunder.wildernessodysseyapi.temporalrift;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TemporalEchoSavedData extends SavedData {
    public static final String DATA_NAME = "wilderness_odyssey_temporal_echoes";
    public static final SavedData.Factory<TemporalEchoSavedData> FACTORY =
            new SavedData.Factory<>(TemporalEchoSavedData::new, TemporalEchoSavedData::load);

    private final List<TemporalEcho> pendingEchoes = new ArrayList<>();

    public List<TemporalEcho> getPendingEchoes() {
        return Collections.unmodifiableList(pendingEchoes);
    }

    public void addEcho(TemporalEcho echo) {
        pendingEchoes.add(echo);
        setDirty();
    }

    public void removeEcho(TemporalEcho echo) {
        pendingEchoes.remove(echo);
        setDirty();
    }

    private static TemporalEchoSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TemporalEchoSavedData data = new TemporalEchoSavedData();
        ListTag list = tag.getList("echoes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            data.pendingEchoes.add(TemporalEcho.load(list.getCompound(i)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (TemporalEcho echo : pendingEchoes) {
            list.add(echo.save());
        }
        tag.put("echoes", list);
        return tag;
    }

    public static TemporalEchoSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }
}
