package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterUnits;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists fractional chunk-scale rain and evaporation balances.
 *
 * <p>The ledger stores milli-units so repeated weak weather is not lost to
 * fixed-point rounding. It is not a second water authority: successful volume
 * transfers are always committed through {@code WaterAccess}, then consumed
 * from this bounded balance.</p>
 */
public final class HydrologySavedData extends SavedData {

    private static final String DATA_NAME = ModConstants.MOD_ID + "_water_hydrology";
    private static final String VERSION_KEY = "version";
    private static final String CHUNK_KEYS = "chunk_keys";
    private static final String BALANCES = "balances_milli_units";
    private static final String REPRESENTATIVES = "representatives";
    private static final String LAST_TOUCHED = "last_touched";
    private static final int DATA_VERSION = 1;
    private static final long MILLI_UNITS_PER_UNIT = 1_000L;
    private static final long MAX_ABSOLUTE_BALANCE_MILLI_UNITS =
            WaterUnits.UNITS_PER_BLOCK * MILLI_UNITS_PER_UNIT * 64L;
    private static final int HARD_MAX_ENTRIES = 65_536;

    private final LinkedHashMap<Long, Entry> entries =
            new LinkedHashMap<>(128, 0.75f, true);

    /** Returns the dimension-owned persistent hydrology ledger. */
    public static HydrologySavedData get(ServerLevel level) {
        HydrologySavedData data = level.getDataStorage().computeIfAbsent(
                new Factory<>(HydrologySavedData::new, HydrologySavedData::load),
                DATA_NAME
        );
        data.enforceBudget(WaterSimulationConfig.hydrologyMaxLedgerEntries());
        return data;
    }

    static HydrologySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        HydrologySavedData data = new HydrologySavedData();
        if (tag.getInt(VERSION_KEY) != DATA_VERSION) {
            return data;
        }
        long[] keys = tag.getLongArray(CHUNK_KEYS);
        long[] balances = tag.getLongArray(BALANCES);
        long[] representatives = tag.getLongArray(REPRESENTATIVES);
        long[] touched = tag.getLongArray(LAST_TOUCHED);
        int count = Math.min(
                Math.min(keys.length, balances.length),
                Math.min(representatives.length, touched.length)
        );
        for (int index = 0; index < count && data.entries.size() < HARD_MAX_ENTRIES; index++) {
            long balance = clampBalance(balances[index]);
            if (balance == 0L) {
                continue;
            }
            data.entries.put(keys[index], new Entry(
                    balance,
                    representatives[index],
                    Math.max(0L, touched[index])
            ));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] keys = new long[entries.size()];
        long[] balances = new long[entries.size()];
        long[] representatives = new long[entries.size()];
        long[] touched = new long[entries.size()];
        int index = 0;
        for (Map.Entry<Long, Entry> mapEntry : entries.entrySet()) {
            keys[index] = mapEntry.getKey();
            balances[index] = mapEntry.getValue().balanceMilliUnits;
            representatives[index] = mapEntry.getValue().representativePosition;
            touched[index] = mapEntry.getValue().lastTouchedTick;
            index++;
        }
        tag.putInt(VERSION_KEY, DATA_VERSION);
        tag.putLongArray(CHUNK_KEYS, keys);
        tag.putLongArray(BALANCES, balances);
        tag.putLongArray(REPRESENTATIVES, representatives);
        tag.putLongArray(LAST_TOUCHED, touched);
        return tag;
    }

    /** Adds signed fractional flux and returns the resulting signed unit balance. */
    public double accumulate(
            long chunkKey,
            BlockPos representative,
            double units,
            long gameTime
    ) {
        if (representative == null || !Double.isFinite(units) || Math.abs(units) < 1.0e-9) {
            return balanceUnits(chunkKey);
        }
        long delta = Math.round(units * MILLI_UNITS_PER_UNIT);
        if (delta == 0L) {
            return balanceUnits(chunkKey);
        }
        Entry entry = entries.computeIfAbsent(chunkKey, ignored -> new Entry(
                0L,
                representative.asLong(),
                Math.max(0L, gameTime)
        ));
        entry.balanceMilliUnits = clampBalance(saturatedAdd(entry.balanceMilliUnits, delta));
        entry.representativePosition = representative.asLong();
        entry.lastTouchedTick = Math.max(0L, gameTime);
        enforceBudget(HARD_MAX_ENTRIES);
        setDirty();
        return entry.balanceMilliUnits / (double) MILLI_UNITS_PER_UNIT;
    }

    /** Consumes a committed signed transfer from the pending balance. */
    public void consume(long chunkKey, long signedTransferredUnits) {
        Entry entry = entries.get(chunkKey);
        if (entry == null || signedTransferredUnits == 0L) {
            return;
        }
        long consumed = saturatedMultiply(signedTransferredUnits, MILLI_UNITS_PER_UNIT);
        entry.balanceMilliUnits = clampBalance(saturatedAdd(entry.balanceMilliUnits, -consumed));
        if (Math.abs(entry.balanceMilliUnits) < MILLI_UNITS_PER_UNIT / 2L) {
            entries.remove(chunkKey);
        }
        setDirty();
    }

    /** Returns the current signed pending balance in authority units. */
    public double balanceUnits(long chunkKey) {
        Entry entry = entries.get(chunkKey);
        return entry == null ? 0.0 : entry.balanceMilliUnits / (double) MILLI_UNITS_PER_UNIT;
    }

    /** Returns the number of persisted pending chunk balances. */
    public int entryCount() {
        return entries.size();
    }

    /** Applies the runtime-configured entry bound without changing balances. */
    public void enforceBudget(int maximumEntries) {
        int maximum = Math.max(1, Math.min(HARD_MAX_ENTRIES, maximumEntries));
        boolean removed = false;
        while (entries.size() > maximum) {
            Iterator<Long> iterator = entries.keySet().iterator();
            iterator.next();
            iterator.remove();
            removed = true;
        }
        if (removed) {
            setDirty();
        }
    }

    private static long clampBalance(long balance) {
        return Math.max(
                -MAX_ABSOLUTE_BALANCE_MILLI_UNITS,
                Math.min(MAX_ABSOLUTE_BALANCE_MILLI_UNITS, balance)
        );
    }

    private static long saturatedAdd(long first, long second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException ignored) {
            return second >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private static long saturatedMultiply(long first, long second) {
        try {
            return Math.multiplyExact(first, second);
        } catch (ArithmeticException ignored) {
            return (first < 0L) == (second < 0L) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private static final class Entry {
        private long balanceMilliUnits;
        private long representativePosition;
        private long lastTouchedTick;

        private Entry(long balanceMilliUnits, long representativePosition, long lastTouchedTick) {
            this.balanceMilliUnits = balanceMilliUnits;
            this.representativePosition = representativePosition;
            this.lastTouchedTick = lastTouchedTick;
        }
    }
}
