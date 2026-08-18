package com.thunder.wildernessodysseyapi.meteor.worldgen;

import com.thunder.wildernessodysseyapi.meteor.api.MeteorSiteSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

/**
 * Persists indexed meteor sites for radiation, ecology, story, and client summaries.
 */
public class MeteorSavedData extends SavedData {

    private static final String DATA_NAME = MOD_ID + "_meteors";
    private static final int INDEX_REGION_SIZE = 256;

    /** Version-tolerant immutable record for one successful impact site. */
    public record MeteorRecord(
            UUID id,
            BlockPos center,
            int craterRadius,
            long createdAt,
            double intensity,
            MeteorSiteSource source
    ) {
        public MeteorRecord {
            id = id == null ? UUID.randomUUID() : id;
            center = center == null ? BlockPos.ZERO : center.immutable();
            craterRadius = Math.max(1, Math.min(1_024, craterRadius));
            createdAt = Math.max(0L, createdAt);
            double finite = Double.isFinite(intensity) ? intensity : 1.0;
            intensity = Math.max(0.0, Math.min(1.0, finite));
            source = source == null ? MeteorSiteSource.UNKNOWN : source;
        }

        /** Retains the original public construction shape for integrations. */
        public MeteorRecord(BlockPos center, int craterRadius) {
            this(UUID.randomUUID(), center, craterRadius, 0L, 1.0, MeteorSiteSource.UNKNOWN);
        }
    }

    private final List<MeteorRecord> meteors = new ArrayList<>();
    private final Map<Long, List<MeteorRecord>> regionIndex = new HashMap<>();

    // ---- Factory ----

    public static MeteorSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new Factory<>(
                MeteorSavedData::new,
                MeteorSavedData::load
            ),
            DATA_NAME
        );
    }

    // ---- NBT ----

    private static MeteorSavedData load(CompoundTag nbt, HolderLookup.Provider registries) {
        MeteorSavedData data = new MeteorSavedData();
        ListTag list = nbt.getList("meteors", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos center = new BlockPos(
                entry.getInt("x"),
                entry.getInt("y"),
                entry.getInt("z")
            );
            int craterRadius = entry.getInt("radius");
            UUID id = readUuid(entry.getString("id"), center, craterRadius);
            long createdAt = entry.contains("created_at", Tag.TAG_LONG)
                    ? entry.getLong("created_at") : 0L;
            double intensity = entry.contains("intensity", Tag.TAG_DOUBLE)
                    ? entry.getDouble("intensity") : 1.0;
            MeteorSiteSource source = MeteorSiteSource.fromSerializedName(entry.getString("source"));
            data.addLoaded(new MeteorRecord(
                    id, center, craterRadius, createdAt, intensity, source));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (MeteorRecord m : meteors) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", m.center().getX());
            entry.putInt("y", m.center().getY());
            entry.putInt("z", m.center().getZ());
            entry.putInt("radius", m.craterRadius());
            entry.putString("id", m.id().toString());
            entry.putLong("created_at", m.createdAt());
            entry.putDouble("intensity", m.intensity());
            entry.putString("source", m.source().serializedName());
            list.add(entry);
        }
        nbt.put("meteors", list);
        return nbt;
    }

    // ---- API ----

    public void addMeteor(BlockPos center, int craterRadius) {
        addMeteor(center, craterRadius, 0L, 1.0, MeteorSiteSource.UNKNOWN);
    }

    /** Adds one authoritative site and updates the in-memory spatial index. */
    public MeteorRecord addMeteor(
            BlockPos center,
            int craterRadius,
            long createdAt,
            double intensity,
            MeteorSiteSource source
    ) {
        BlockPos safeCenter = center == null ? BlockPos.ZERO : center.immutable();
        for (MeteorRecord existing : regionIndex.getOrDefault(indexKey(safeCenter), List.of())) {
            if (existing.center().equals(safeCenter)) {
                return existing;
            }
        }
        MeteorRecord record = new MeteorRecord(
                UUID.randomUUID(), safeCenter, craterRadius, createdAt, intensity, source);
        addLoaded(record);
        setDirty();
        return record;
    }

    public List<MeteorRecord> getMeteors() {
        return Collections.unmodifiableList(meteors);
    }

    /** Returns indexed sites within a bounded horizontal distance. */
    public List<MeteorRecord> findWithin(BlockPos position, int maximumDistance) {
        if (position == null || maximumDistance < 0) {
            return List.of();
        }
        int radius = Math.min(4_096, maximumDistance);
        int minimumRegionX = Math.floorDiv(position.getX() - radius, INDEX_REGION_SIZE);
        int maximumRegionX = Math.floorDiv(position.getX() + radius, INDEX_REGION_SIZE);
        int minimumRegionZ = Math.floorDiv(position.getZ() - radius, INDEX_REGION_SIZE);
        int maximumRegionZ = Math.floorDiv(position.getZ() + radius, INDEX_REGION_SIZE);
        long maximumDistanceSquared = (long) radius * radius;
        List<MeteorRecord> found = new ArrayList<>();
        for (int regionX = minimumRegionX; regionX <= maximumRegionX; regionX++) {
            for (int regionZ = minimumRegionZ; regionZ <= maximumRegionZ; regionZ++) {
                for (MeteorRecord record : regionIndex.getOrDefault(pack(regionX, regionZ), List.of())) {
                    long dx = (long) record.center().getX() - position.getX();
                    long dz = (long) record.center().getZ() - position.getZ();
                    if (dx * dx + dz * dz <= maximumDistanceSquared) {
                        found.add(record);
                    }
                }
            }
        }
        return List.copyOf(found);
    }

    /** Returns the closest indexed site inside a bounded horizontal distance. */
    public Optional<MeteorRecord> findNearest(BlockPos position, int maximumDistance) {
        MeteorRecord nearest = null;
        long nearestDistanceSquared = Long.MAX_VALUE;
        for (MeteorRecord record : findWithin(position, maximumDistance)) {
            long dx = (long) record.center().getX() - position.getX();
            long dz = (long) record.center().getZ() - position.getZ();
            long distanceSquared = dx * dx + dz * dz;
            if (distanceSquared < nearestDistanceSquared) {
                nearest = record;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private void addLoaded(MeteorRecord record) {
        meteors.add(record);
        regionIndex.computeIfAbsent(indexKey(record.center()), ignored -> new ArrayList<>()).add(record);
    }

    private static long indexKey(BlockPos center) {
        return pack(
                Math.floorDiv(center.getX(), INDEX_REGION_SIZE),
                Math.floorDiv(center.getZ(), INDEX_REGION_SIZE)
        );
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static UUID readUuid(String value, BlockPos center, int craterRadius) {
        try {
            if (value != null && !value.isBlank()) {
                return UUID.fromString(value);
            }
        } catch (IllegalArgumentException exception) {
            // Fall through to a deterministic legacy identity.
        }
        String legacyKey = center.getX() + ":" + center.getY() + ":"
                + center.getZ() + ":" + craterRadius;
        return UUID.nameUUIDFromBytes(legacyKey.getBytes(StandardCharsets.UTF_8));
    }
}
