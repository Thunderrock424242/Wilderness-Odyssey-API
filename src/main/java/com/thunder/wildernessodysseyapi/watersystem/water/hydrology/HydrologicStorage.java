package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;

import java.util.Arrays;

/**
 * High-precision regional water inventory.
 *
 * <p>One canonical unit contains {@value #MILLI_UNITS_PER_CANONICAL_UNIT} stored
 * milli-units, while one cubic block/metre remains exactly 4,096 canonical
 * units. Presentation-scale normalized values must be derived from this state.</p>
 */
public final class HydrologicStorage {

    public static final long MILLI_UNITS_PER_CANONICAL_UNIT = 1_000L;
    public static final long MILLI_UNITS_PER_CUBIC_BLOCK =
            WaterVolumeChunk.UNITS_PER_BLOCK * MILLI_UNITS_PER_CANONICAL_UNIT;

    private final long[] storedMilliUnits;
    private final long[] capacityMilliUnits;

    /** Creates an empty inventory with independently bounded reservoirs. */
    public HydrologicStorage(long[] capacityMilliUnits) {
        int count = HydrologicReservoir.values().length;
        if (capacityMilliUnits == null || capacityMilliUnits.length != count) {
            throw new IllegalArgumentException("One capacity is required for every reservoir");
        }
        this.storedMilliUnits = new long[count];
        this.capacityMilliUnits = capacityMilliUnits.clone();
        for (int index = 0; index < count; index++) {
            this.capacityMilliUnits[index] = Math.max(0L, this.capacityMilliUnits[index]);
        }
    }

    private HydrologicStorage(long[] storedMilliUnits, long[] capacityMilliUnits) {
        this.storedMilliUnits = storedMilliUnits;
        this.capacityMilliUnits = capacityMilliUnits;
    }

    /** Returns an independent snapshot suitable for rollback or persistence. */
    public HydrologicStorage copy() {
        return new HydrologicStorage(storedMilliUnits.clone(), capacityMilliUnits.clone());
    }

    /** Restores exact inventory; malformed amounts fail rather than silently losing water. */
    public static HydrologicStorage restore(long[] stored, long[] capacity) {
        HydrologicStorage result = new HydrologicStorage(capacity);
        if (stored == null || stored.length != capacity.length) {
            throw new IllegalArgumentException("Invalid reservoir inventory length");
        }
        for (int i = 0; i < stored.length; i++) {
            if (stored[i] < 0 || stored[i] > capacity[i]) {
                throw new IllegalArgumentException("Invalid reservoir inventory at " + i);
            }
            result.storedMilliUnits[i] = stored[i];
        }
        result.totalStored();
        return result;
    }

    public long stored(HydrologicReservoir reservoir) {
        return storedMilliUnits[index(reservoir)];
    }

    public long capacity(HydrologicReservoir reservoir) {
        return capacityMilliUnits[index(reservoir)];
    }

    public long availableCapacity(HydrologicReservoir reservoir) {
        int index = index(reservoir);
        return capacityMilliUnits[index] - storedMilliUnits[index];
    }

    /** Credits at most the destination capacity and returns the exact accepted amount. */
    public long credit(HydrologicReservoir reservoir, long requestedMilliUnits) {
        int index = index(reservoir);
        long accepted = Math.min(Math.max(0L, requestedMilliUnits), availableCapacity(reservoir));
        storedMilliUnits[index] += accepted;
        return accepted;
    }

    /** Debits at most the stored quantity and returns the exact removed amount. */
    public long debit(HydrologicReservoir reservoir, long requestedMilliUnits) {
        int index = index(reservoir);
        long removed = Math.min(Math.max(0L, requestedMilliUnits), storedMilliUnits[index]);
        storedMilliUnits[index] -= removed;
        return removed;
    }

    /** Transfers one exact accepted amount; an unaccepted remainder stays with the source. */
    public long transfer(
            HydrologicReservoir source,
            HydrologicReservoir destination,
            long requestedMilliUnits
    ) {
        if (source == destination || requestedMilliUnits <= 0L) {
            return 0L;
        }
        long accepted = Math.min(stored(source),
                Math.min(availableCapacity(destination), requestedMilliUnits));
        storedMilliUnits[index(source)] -= accepted;
        storedMilliUnits[index(destination)] += accepted;
        return accepted;
    }

    public long totalStored() {
        long total = 0L;
        for (long value : storedMilliUnits) {
            total = Math.addExact(total, value);
        }
        return total;
    }

    public long[] storedSnapshot() {
        return storedMilliUnits.clone();
    }

    public long[] capacitySnapshot() {
        return capacityMilliUnits.clone();
    }

    @Override
    public String toString() {
        return "HydrologicStorage" + Arrays.toString(storedMilliUnits);
    }

    private static int index(HydrologicReservoir reservoir) {
        if (reservoir == null) {
            throw new IllegalArgumentException("Reservoir is required");
        }
        return reservoir.ordinal();
    }
}
