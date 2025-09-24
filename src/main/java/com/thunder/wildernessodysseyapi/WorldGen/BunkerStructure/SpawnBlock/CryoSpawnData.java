package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.SpawnBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Persists the world positions of cryo tubes that can be used for player spawns.
 */
public class CryoSpawnData extends SavedData {
    private static final String DATA_NAME = "wildernessodyssey_cryo_spawn_data";
    private final Set<Long> cryoPositions = new HashSet<>();

    public CryoSpawnData() {
    }

    public CryoSpawnData(CompoundTag tag, HolderLookup.Provider registries) {
        long[] entries = tag.getLongArray("cryo_positions");
        for (long entry : entries) {
            cryoPositions.add(entry);
        }
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        tag.putLongArray("cryo_positions", cryoPositions.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    /**
     * Adds a cryo tube position to the saved data.
     */
    public void add(BlockPos pos) {
        if (cryoPositions.add(pos.asLong())) {
            setDirty();
        }
    }

    /**
     * Adds multiple cryo tube positions.
     */
    public void addAll(Collection<BlockPos> positions) {
        boolean changed = false;
        for (BlockPos pos : positions) {
            if (cryoPositions.add(pos.asLong())) {
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    /**
     * Replaces all stored cryo tube positions.
     */
    public void replaceAll(Collection<BlockPos> positions) {
        cryoPositions.clear();
        for (BlockPos pos : positions) {
            cryoPositions.add(pos.asLong());
        }
        setDirty();
    }

    /**
     * @return an immutable list of all known cryo tube positions.
     */
    public List<BlockPos> getPositions() {
        if (cryoPositions.isEmpty()) {
            return Collections.emptyList();
        }
        List<BlockPos> positions = new ArrayList<>(cryoPositions.size());
        for (long entry : cryoPositions) {
            positions.add(BlockPos.of(entry));
        }
        return positions;
    }

    public static CryoSpawnData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CryoSpawnData::new, CryoSpawnData::new),
                DATA_NAME
        );
    }
}
