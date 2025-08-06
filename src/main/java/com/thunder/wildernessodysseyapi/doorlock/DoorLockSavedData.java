package com.thunder.wildernessodysseyapi.doorlock;

import com.thunder.ticktoklib.util.TickTokCountdown;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores door lock timers in world save data.
 */
public class DoorLockSavedData extends SavedData {
    private static final String DATA_NAME = "wildernessodyssey_door_locks";

    /**
     * If set to {@code true}, door locks will not begin counting down until the
     * mod is run without this flag. The flag is read from the environment
     * variable {@code WO_DOORLOCK_DEV} to allow developers to stage locks without
     * immediately activating them.
     */
    public static final boolean DEV_MODE = Boolean.parseBoolean(System.getenv().getOrDefault("WO_DOORLOCK_DEV", "false"));

    private final Map<Long, LockInfo> locks = new HashMap<>();

    public record LockInfo(long duration, long start) {}

    public DoorLockSavedData() {}

    public DoorLockSavedData(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = tag.getList("locks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag lockTag = list.getCompound(i);
            long pos = lockTag.getLong("pos");
            long duration = lockTag.getLong("duration");
            long start = lockTag.getLong("start");
            locks.put(pos, new LockInfo(duration, start));
        }
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, LockInfo> e : locks.entrySet()) {
            CompoundTag lockTag = new CompoundTag();
            lockTag.putLong("pos", e.getKey());
            lockTag.putLong("duration", e.getValue().duration());
            lockTag.putLong("start", e.getValue().start());
            list.add(lockTag);
        }
        tag.put("locks", list);
        return tag;
    }

    public void setLock(BlockPos pos, long duration, long start) {
        long startTime = DEV_MODE ? -1L : start;
        locks.put(pos.asLong(), new LockInfo(duration, startTime));
        setDirty();
    }

    public boolean removeLock(BlockPos pos) {
        if (locks.remove(pos.asLong()) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public long remaining(BlockPos pos, long now) {
        LockInfo info = locks.get(pos.asLong());
        if (info == null) return 0L;
        if (info.start < 0) return info.duration();
        TickTokCountdown countdown = new TickTokCountdown(info.duration(), info.start());
        return countdown.remaining(now);
    }

    public boolean isLocked(BlockPos pos, long now) {
        LockInfo info = locks.get(pos.asLong());
        if (info == null) return false;
        if (info.start < 0) {
            if (!DEV_MODE) {
                locks.put(pos.asLong(), new LockInfo(info.duration(), now));
                setDirty();
                TickTokCountdown countdown = new TickTokCountdown(info.duration(), now);
                return !countdown.isFinished(now);
            }
            return false;
        }
        TickTokCountdown countdown = new TickTokCountdown(info.duration(), info.start());
        return !countdown.isFinished(now);
    }

    public static DoorLockSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DoorLockSavedData::new, DoorLockSavedData::new),
                DATA_NAME
        );
    }
}
