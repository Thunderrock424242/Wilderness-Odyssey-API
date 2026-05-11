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

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

public class TemporalTransferSavedData extends SavedData {
    public static final String DATA_NAME = MOD_ID + "_temporal_transfers";
    public static final SavedData.Factory<TemporalTransferSavedData> FACTORY =
            new SavedData.Factory<>(TemporalTransferSavedData::new, TemporalTransferSavedData::load);

    private final List<TemporalTransfer> pendingTransfers = new ArrayList<>();

    public List<TemporalTransfer> getPendingTransfers() {
        return Collections.unmodifiableList(pendingTransfers);
    }

    public void addTransfer(TemporalTransfer transfer) {
        pendingTransfers.add(transfer);
        setDirty();
    }

    public void removeTransfer(TemporalTransfer transfer) {
        pendingTransfers.remove(transfer);
        setDirty();
    }

    private static TemporalTransferSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TemporalTransferSavedData data = new TemporalTransferSavedData();
        ListTag list = tag.getList("transfers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            data.pendingTransfers.add(TemporalTransfer.load(list.getCompound(i)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (TemporalTransfer transfer : pendingTransfers) {
            list.add(transfer.save());
        }
        tag.put("transfers", list);
        return tag;
    }

    public static TemporalTransferSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }
}
