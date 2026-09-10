package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

/**
 * Transaction boundary for a closed regional hydrologic step.
 *
 * <p>Internal transfers debit and credit the same exact quantity. Boundary inputs and outputs
 * are recorded separately so the residual is always {@code input - output - delta storage}.</p>
 */
public final class WaterBudget {

    private final HydrologicStorage storage;
    private final long initialStorage;
    private long precipitationInput;
    private long upstreamInput;
    private long evapotranspirationOutput;
    private long downstreamOutput;
    private long otherBoundaryInput;
    private long otherBoundaryOutput;

    public WaterBudget(HydrologicStorage storage) {
        if (storage == null) {
            throw new IllegalArgumentException("Hydrologic storage is required");
        }
        this.storage = storage;
        this.initialStorage = storage.totalStored();
    }

    /** Credits precipitation only when the selected reservoir can own the accepted quantity. */
    public long creditPrecipitation(HydrologicReservoir destination, long requestedMilliUnits) {
        Math.addExact(precipitationInput, Math.min(Math.max(0, requestedMilliUnits), storage.availableCapacity(destination)));
        long accepted = storage.credit(destination, requestedMilliUnits);
        precipitationInput = Math.addExact(precipitationInput, accepted);
        return accepted;
    }

    /** Credits exact upstream inflow to a regional owner. */
    public long creditUpstream(HydrologicReservoir destination, long requestedMilliUnits) {
        Math.addExact(upstreamInput, Math.min(Math.max(0, requestedMilliUnits), storage.availableCapacity(destination)));
        long accepted = storage.credit(destination, requestedMilliUnits);
        upstreamInput = Math.addExact(upstreamInput, accepted);
        return accepted;
    }

    /** Debits exact evapotranspiration; callers may credit the same receipt to atmosphere. */
    public long debitEvapotranspiration(HydrologicReservoir source, long requestedMilliUnits) {
        Math.addExact(evapotranspirationOutput, Math.min(Math.max(0, requestedMilliUnits), storage.stored(source)));
        long removed = storage.debit(source, requestedMilliUnits);
        evapotranspirationOutput = Math.addExact(evapotranspirationOutput, removed);
        return removed;
    }

    /** Debits exact downstream discharge suitable for a matching neighboring inflow receipt. */
    public long debitDownstream(HydrologicReservoir source, long requestedMilliUnits) {
        Math.addExact(downstreamOutput, Math.min(Math.max(0, requestedMilliUnits), storage.stored(source)));
        long removed = storage.debit(source, requestedMilliUnits);
        downstreamOutput = Math.addExact(downstreamOutput, removed);
        return removed;
    }

    /** Records an explicit boundary reservoir input, including effectively infinite oceans. */
    public long creditBoundary(HydrologicReservoir destination, long requestedMilliUnits) {
        Math.addExact(otherBoundaryInput, Math.min(Math.max(0, requestedMilliUnits), storage.availableCapacity(destination)));
        long accepted = storage.credit(destination, requestedMilliUnits);
        otherBoundaryInput = Math.addExact(otherBoundaryInput, accepted);
        return accepted;
    }

    /** Records deep seepage, ocean export, or another intentionally open boundary output. */
    public long debitBoundary(HydrologicReservoir source, long requestedMilliUnits) {
        Math.addExact(otherBoundaryOutput, Math.min(Math.max(0, requestedMilliUnits), storage.stored(source)));
        long removed = storage.debit(source, requestedMilliUnits);
        otherBoundaryOutput = Math.addExact(otherBoundaryOutput, removed);
        return removed;
    }

    public long transfer(
            HydrologicReservoir source,
            HydrologicReservoir destination,
            long requestedMilliUnits
    ) {
        return storage.transfer(source, destination, requestedMilliUnits);
    }

    /** Positive or negative nonzero values identify a water-budget defect. */
    public long residualMilliUnits() {
        long inputs = Math.addExact(Math.addExact(precipitationInput, upstreamInput), otherBoundaryInput);
        long outputs = Math.addExact(
                Math.addExact(evapotranspirationOutput, downstreamOutput),
                otherBoundaryOutput
        );
        long storageDelta = storage.totalStored() - initialStorage;
        return Math.subtractExact(Math.subtractExact(inputs, outputs), storageDelta);
    }

    public Snapshot snapshot() {
        return new Snapshot(
                precipitationInput,
                upstreamInput,
                evapotranspirationOutput,
                downstreamOutput,
                otherBoundaryInput,
                otherBoundaryOutput,
                initialStorage,
                storage.totalStored(),
                residualMilliUnits()
        );
    }

    /** Immutable developer-auditor view of one closed accounting interval. */
    public record Snapshot(
            long precipitationInput,
            long upstreamInput,
            long evapotranspirationOutput,
            long downstreamOutput,
            long otherBoundaryInput,
            long otherBoundaryOutput,
            long initialStorage,
            long finalStorage,
            long residual
    ) {
    }
}
