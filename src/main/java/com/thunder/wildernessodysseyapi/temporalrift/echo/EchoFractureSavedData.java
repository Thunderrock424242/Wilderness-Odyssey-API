package com.thunder.wildernessodysseyapi.temporalrift.echo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded persistent major fracture sites; querying these coordinates never loads chunks. */
public final class EchoFractureSavedData extends SavedData {
    public static final int MAX_SITES = 128;
    private static final String DATA_NAME = "wildernessodysseyapi_echo_fractures";
    private static final Factory<EchoFractureSavedData> FACTORY =
            new Factory<>(EchoFractureSavedData::new, EchoFractureSavedData::load);
    private final Map<Long, BlockPos> sites = new LinkedHashMap<>();

    /** Read-only coordinates in Earth/Echo's shared horizontal coordinate system. */
    public Collection<BlockPos> sites() {
        return Collections.unmodifiableCollection(sites.values());
    }

    /** Records an authored major fracture or Temporal Rift, with at most one site per chunk. */
    public void record(BlockPos position) {
        long key = net.minecraft.world.level.ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4);
        if (sites.containsKey(key)) return;
        if (sites.size() >= MAX_SITES) sites.remove(sites.keySet().iterator().next());
        sites.put(key, position.immutable());
        setDirty();
    }

    /** Resolves the server-owned ledger stored with the Overworld. */
    public static EchoFractureSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static EchoFractureSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        EchoFractureSavedData data = new EchoFractureSavedData();
        ListTag list = tag.getList("sites", Tag.TAG_COMPOUND);
        for (int i = Math.max(0, list.size() - MAX_SITES); i < list.size(); i++) {
            data.record(BlockPos.of(list.getCompound(i).getLong("position")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (BlockPos site : sites.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("position", site.asLong());
            list.add(entry);
        }
        tag.put("sites", list);
        return tag;
    }
}
