package com.thunder.wildernessodysseyapi.worldgen.meteor;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

/**
 * Persists the center + radius of every meteor site in a dimension.
 * Used by RadiationTickHandler so radiation zones survive server restarts.
 */
public class MeteorSavedData extends SavedData {

    private static final String DATA_NAME = MOD_ID + "_meteors";

    public record MeteorRecord(BlockPos center, int craterRadius) {}

    private final List<MeteorRecord> meteors = new ArrayList<>();

    // ---- Factory ----

    public static MeteorSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new Factory<>(
                MeteorSavedData::new,
                MeteorSavedData::load,
                null
            ),
            DATA_NAME
        );
    }

    // ---- NBT ----

    private static MeteorSavedData load(CompoundTag nbt) {
        MeteorSavedData data = new MeteorSavedData();
        ListTag list = nbt.getList("meteors", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos center = new BlockPos(
                entry.getInt("x"),
                entry.getInt("y"),
                entry.getInt("z")
            );
            data.meteors.add(new MeteorRecord(center, entry.getInt("radius")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag list = new ListTag();
        for (MeteorRecord m : meteors) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", m.center().getX());
            entry.putInt("y", m.center().getY());
            entry.putInt("z", m.center().getZ());
            entry.putInt("radius", m.craterRadius());
            list.add(entry);
        }
        nbt.put("meteors", list);
        return nbt;
    }

    // ---- API ----

    public void addMeteor(BlockPos center, int craterRadius) {
        meteors.add(new MeteorRecord(center, craterRadius));
        setDirty();
    }

    public List<MeteorRecord> getMeteors() {
        return Collections.unmodifiableList(meteors);
    }
}
