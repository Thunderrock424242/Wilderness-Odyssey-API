package com.thunder.wildernessodysseyapi.weather.system;

import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Versioned, defensive NBT codec for persistent storm and front identities. */
public final class WeatherSystemStorageCodec {
    public static final int DATA_VERSION = 1;
    private static final int ABSOLUTE_MAXIMUM_SYSTEMS = 256;

    private WeatherSystemStorageCodec() {
    }

    /** Encodes the tracker independently from the atmospheric-cell schema. */
    public static CompoundTag encode(WeatherSystemTracker tracker, int maximumSystems) {
        CompoundTag root = new CompoundTag();
        root.putInt("dataVersion", DATA_VERSION);
        root.putLong("nextId", tracker == null ? 1L : tracker.nextId());
        ListTag entries = new ListTag();
        if (tracker != null) {
            int limit = Math.max(1, Math.min(ABSOLUTE_MAXIMUM_SYSTEMS, maximumSystems));
            List<TrackedWeatherSystem> systems = tracker.systems();
            for (int index = 0; index < Math.min(limit, systems.size()); index++) {
                TrackedWeatherSystem system = systems.get(index);
                CompoundTag entry = new CompoundTag();
                entry.putLong("id", system.id());
                entry.putByte("type", (byte) system.type().ordinal());
                entry.putByte("stage", (byte) system.stage().ordinal());
                entry.putDouble("x", system.centerX());
                entry.putDouble("z", system.centerZ());
                entry.putDouble("radius", system.radiusBlocks());
                entry.putDouble("intensity", system.intensity());
                entry.putDouble("motionX", system.motion().x());
                entry.putDouble("motionZ", system.motion().z());
                entry.putDouble("organization", system.organization());
                entry.putLong("age", system.ageTicks());
                entry.putLong("updated", system.lastUpdatedTick());
                entry.putLong("split", system.lastSplitTick());
                entries.add(entry);
            }
        }
        root.put("systems", entries);
        return root;
    }

    /** Decodes valid entries while dropping duplicates and malformed identities. */
    public static DecodeResult decode(CompoundTag root, int maximumSystems) {
        if (root == null || !root.contains("dataVersion", Tag.TAG_INT)) {
            return new DecodeResult(1L, List.of(), 0, true);
        }
        int version = root.getInt("dataVersion");
        if (version != DATA_VERSION || !root.contains("systems", Tag.TAG_LIST)) {
            return new DecodeResult(1L, List.of(), version, true);
        }

        int limit = Math.max(1, Math.min(ABSOLUTE_MAXIMUM_SYSTEMS, maximumSystems));
        ListTag entries = root.getList("systems", Tag.TAG_COMPOUND);
        List<TrackedWeatherSystem> systems = new ArrayList<>(Math.min(limit, entries.size()));
        Set<Long> ids = new HashSet<>();
        int skipped = Math.max(0, entries.size() - limit);
        for (int index = 0; index < Math.min(limit, entries.size()); index++) {
            CompoundTag entry = entries.getCompound(index);
            try {
                long id = entry.getLong("id");
                int type = entry.getByte("type");
                int stage = entry.getByte("stage");
                if (id <= 0L || !ids.add(id)
                        || type < 0 || type >= WeatherSystemType.values().length
                        || stage < 0 || stage >= WeatherSystemStage.values().length) {
                    skipped++;
                    continue;
                }
                systems.add(new TrackedWeatherSystem(
                        id,
                        WeatherSystemType.values()[type],
                        WeatherSystemStage.values()[stage],
                        entry.getDouble("x"),
                        entry.getDouble("z"),
                        entry.getDouble("radius"),
                        entry.getDouble("intensity"),
                        new WindVector(entry.getDouble("motionX"), entry.getDouble("motionZ")),
                        entry.getDouble("organization"),
                        entry.getLong("age"),
                        entry.getLong("updated"),
                        entry.getLong("split")
                ));
            } catch (RuntimeException malformed) {
                skipped++;
            }
        }
        long nextId = root.contains("nextId", Tag.TAG_LONG) ? Math.max(1L, root.getLong("nextId")) : 1L;
        return new DecodeResult(nextId, List.copyOf(systems), version, skipped > 0);
    }

    /** Validated decoded tracker state and recovery metadata. */
    public record DecodeResult(long nextId, List<TrackedWeatherSystem> systems, int dataVersion, boolean recovered) {
    }
}
