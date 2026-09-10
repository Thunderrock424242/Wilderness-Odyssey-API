package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

/** Exact server-thread reservation between a regional owner and detailed canonical water. */
public final class RegionalWaterProjection {
    private RegionalWaterProjection() { }

    /** Returns null unless the whole canonical parcel can be funded; no partial block funding. */
    public static Reservation reserve(RegionalHydrologyState state, HydrologicReservoir source, int units) {
        if (state == null || units <= 0 || units > 4096) return null;
        long amount = units * 1000L;
        if (state.stored(source) < amount) return null;
        long counter = Math.addExact(state.receipt(RegionalHydrologyState.Boundary.PROJECTION_OUT), amount);
        state.storage.debit(source, amount);
        return new Reservation(state, source, amount, counter);
    }

    /** Credits only a parcel already successfully removed from canonical ownership. */
    public static long returnParcel(RegionalHydrologyState state, int units, boolean legacy) {
        return state.credit(HydrologicReservoir.SURFACE_RUNOFF, units * 1000L,
                legacy ? RegionalHydrologyState.Boundary.LEGACY_RETURN : RegionalHydrologyState.Boundary.PROJECTION_IN);
    }

    /** A stack-local reservation owns the removed quantity until commit or rollback. */
    public static final class Reservation implements AutoCloseable {
        private final RegionalHydrologyState state;
        private final HydrologicReservoir source;
        private final long amount;
        private final long committedCounter;
        private boolean finished;

        private Reservation(RegionalHydrologyState state, HydrologicReservoir source, long amount, long counter) {
            this.state = state;
            this.source = source;
            this.amount = amount;
            this.committedCounter = counter;
        }

        /** Call only after canonical placement and its provenance ledger both succeed. */
        public void commit() {
            if (finished) throw new IllegalStateException("Projection reservation already finished");
            state.receipts[RegionalHydrologyState.Boundary.PROJECTION_OUT.ordinal()] = committedCounter;
            finished = true;
        }

        @Override
        public void close() {
            if (!finished) {
                if (state.storage.credit(source, amount) != amount) throw new IllegalStateException("Projection rollback capacity changed");
                finished = true;
            }
        }
    }
}
