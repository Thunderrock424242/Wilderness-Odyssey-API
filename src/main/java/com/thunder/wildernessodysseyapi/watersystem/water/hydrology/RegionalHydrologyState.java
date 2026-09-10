package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.WaterFeature;
import net.minecraft.nbt.CompoundTag;

import java.util.Arrays;

/**
 * Persistent physical inventory of a 16 by 16 metre terrain cell. Generated water
 * is not copied here: these stores own catchment water and detailed projections
 * debit them. Packed watershed conditions are only a compatibility view.
 */
public final class RegionalHydrologyState {
    public static final double AREA = 256.0;
    public static final long NO_OUTLET = Long.MIN_VALUE;
    private static final long MAX_STORE = 1L << 50;
    public final long key;
    final HydrologicStorage storage;
    final long[] receipts = new long[Boundary.values().length];
    long openingStorage;
    long lastSimulationTick;
    long forcingTick;
    double precipitationFraction;
    boolean snowing;
    HydrologicFlux lastFlux = HydrologicFlux.ZERO;
    double airTemperature = 15.0;
    double humidity = 0.5;
    double wind;
    double sunlight;
    double precipitationRemainder;
    double evaporationRemainder;
    double temperatureCelsius = 12.0;
    double vegetation;
    double elevation;
    double spillElevation;
    long downstream = NO_OUTLET;
    long basin;
    double contributingArea = AREA;
    double discharge;
    double velocity;
    double stage;
    double lastMelt;
    double lastRecharge;
    double lastBaseflow;
    SoilHydrologyModel.SoilProfile soil = SoilHydrologyModel.SoilProfile.LOAM;
    WaterFeature feature = WaterFeature.NONE;
    boolean ocean;

    /** Named external receipts make ocean and detailed-water approximations auditable. */
    public enum Boundary { PRECIPITATION, UPSTREAM, EVAPORATION, DOWNSTREAM,
        OCEAN_IN, OCEAN_OUT, PROJECTION_OUT, PROJECTION_IN, LEGACY_RETURN }

    public RegionalHydrologyState(long key, long time, double elevation) {
        this.key = key;
        this.lastSimulationTick = Math.max(0, time);
        this.elevation = this.spillElevation = elevation;
        this.basin = key;
        long[] capacities = new long[HydrologicReservoir.values().length];
        Arrays.fill(capacities, MAX_STORE);
        // Detailed water is owned by CanonicalWater, never counted twice here.
        capacities[HydrologicReservoir.CANONICAL_PROJECTION.ordinal()] = 0;
        storage = new HydrologicStorage(capacities);
    }

    private RegionalHydrologyState(long key, HydrologicStorage storage) {
        this.key = key;
        this.storage = storage;
    }

    public long stored(HydrologicReservoir reservoir) { return storage.stored(reservoir); }
    public long totalStored() { return storage.totalStored(); }
    public long lastSimulationTick() { return lastSimulationTick; }
    public double dischargeCubicMetresPerSecond() { return discharge; }
    public double stageMetres() { return stage; }
    public long receipt(Boundary boundary) { return receipts[boundary.ordinal()]; }

    /** Exact accepted credit; check counter arithmetic before mutating inventory. */
    long credit(HydrologicReservoir destination, long amount, Boundary boundary) {
        long accepted = Math.min(Math.max(0, amount), storage.availableCapacity(destination));
        long next = Math.addExact(receipt(boundary), accepted);
        storage.credit(destination, accepted);
        receipts[boundary.ordinal()] = next;
        return accepted;
    }

    long debit(HydrologicReservoir source, long amount, Boundary boundary) {
        long accepted = Math.min(Math.max(0, amount), stored(source));
        long next = Math.addExact(receipt(boundary), accepted);
        storage.debit(source, accepted);
        receipts[boundary.ordinal()] = next;
        return accepted;
    }

    /** Two-region transfer is all-or-nothing for the accepted quantity, on the server thread. */
    long routeTo(RegionalHydrologyState target, HydrologicReservoir source, long requested) {
        if (target == this) return 0;
        long accepted = Math.min(Math.max(0, requested), Math.min(stored(source),
                target.storage.availableCapacity(HydrologicReservoir.SURFACE_RUNOFF)));
        long sourceCounter = Math.addExact(receipt(Boundary.DOWNSTREAM), accepted);
        long targetCounter = Math.addExact(target.receipt(Boundary.UPSTREAM), accepted);
        storage.debit(source, accepted);
        target.storage.credit(HydrologicReservoir.SURFACE_RUNOFF, accepted);
        receipts[Boundary.DOWNSTREAM.ordinal()] = sourceCounter;
        target.receipts[Boundary.UPSTREAM.ordinal()] = targetCounter;
        return accepted;
    }

    /** Inputs minus outputs minus change of regional inventory; detailed inventory is separate. */
    public long residualMilliUnits() {
        long input = openingStorage + receipt(Boundary.PRECIPITATION) + receipt(Boundary.UPSTREAM)
                + receipt(Boundary.OCEAN_IN) + receipt(Boundary.PROJECTION_IN) + receipt(Boundary.LEGACY_RETURN);
        long output = receipt(Boundary.EVAPORATION) + receipt(Boundary.DOWNSTREAM)
                + receipt(Boundary.OCEAN_OUT) + receipt(Boundary.PROJECTION_OUT);
        return input - output - totalStored();
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("key", key);
        tag.putLongArray("stores", storage.storedSnapshot());
        tag.putLongArray("capacities", storage.capacitySnapshot());
        tag.putLongArray("receipts", receipts);
        tag.putLong("opening", openingStorage);
        tag.putLong("time", lastSimulationTick);
        tag.putLong("forcing_time", forcingTick);
        tag.putDouble("rain", precipitationFraction);
        tag.putBoolean("snowing", snowing);
        tag.putDouble("air", airTemperature);
        tag.putDouble("humidity", humidity);
        tag.putDouble("wind", wind);
        tag.putDouble("sun", sunlight);
        tag.putDouble("rain_remainder", precipitationRemainder);
        tag.putDouble("et_remainder", evaporationRemainder);
        tag.putDouble("temperature", temperatureCelsius);
        tag.putDouble("vegetation", vegetation);
        tag.putDouble("elevation", elevation);
        tag.putDouble("spill", spillElevation);
        tag.putLong("downstream", downstream);
        tag.putLong("basin", basin);
        tag.putDouble("area", contributingArea);
        tag.putDouble("discharge", discharge);
        tag.putDouble("velocity", velocity);
        tag.putDouble("stage", stage);
        tag.putString("soil", soil.name());
        tag.putString("feature", feature.name());
        tag.putBoolean("ocean", ocean);
        return tag;
    }

    static RegionalHydrologyState load(CompoundTag tag) {
        RegionalHydrologyState state = new RegionalHydrologyState(tag.getLong("key"),
                HydrologicStorage.restore(tag.getLongArray("stores"), tag.getLongArray("capacities")));
        long[] counters = tag.getLongArray("receipts");
        if (counters.length != state.receipts.length) throw new IllegalArgumentException("Invalid hydrology receipts");
        for (int i = 0; i < counters.length; i++) {
            if (counters[i] < 0) throw new IllegalArgumentException("Negative hydrology receipt");
            state.receipts[i] = counters[i];
        }
        state.openingStorage = tag.getLong("opening");
        state.lastSimulationTick = tag.getLong("time");
        state.forcingTick = tag.getLong("forcing_time");
        state.precipitationFraction = finite(tag, "rain");
        state.snowing = tag.getBoolean("snowing");
        state.airTemperature = finite(tag, "air");
        state.humidity = finite(tag, "humidity");
        state.wind = finite(tag, "wind");
        state.sunlight = finite(tag, "sun");
        state.precipitationRemainder = finite(tag, "rain_remainder");
        state.evaporationRemainder = finite(tag, "et_remainder");
        state.temperatureCelsius = finite(tag, "temperature");
        state.vegetation = finite(tag, "vegetation");
        state.elevation = finite(tag, "elevation");
        state.spillElevation = finite(tag, "spill");
        state.downstream = tag.getLong("downstream");
        state.basin = tag.getLong("basin");
        state.contributingArea = finite(tag, "area");
        state.discharge = finite(tag, "discharge");
        state.velocity = finite(tag, "velocity");
        state.stage = finite(tag, "stage");
        state.soil = SoilHydrologyModel.SoilProfile.valueOf(tag.getString("soil"));
        state.feature = WaterFeature.valueOf(tag.getString("feature"));
        state.ocean = tag.getBoolean("ocean");
        return state;
    }

    private static double finite(CompoundTag tag, String key) {
        double value = tag.getDouble(key);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite hydrology " + key);
        return value;
    }
}
