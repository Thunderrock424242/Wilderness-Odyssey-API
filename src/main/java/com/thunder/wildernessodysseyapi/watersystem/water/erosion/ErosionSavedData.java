package com.thunder.wildernessodysseyapi.watersystem.water.erosion;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded persisted natural eligibility and conserved sediment. Unknown chunks
 * are protected. Revoked entries are retained so reload cannot re-enable them.
 * At capacity enrollment stops rather than evicting protection or material.
 */
public final class ErosionSavedData extends SavedData {
    private static final int MAX_CHUNKS = 8_192;
    private static final int MAX_MATERIAL_UNITS = 64;
    private final Map<Long, Entry> entries = new LinkedHashMap<>();

    /** Retrieves the level-owned ledger; no global world reference is retained. */
    public static ErosionSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(ErosionSavedData::new, ErosionSavedData::load),
                ModConstants.MOD_ID + "_erosion");
    }

    /** Enrolls a newly generated chunk only; existing records are never overwritten. */
    public void enroll(long chunk) {
        if (entries.size() < MAX_CHUNKS && !entries.containsKey(chunk)) {
            entries.put(chunk, new Entry(true));
            setDirty();
        }
    }

    /** Permanently protects a chunk after a player/automation placement. */
    public void protect(long chunk) {
        Entry entry = entries.get(chunk);
        if (entry == null && entries.size() < MAX_CHUNKS) {
            entries.put(chunk, new Entry(false));
            setDirty();
            return;
        }
        if (entry != null && entry.eligible) {
            entry.eligible = false;
            setDirty();
        }
    }

    /** Returns true only for a recorded, still-natural chunk. */
    public boolean eligible(long chunk) {
        Entry entry = entries.get(chunk);
        return entry != null && entry.eligible;
    }

    /** Returns whether credit can be retained before removing any terrain. */
    public boolean canCredit(long chunk, MaterialErosionRegistry.Material material) {
        Entry entry = entries.get(chunk);
        return entry != null && entry.units[material.ordinal()] < MAX_MATERIAL_UNITS;
    }

    /** Credits one successful erosion; callers must check capacity before mutation. */
    public void credit(long chunk, MaterialErosionRegistry.Material material) {
        if (!canCredit(chunk, material)) throw new IllegalStateException("Sediment capacity exhausted");
        entries.get(chunk).units[material.ordinal()]++;
        setDirty();
    }

    /** Spends one existing unit only after a successful deposition. */
    public void spend(long chunk, MaterialErosionRegistry.Material material) {
        Entry entry = entries.get(chunk);
        if (entry == null || entry.units[material.ordinal()] <= 0) throw new IllegalStateException("No sediment credit");
        entry.units[material.ordinal()]--;
        setDirty();
    }

    /** Finds the first available conserved category without manufacturing turbidity mass. */
    public MaterialErosionRegistry.Material available(long chunk) {
        Entry entry = entries.get(chunk);
        if (entry != null) {
            for (var material : MaterialErosionRegistry.Material.values()) {
                if (entry.units[material.ordinal()] > 0) return material;
            }
        }
        return null;
    }

    /** Atomically transfers one existing unit between enrolled chunks. */
    public boolean transfer(long from, long to, MaterialErosionRegistry.Material material) {
        if (from == to || material == null || !canCredit(to, material)) return false;
        Entry source = entries.get(from);
        if (source == null || source.units[material.ordinal()] <= 0) return false;
        spend(from, material);
        credit(to, material);
        return true;
    }

    /** Returns material units retained across chunk unloads and server restarts. */
    public int units(long chunk) {
        Entry entry = entries.get(chunk);
        int total = 0;
        if (entry != null) for (int amount : entry.units) total += amount;
        return total;
    }

    static ErosionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ErosionSavedData data = new ErosionSavedData();
        if (tag.getInt("version") != 1) return data;
        ListTag chunks = tag.getList("chunks", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(MAX_CHUNKS, chunks.size()); i++) {
            CompoundTag stored = chunks.getCompound(i);
            Entry entry = new Entry(stored.getBoolean("natural"));
            for (var material : MaterialErosionRegistry.Material.values()) {
                entry.units[material.ordinal()] = Math.max(0, Math.min(MAX_MATERIAL_UNITS, stored.getInt(material.name())));
            }
            data.entries.put(stored.getLong("pos"), entry);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", 1);
        ListTag chunks = new ListTag();
        entries.forEach((key, entry) -> {
            CompoundTag stored = new CompoundTag();
            stored.putLong("pos", key);
            stored.putBoolean("natural", entry.eligible);
            for (var material : MaterialErosionRegistry.Material.values()) stored.putInt(material.name(), entry.units[material.ordinal()]);
            chunks.add(stored);
        });
        tag.put("chunks", chunks);
        return tag;
    }

    private static final class Entry {
        private boolean eligible;
        private final int[] units = new int[MaterialErosionRegistry.Material.values().length];
        private Entry(boolean eligible) { this.eligible = eligible; }
    }
}
