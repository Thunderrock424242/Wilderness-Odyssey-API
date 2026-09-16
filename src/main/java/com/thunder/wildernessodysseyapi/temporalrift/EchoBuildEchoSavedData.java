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
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import com.thunder.wildernessodysseyapi.temporalrift.config.TemporalRiftConfig;

/** Existing reality-echo ledger, with bounded admission and coalescing by source position. */
public class EchoBuildEchoSavedData extends SavedData {
    public static final String DATA_NAME = "wilderness_odyssey_echo_build_echoes";
    public static final SavedData.Factory<EchoBuildEchoSavedData> FACTORY =
            new SavedData.Factory<>(EchoBuildEchoSavedData::new, EchoBuildEchoSavedData::load);

    private final Map<BlockPos, EchoBuildEcho> pendingEchoes = new LinkedHashMap<>();
    private static final int LEGACY_LOAD_LIMIT = 65536;

    public List<EchoBuildEcho> pendingEchoes() {
        return List.copyOf(pendingEchoes.values());
    }

    public void addEcho(EchoBuildEcho echo) {
        if (!pendingEchoes.containsKey(echo.sourcePos())
                && pendingEchoes.size() >= TemporalRiftConfig.ECHO_MAX_PENDING_REALITY_ECHOES.get()) return;
        pendingEchoes.put(echo.sourcePos(), echo);
        setDirty();
    }

    public void removeEcho(EchoBuildEcho echo) {
        pendingEchoes.remove(echo.sourcePos(), echo);
        setDirty();
    }

    private static EchoBuildEchoSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        EchoBuildEchoSavedData data = new EchoBuildEchoSavedData();
        ListTag list = tag.getList("echoes", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(list.size(), LEGACY_LOAD_LIMIT); i++) {
            EchoBuildEcho echo = EchoBuildEcho.load(list.getCompound(i));
            data.pendingEchoes.put(echo.sourcePos(), echo);
        }
        if (list.size() > LEGACY_LOAD_LIMIT) {
            org.slf4j.LoggerFactory.getLogger("TemporalRift").warn(
                    "Legacy Echo queue has {} records; retaining the first {} to bound saved state.", list.size(), LEGACY_LOAD_LIMIT);
            data.setDirty();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (EchoBuildEcho echo : pendingEchoes.values()) {
            list.add(echo.save());
        }
        tag.put("echoes", list);
        return tag;
    }

    public static EchoBuildEchoSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** Rotates a bounded batch so an unloaded destination cannot starve later loaded destinations. */
    public List<EchoBuildEcho> nextBatch(int limit) {
        List<EchoBuildEcho> batch = new ArrayList<>(Math.min(Math.max(0, limit), pendingEchoes.size()));
        for (EchoBuildEcho echo : pendingEchoes.values()) {
            if (batch.size() >= limit) break;
            batch.add(echo);
        }
        for (EchoBuildEcho echo : batch) {
            pendingEchoes.remove(echo.sourcePos());
            pendingEchoes.put(echo.sourcePos(), echo);
        }
        return batch;
    }

    /** Queue size without allocating a copy of pending records. */
    public int size() {
        return pendingEchoes.size();
    }
}
