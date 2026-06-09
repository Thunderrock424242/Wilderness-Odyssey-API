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

public class EchoBuildEchoSavedData extends SavedData {
    public static final String DATA_NAME = "wilderness_odyssey_echo_build_echoes";
    public static final SavedData.Factory<EchoBuildEchoSavedData> FACTORY =
            new SavedData.Factory<>(EchoBuildEchoSavedData::new, EchoBuildEchoSavedData::load);

    private final List<EchoBuildEcho> pendingEchoes = new ArrayList<>();

    public List<EchoBuildEcho> pendingEchoes() {
        return Collections.unmodifiableList(pendingEchoes);
    }

    public void addEcho(EchoBuildEcho echo) {
        pendingEchoes.add(echo);
        setDirty();
    }

    public void removeEcho(EchoBuildEcho echo) {
        pendingEchoes.remove(echo);
        setDirty();
    }

    private static EchoBuildEchoSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        EchoBuildEchoSavedData data = new EchoBuildEchoSavedData();
        ListTag list = tag.getList("echoes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            data.pendingEchoes.add(EchoBuildEcho.load(list.getCompound(i)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (EchoBuildEcho echo : pendingEchoes) {
            list.add(echo.save());
        }
        tag.put("echoes", list);
        return tag;
    }

    public static EchoBuildEchoSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }
}
